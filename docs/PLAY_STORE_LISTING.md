# Google Play submission kit: Anchor

Everything to copy into Play Console. Files: `release/anchor-1.0.0.aab` (upload), `docs/store/icon-512.png`,
`docs/store/feature-1024x500.png`, phone screenshots in `web/img/`.

## App details

- **App name:** Anchor: Family Wellbeing
- **Package:** `com.anchor.copilot`
- **Category:** Parenting
- **Contact email:** *(your email)*
- **Privacy policy:** `https://<your-render-service>.onrender.com/privacy`

### Short description (≤ 80 chars)
Consent-based family wellbeing: kids check in, parents see patterns, not spying.

### Full description
Anchor is a consent-based digital wellbeing co-pilot for families with kids aged about 5–12.

It's not a spy app. Anchor is built on the child's agreement: kids set it up together with a grown-up, choose exactly what to share, and can always see what their family sees.

FOR KIDS
• Vibe Check: a 15-second emoji check-in. An optional camera mirror helps you see your own face; it is never recorded or analyzed
• Your Sprout grows with honest check-ins. Hard days count just as much as good ones
• Transition Check: after a long screen session ends, one quick tap about how you feel
• "Something bothered me": tell your family in two taps, without getting in trouble
• Anchor Time: phone-free dinner, homework or outdoor timers
• "What my family can see": sharing switches and a pause button you control

FOR PARENTS AND CAREGIVERS
• A weekly Family Digest written as a conversation, never a diagnosis
• "Worth a conversation?" alerts with a suggested conversation starter
• 20-day trends: mood, screen-time category mix, how screen-off transitions go
• App Lens: what popular apps are, age ratings, and settings to check together
• Family goals your child agrees to. Anchor never blocks apps
• Invite an older sibling or mentor as a Gen Z guide

MONITORING DISCLOSURE
Anchor shares a summary of app usage time and check-ins from a child's device with the family members the child connected with. It shows a persistent "Anchor is on" notification on the child's device, never hides its icon, and never reads messages, photos, keystrokes or screen contents. Anchor is intended only for parents and their children, with the child's knowledge.

Anchor does not diagnose any condition. If you're worried about a child's safety, contact a professional or local helpline.

## App content answers

| Section | Answer |
|---|---|
| Ads | No ads |
| App access | All features available via "Try the demo on this phone" (no login needed) |
| Target audience | 18+ (parents). The app includes a kid experience used together with a parent; follow the Families questionnaire honestly |
| Content rating | Complete IARC questionnaire: no violence, no user-to-user public chat, shares info within a family |
| News app | No |
| Health apps | Not a medical device; wellbeing reflection only |
| Government app | No |
| Financial features | None |
| **isMonitoringTool** | Declared in manifest: `child_monitoring` |

### Data safety

| Data type | Collected | Shared | Purpose | Optional? |
|---|---|---|---|---|
| App activity → App interactions / other user-generated content (check-ins, flags, shared links) | Yes | No third parties (delivered to family members the user connected) | App functionality | Child chooses per type |
| App activity → Installed apps / in-app usage time (app names + minutes) | Yes | Family only | App functionality | Yes (toggle) |
| App info and performance | No | No | | |
| Photos / videos / audio | **No** (camera preview is never stored) | No | | |
| Location, contacts, messages, financial, health | No | No | | |

- Data encrypted in transit: **Yes** (HTTPS relay)
- Users can request deletion: **Yes** (in-app reset; email for relay data)
- Committed to Families Policy: **Yes**

### Sensitive permissions and declarations

**Usage access (`PACKAGE_USAGE_STATS`)**: core feature: on a child's device, with consent, Anchor summarizes minutes per app category and detects when a screen session ends to ask a one-tap mood check-in. The family grants it in system Settings.

**Foreground service type `specialUse`**: justification text:
> On a child's device, after the child and parent agree, Anchor runs a lightweight companion that (1) keeps a persistent "Anchor is on" disclosure notification visible, as required for transparent child-monitoring apps, and (2) notices when a long screen session ends to show a one-tap Transition Check about 75 seconds later. It does not use camera, microphone, location or screen contents. Users can pause sharing at any time.

Record a 30-second video: kid onboarding → press-and-hold consent → notification visible → use an app → Transition Check notification appears.

**Camera**: Vibe Check mirror only, while the child has it on screen; no capture or analysis.

## Release checklist

1. Put your contact email in `web/privacy.html` → redeploy Render
2. Rebuild with your Render URL: `./gradlew bundleRelease -PanchorRelayUrl=https://<service>.onrender.com`
3. Increase `versionCode` in `android/app/build.gradle.kts` for every upload
4. Upload AAB to **Internal testing**, add testers' emails, share the opt-in link
5. Personal developer accounts: run **Closed testing** with ≥ 12 testers for 14 days before applying for production
6. Keep `anchor-upload.jks` and `android/keystore.properties` backed up privately (enroll in Play App Signing when prompted)
