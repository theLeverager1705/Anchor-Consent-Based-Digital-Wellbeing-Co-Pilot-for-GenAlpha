# Anchor: working context for Claude Code

Consent-based digital wellbeing co-pilot for Gen Alpha families (AI Builders Hackathon 2026, Team Gaya).
`README.md` is the human-facing description. The pitch, deck and research dossier are in `docs/`; product
decisions come from them.

## Layout

| Path | What |
|---|---|
| `android/` | Kotlin + Compose app, package `com.anchor.copilot`, compileSdk 37 / targetSdk 36 / minSdk 26, AGP 9.4, Gradle 9.7.1 |
| `server/anchor_relay.py` | Render web service: family event-log API (`/v1/*`), `/v1/digest` (Claude via `anthropic` SDK), serves `web/` |
| `web/` | Landing page, `privacy.html`, `img/` screenshots, `prototype/index.html` |
| `render.yaml` | Render Blueprint |

## Build & run

```bash
cd android && ./gradlew assembleDebug                       # debug APK
./gradlew assembleDebug -PanchorRelayUrl=http://10.0.2.2:8787 # point at a local relay from the emulator
python server/anchor_relay.py                                  # relay + website on :8787
```

No unit tests yet. Verify changes by running the app (emulator: "Try the demo on this phone", switch Kid/Parent
Mode) and smoke-testing the relay with curl.

## Architecture in one paragraph

Every device holds the family's append-only **event log** (`Event` in `data/Models.kt`) and derives
`FamilyState` with the same reducer (`FamilyState.reduce`). The kid device emits `CONSENT`, `SNAPSHOT`,
`VIBE_CHECK`, `TRANSITION`, `FLAG`, `SHARE`, `FOCUS`, `SHARING_CHANGED`, `PACT_ACCEPTED`; grown-ups emit
`MEMBER_JOINED`, `PACT_PROPOSED`, `KUDOS`, `FLAG_ACK`. Transport is either `LOCAL_DEMO` (one phone, seeded by
`SampleFamily`) or `RELAY` (push pending events with `seq == 0`, pull `after=lastSeq`). `CoPilot.alerts` is
the rule-based alert engine; keep it explainable.

## Hard constraints: product commitments, don't break them

1. **Consent first.** Nothing is shared before the child's `CONSENT`. Sharing prefs are applied on the kid's
   device (`UsageCollector.applySharing`, `AgentActions.shareSnapshot`) *before* upload.
2. **Camera is a mirror.** `CameraMirror` binds only a `Preview` use case: no `ImageAnalysis`, no capture, no
   face model. Keep the visible "camera on" indicator.
3. **Journal text stays on device** (`DeviceProfile.privateJournal`) unless the child ticks "share".
4. **Never diagnostic.** Alerts say "worth a conversation"; no clinical labels. The digest prompt enforces this.
5. **AI sees aggregates only.** `CoPilot.digestInput` → `/v1/digest`, which whitelists keys. Never send names,
   notes, app-level detail or media.
6. **Transparent to the child.** Persistent notification via `CompanionService`, `isMonitoringTool` manifest
   flag, "What my family can see" mirrors the parent view. Never hide the icon or block apps.
7. **Web prototype is frozen**: `web/prototype/index.html` keeps its own rules (single file, no network).
8. Be honest about real vs. simulated; update the README table when that changes.

## Conventions

- Kotlin: 4-space indent, Compose screens take `(state, profile, repo, open…)`; colors only from `ui/theme/Anchor`
- New event types: add to `EventType`, a `@Serializable` payload, and the reducer
- Python server: stdlib only apart from `anthropic`; model `claude-opus-5` with `fallbacks: "default"`
