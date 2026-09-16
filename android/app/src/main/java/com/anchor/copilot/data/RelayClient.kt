package com.anchor.copilot.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class RelayException(message: String) : IOException(message)

/**
 * Client for Anchor Relay, a tiny open-source server (server/anchor_relay.py) that only stores the
 * family's event log. It holds summaries the child agreed to share, never raw device activity.
 */
class RelayClient(baseUrl: String) {
    private val base = baseUrl.trim().trimEnd('/')

    @Serializable
    data class FamilyCreated(val familyId: String, val code: String, val token: String)

    @Serializable
    data class Joined(val familyId: String, val token: String)

    @Serializable
    data class Posted(val seq: Long)

    @Serializable
    data class EventPage(val events: List<Event>, val latest: Long)

    @Serializable
    data class Digest(val digest: String, val starter: String, val model: String = "")

    suspend fun digest(input: Map<String, Any>): Digest {
        val body = buildJsonObject {
            input.forEach { (k, v) ->
                when (v) {
                    is Number -> put(k, v)
                    is List<*> -> put(k, kotlinx.serialization.json.JsonArray(v.map { kotlinx.serialization.json.JsonPrimitive(it.toString()) }))
                    else -> put(k, v.toString())
                }
            }
        }
        return AnchorJson.decodeFromString(request("POST", "/v1/digest", null, body))
    }

    suspend fun health(): Boolean = runCatching { request("GET", "/v1/health", null, null); true }.getOrDefault(false)

    /** True when the relay has a Claude API key configured. */
    suspend fun aiEnabled(): Boolean =
        (AnchorJson.parseToJsonElement(request("GET", "/v1/health", null, null)) as JsonObject)["ai"]?.toString() == "true"

    suspend fun createFamily(name: String, role: Role): FamilyCreated =
        AnchorJson.decodeFromString(request("POST", "/v1/families", null, buildJsonObject { put("name", name); put("role", role.name) }))

    suspend fun join(code: String, name: String, role: Role): Joined =
        AnchorJson.decodeFromString(request("POST", "/v1/join", null, buildJsonObject { put("code", code.trim().uppercase()); put("name", name); put("role", role.name) }))

    suspend fun post(familyId: String, token: String, event: Event): Long =
        AnchorJson.decodeFromString<Posted>(request("POST", "/v1/families/$familyId/events", token, AnchorJson.encodeToJsonElement(Event.serializer(), event) as JsonObject)).seq

    suspend fun fetch(familyId: String, token: String, after: Long): EventPage =
        AnchorJson.decodeFromString(request("GET", "/v1/families/$familyId/events?after=$after", token, null))

    private suspend fun request(method: String, path: String, token: String?, body: JsonObject?): String = withContext(Dispatchers.IO) {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 8_000
            conn.readTimeout = 45_000 // the AI digest can take a while; free Render instances also cold-start
            conn.setRequestProperty("Accept", "application/json")
            token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { AnchorJson.parseToJsonElement(text) as JsonObject }.getOrNull()?.get("error")?.toString()?.trim('"')
                throw RelayException(msg ?: "Relay error $code")
            }
            text
        } finally {
            conn.disconnect()
        }
    }
}
