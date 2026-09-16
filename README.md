# ⚓ Anchor: Consent-Based Digital Wellbeing Co-Pilot for Gen Alpha

> **A check-in kids choose. A pattern parents can finally see.**
> Not surveillance: nothing covert, nothing diagnostic, and the child can see everything.

AI Builders Hackathon 2026 · Team Gaya

Gen Alpha is growing up with the earliest, heaviest device exposure of any generation. Parents see the symptoms (the toddler who only eats with a screen, the meltdown when the tablet goes away) but not the pattern. Existing parental-control apps report screen time, not how a child is doing. Anchor is the tool Gen Z wishes their families had: one app, two experiences, built on the child's consent.

| | |
|---|---|
| 📱 **Android app** | `android/`: Kotlin + Jetpack Compose, targets API 36, Play-Store ready |
| 🌐 **Website + backend** | `server/` + `web/`: one Render service: landing page, privacy policy, family sync API, Claude-written digest |
| 🧪 **Web prototype** | `web/prototype/index.html`: the original judge-clickable single-file prototype (unchanged) |
| 📄 **Pitch & research** | `docs/`: Pitch & Roadmap, 10-slide deck, Gen Alpha Research Dossier |

---

## What it does

**Kid Mode**
- **Sprout** 🌱→🌸🌳 grows with *consistent, honest* check-ins (a hard day counts as much as a good one; one skipped day is forgiven)
- **Vibe Check**: 15-second emoji check-in, optional **camera mirror** (live preview only, never analyzed or recorded, pulsing "camera on" dot) and a **private journal** with on-device lexicon sentiment. The note is shared only if the child ticks "share"
- **Transition Check**: Anchor notices a screen session has ended (Android Usage Access) and ~75 s later asks for one emoji tap; reaction time + intensity are recorded
- **Something bothered me 🛟**: tell family in two taps; parents get a calm conversation starter and helplines
- **Anchor Time**: phone-free meal / homework / outside / wind-down timer
- **What my family can see**: a live mirror of the parent view, per-signal sharing toggles, and a visible **pause**
- **Share to Anchor**: share a video/link from any app to family via the Android share sheet

**Parent Mode**
- **Family Digest** + stat row + **"Worth a conversation?"** alerts, each with a conversation starter (rules ported from the prototype: 4-day vs prior 6-day mood dip, 3 of the last 5 transitions ≥ 3.5, dominant category > 35%, plus late-night / meal-time / marathon-session patterns from the Research Dossier)
- **✨ AI digest**: Claude writes the weekly summary from **aggregated, de-identified numbers only**
- **Trends**: 20-day mood line, category donut, transition-reaction bars, weekly screen time vs goal, today's apps
- **App Lens**: what TikTok/Roblox/Snapchat/… are, age ratings, "settings to check *together*", a starter
- **Family goals** the child must agree to (press-and-hold). Anchor never blocks apps
- **Gen Z guides**: older siblings / mentors join the circle with the family code

## Real vs. simulated

| Capability | Web prototype | Android app |
|---|---|---|
| Vibe Check, streak, Sprout, sentiment | Real | Real |
| Camera mirror (never analyzed) | Real | Real (CameraX preview only) |
| Transition Check | Simulated button | **Real**: detects session end; demo also has a simulate button |
| Screen-time category mix | Simulated | **Real**: `UsageStatsManager` |
| Kid ↔ parent sync | n/a | **Real**: Anchor Relay |
| AI digest | n/a | **Real** when the relay has `ANTHROPIC_API_KEY`; rule-based fallback otherwise |
| 20-day history in "Try the demo" | Seeded | Seeded and labelled "sample data". Real usage replaces today's sample once Usage Access is on |

Verified on an Android 16 emulator: onboarding, demo in both modes, Vibe Check + camera permission, simulated **and real** Transition Check (Clock app session → notification → one tap), persistent disclosure notification, consent toggles (app names stripped *on device* before upload), and kid ↔ relay ↔ parent sync.

---

## Try it

### Android app
- **Demo on one phone:** install the APK → **Try the demo on this phone** → switch **Kid Mode / Parent Mode** in the top bar.
- **Real family:** parent phone → *I'm a parent* → **Create family** → share the 6-letter code. Kid phone → *I'm a kid* → read the screens together → enter code → **press and hold to agree** → grant Usage access + notifications. Older sibling → *I'm an older sibling or mentor*.

### Build the APK
Requirements: JDK 17, Android SDK (platform 37, build-tools 36).

```bash
cd android
./gradlew assembleDebug                      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleDebug -PanchorRelayUrl=http://10.0.2.2:8787   # emulator + local relay
```

Release (Play Store) builds need `android/keystore.properties` (never committed):

```properties
storeFile=../anchor-upload.jks
storePassword=…
keyAlias=anchor
keyPassword=…
```

```bash
./gradlew bundleRelease -PanchorRelayUrl=https://anchor-relay.onrender.com   # AAB for Play
./gradlew assembleRelease -PanchorRelayUrl=https://anchor-relay.onrender.com # APK for sideloading
```

### Run the relay + website locally
```bash
pip install -r server/requirements.txt
python server/anchor_relay.py          # http://localhost:8787  (website at /, API at /v1)
ANTHROPIC_API_KEY=sk-ant-... python server/anchor_relay.py   # enables the AI digest
```

---

## Deploy to Render

1. Push this repo to GitHub.
2. Render dashboard → **New → Blueprint** → select the repo. `render.yaml` creates the `anchor-relay` web service (Python, free plan).
3. Optional: in the service's **Environment**, set `ANTHROPIC_API_KEY` to enable the Claude digest.
4. Your site is live at `https://anchor-relay.onrender.com` (landing page `/`, prototype `/prototype/`, privacy policy `/privacy`, health `/v1/health`).
5. Rebuild the app with `-PanchorRelayUrl=https://anchor-relay.onrender.com` so new installs point at it (it's also editable in-app under *Server settings*).

> **Free-plan caveats:** the instance sleeps after ~15 min idle (first request takes ~30–60 s) and the disk is ephemeral, so family data resets on redeploy/restart. For a real pilot, add a Render persistent disk and set `ANCHOR_DATA_DIR`, or move storage to a database.

## Publish on Google Play

1. Create a Play Console developer account (one-time US$25) and complete identity verification.
2. **Create app** → name *Anchor*, app, free. Package: `com.anchor.copilot`.
3. Upload `app-release.aab` to **Testing → Internal testing** first (instant, up to 100 testers). *New personal accounts must run a closed test with at least 12 testers for 14 days before production access.*
4. **App content** declarations:
   - Privacy policy URL: `https://anchor-relay.onrender.com/privacy` (add your contact email in `web/privacy.html` first)
   - **Target audience:** parents (18+). Because children use the kid experience, answer the Families questions honestly and follow the Families Policy (no ads or ad SDKs; Anchor has none)
   - **Data safety:** collected = app activity (app interactions, in-app usage minutes), user-generated content (check-ins, optional notes shared by the child), app info; shared with family members only; encrypted in transit (HTTPS relay); deletion on request; no data sold; no ads
   - **Permissions:** *Usage access* (core feature: family wellbeing summaries), *foreground service: specialUse* (persistent disclosure + transition check; provide a short video), *camera* (Vibe Check mirror, not stored)
   - **Stalkerware / monitoring policy:** the manifest already declares `isMonitoringTool=child_monitoring`; the app shows a persistent notification and its icon, never hides, and discloses monitoring in the store listing. State clearly in the description that Anchor is for parents and their children with the child's knowledge
5. Store listing: short description, full description (mention monitoring + consent), 512×512 icon, 1024×500 feature graphic, phone screenshots (see `web/img/`).

## Architecture

```
Kid phone (Android)                        Anchor Relay (Render)              Parent / Gen Z guide phone
┌──────────────────────────────┐          ┌──────────────────────────┐        ┌───────────────────────────┐
│ UsageStatsManager ─► summary │─events──►│ append-only family log   │◄──────►│ same reducer → dashboard  │
│ Vibe/Transition check-ins    │  (HTTPS) │ hashed tokens, pairing   │        │ alerts, trends, App Lens  │
│ sharing prefs applied HERE   │          │ /v1/digest ─► Claude API │◄─agg.──│ "Write digest with AI"    │
│ camera mirror & journal stay │          │ serves web/ + /privacy   │ numbers└───────────────────────────┘
└──────────────────────────────┘          └──────────────────────────┘
```

- Every device keeps the family's **event log** and rebuilds the same `FamilyState`, so the kid's "What my family can see" screen is exactly what the parent sees.
- The kid's sharing choices are enforced **on the kid's device before anything is sent**.
- The relay stores only events; tokens are SHA-256 hashed; `/v1/digest` accepts only a whitelist of aggregate fields.

```
android/app/src/main/java/com/anchor/copilot/
  data/      Models, FamilyState (reducer, streak, Sprout, trends), CoPilot (alert engine),
             UsageCollector (screen time + session-end detection), Sentiment, AppCatalog (App Lens),
             AnchorRepository (storage + relay sync), RelayClient, SampleFamily (demo data)
  agent/     CompanionService (disclosure notification + transition checks), SyncWorker, Notifier
  ui/        AnchorRoot (navigation), onboarding/, kid/, parent/, components/ (cards, charts, camera mirror), theme/
server/anchor_relay.py   stdlib HTTP server + official anthropic SDK
web/                     landing page, privacy policy, screenshots, original prototype
```

## Privacy commitments (non-negotiable)

- No background or covert camera/mic, no face-reading model, no diagnosis
- Never reads messages, photos, keystrokes or screen contents; never blocks apps
- Raw media and private journal text never leave the device
- Persistent "Anchor is on" notification on the child's phone; child-controlled sharing and pause
- Alerts are framed as "worth a conversation", with helplines (CHILDLINE 1098, Tele-MANAS 14416, 988, Childline UK)

See the [privacy policy](web/privacy.html) and `docs/Gen_Alpha_Research_Dossier.docx` for the reasoning.
