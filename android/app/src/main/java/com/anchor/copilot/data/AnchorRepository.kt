package com.anchor.copilot.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/**
 * Single source of truth on the device: this device's profile plus the family's shared event log.
 * Both the kid and grown-up apps run the same reducer over the same log, so what a parent sees is
 * exactly what the child can inspect in "What my family sees".
 */
class AnchorRepository private constructor(private val appContext: Context) {

    private val prefs: SharedPreferences = appContext.getSharedPreferences("anchor", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val syncMutex = Mutex()

    private val _profile = MutableStateFlow(loadProfile())
    val profile: StateFlow<DeviceProfile> = _profile.asStateFlow()

    private val _events = MutableStateFlow(loadEvents())
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    val family: StateFlow<FamilyState> = _events.map { FamilyState.reduce(it) }
        .stateIn(scope, SharingStarted.Eagerly, FamilyState.reduce(_events.value))

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    /** The role the UI is currently acting as (demo mode can switch). */
    val actingRole: Role
        get() = _profile.value.let { p -> if (p.transport == Transport.LOCAL_DEMO) p.demoViewAs else p.role ?: Role.PARENT }

    fun currentFamily(): FamilyState = family.value

    // ---------- profile ----------

    fun updateProfile(block: (DeviceProfile) -> DeviceProfile) {
        _profile.update(block)
        prefs.edit().putString(KEY_PROFILE, AnchorJson.encodeToString(DeviceProfile.serializer(), _profile.value)).apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
        _events.value = emptyList()
        _profile.value = DeviceProfile()
    }

    // ---------- events ----------

    inline fun <reified T> emit(type: String, payload: T) = emitRaw(type, payload.toPayload())

    fun emitRaw(type: String, payload: JsonObject, fromRole: Role = actingRole, name: String? = null): Event {
        val p = _profile.value
        val fromName = name ?: if (fromRole == Role.CHILD) family.value.childName.ifBlank { p.myName } else p.myName
        val event = Event(
            id = UUID.randomUUID().toString(),
            type = type,
            from = fromRole,
            fromName = fromName,
            ts = System.currentTimeMillis(),
            seq = if (p.transport == Transport.LOCAL_DEMO) nextLocalSeq() else 0,
            payload = payload,
        )
        appendLocal(listOf(event))
        if (p.transport == Transport.RELAY) scope.launch { sync() }
        return event
    }

    private fun nextLocalSeq(): Long = (_events.value.maxOfOrNull { it.seq } ?: 0L) + 1

    private fun appendLocal(incoming: List<Event>) {
        _events.update { current ->
            val byId = LinkedHashMap<String, Event>()
            current.forEach { byId[it.id] = it }
            incoming.forEach { e -> byId[e.id] = if (e.seq == 0L) (byId[e.id] ?: e) else e }
            compact(byId.values.toList())
        }
        saveEvents()
    }

    /** Keep only the newest snapshot per day (and per real/sample) so the log stays small. */
    private fun compact(list: List<Event>): List<Event> {
        val newestSnapshot = HashMap<String, Event>()
        list.filter { it.type == EventType.SNAPSHOT }.forEach { e ->
            val snap = e.decode<UsageSnapshot>() ?: return@forEach
            val key = snap.date + snap.sample
            val prev = newestSnapshot[key]
            if (prev == null || e.ts >= prev.ts) newestSnapshot[key] = e
        }
        val keep = newestSnapshot.values.map { it.id }.toSet()
        return list.filter { it.type != EventType.SNAPSHOT || it.id in keep }.takeLast(MAX_EVENTS)
    }

    // ---------- relay sync ----------

    suspend fun sync(): Result<Int> = syncMutex.withLock {
        val p = _profile.value
        if (p.transport != Transport.RELAY || p.familyId.isBlank()) return Result.success(0)
        val client = RelayClient(p.relayUrl)
        return runCatching {
            // 1. push anything the relay hasn't acknowledged yet
            _events.value.filter { it.seq == 0L }.forEach { pending ->
                val seq = client.post(p.familyId, p.token, pending)
                appendLocal(listOf(pending.copy(seq = seq)))
            }
            // 2. pull what everyone else sent
            var after = p.lastSeq
            var pulled = 0
            do {
                val page = client.fetch(p.familyId, p.token, after)
                if (page.events.isNotEmpty()) {
                    appendLocal(page.events)
                    pulled += page.events.size
                    after = page.events.maxOf { it.seq }
                }
            } while (page.events.size >= 200)
            if (after != p.lastSeq) updateProfile { it.copy(lastSeq = after) }
            _syncStatus.value = null
            pulled
        }.onFailure { _syncStatus.value = "Offline: ${it.message ?: "can't reach relay"}" }
    }

    fun syncInBackground() {
        scope.launch { sync() }
    }

    // ---------- setup flows ----------

    suspend fun createFamily(relayUrl: String, name: String, role: Role): Result<String> = runCatching {
        val created = RelayClient(relayUrl).createFamily(name, role)
        _events.value = emptyList()
        updateProfile {
            it.copy(transport = Transport.RELAY, relayUrl = relayUrl, familyId = created.familyId, token = created.token, pairCode = created.code, role = role, myName = name, lastSeq = 0)
        }
        emitRaw(EventType.MEMBER_JOINED, MemberJoined(name, role).toPayload(), role, name)
        emitRaw(EventType.PACT_PROPOSED, Pact(version = 1, proposedBy = name, proposedAt = System.currentTimeMillis()).toPayload(), role, name)
        sync().getOrThrow()
        created.code
    }

    suspend fun joinFamily(relayUrl: String, code: String, name: String, role: Role): Result<Unit> = runCatching {
        val joined = RelayClient(relayUrl).join(code, name, role)
        _events.value = emptyList()
        updateProfile {
            it.copy(transport = Transport.RELAY, relayUrl = relayUrl, familyId = joined.familyId, token = joined.token, pairCode = code.uppercase(), role = role, myName = name, lastSeq = 0)
        }
        if (role != Role.CHILD) emitRaw(EventType.MEMBER_JOINED, MemberJoined(name, role).toPayload(), role, name)
        sync().getOrThrow()
    }

    fun startDemo(guardianName: String) {
        _events.value = emptyList()
        updateProfile {
            it.copy(transport = Transport.LOCAL_DEMO, role = Role.PARENT, myName = guardianName, demoViewAs = Role.CHILD, familyId = "demo", pairCode = "DEMO42", onboarded = true, consentAt = System.currentTimeMillis(), relayUrl = it.relayUrl)
        }
        appendLocal(SampleFamily.events(guardianName))
    }

    fun setDemoView(role: Role) = updateProfile { it.copy(demoViewAs = role) }

    /** Journal notes live only in this device's private storage. */
    fun addPrivateNote(note: JournalNote) = updateProfile { it.copy(privateJournal = (listOf(note) + it.privateJournal).take(200)) }

    /** Asks the relay for a Claude-written digest from aggregated numbers only. */
    suspend fun aiDigest(input: Map<String, Any>): Result<RelayClient.Digest> = runCatching {
        val url = _profile.value.relayUrl.ifBlank { com.anchor.copilot.BuildConfig.DEFAULT_RELAY_URL }
        RelayClient(url).digest(input)
    }

    // ---------- storage ----------

    private fun loadProfile(): DeviceProfile = prefs.getString(KEY_PROFILE, null)
        ?.let { runCatching { AnchorJson.decodeFromString(DeviceProfile.serializer(), it) }.getOrNull() } ?: DeviceProfile()

    private fun loadEvents(): List<Event> = prefs.getString(KEY_EVENTS, null)
        ?.let { runCatching { AnchorJson.decodeFromString(ListSerializer(Event.serializer()), it) }.getOrNull() } ?: emptyList()

    private fun saveEvents() {
        prefs.edit().putString(KEY_EVENTS, AnchorJson.encodeToString(ListSerializer(Event.serializer()), _events.value)).apply()
    }

    companion object {
        private const val KEY_PROFILE = "profile"
        private const val KEY_EVENTS = "events"
        private const val MAX_EVENTS = 1500

        @Volatile private var instance: AnchorRepository? = null

        fun get(context: Context): AnchorRepository =
            instance ?: synchronized(this) { instance ?: AnchorRepository(context.applicationContext).also { instance = it } }
    }
}
