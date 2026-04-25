---
stepsCompleted: ['step-01-init', 'step-02-discovery', 'step-02b-vision', 'step-02c-executive-summary', 'step-03-success', 'step-04-journeys', 'step-05-domain', 'step-06-innovation', 'step-07-project-type', 'step-08-scoping', 'step-09-functional', 'step-10-nonfunctional', 'step-11-polish']
inputDocuments:
  - '_bmad/outputs/product-brief-hanative.md'
  - '_bmad/outputs/brainstorming/brainstorming-session-20260420-1918.md'
briefCount: 1
researchCount: 0
brainstormingCount: 1
projectDocsCount: 0
workflowType: 'prd'
classification:
  projectType: mobile_app (platform-foundation)
  domain: Consumer / Smart Home + Developer Tools
  complexity: high
  projectContext: greenfield
  notes: V1-as-foundation-for-V2. SDK component contracts defined in V1. Architecture decisions are public API decisions. HA ecosystem constraints apply.
---

# Product Requirements Document - HaNative

**Author:** Jeremy Blanchard
**Date:** 2026-04-20

## Executive Summary

Home Assistant is the most capable self-hosted smart home platform available. Its companion app is a WebView wrapper. Power users who have invested hundreds of hours building complex automations, entity hierarchies, and contextual rules are handed a mobile interface that cannot represent what they built — every entity gets a toggle, every dashboard requires manual switching, every tap carries page-load latency.

HaNative is a native KMP + Compose Multiplatform control interface for Home Assistant, targeting power users on Android and iOS. It coexists with the HA companion app — companion handles infrastructure (push notifications, background sensors, location, device registration); HaNative owns the experience layer: dashboards, entity control, theme. The app connects directly to HA via local WebSocket, evaluating live entity states to deliver a control surface that matches the semantic complexity of a real smart home.

The primary competitor is not the official companion app. Power users currently treat mobile as a degraded fallback and use the browser as their primary control surface. HaNative's job-to-be-done is full-fidelity device control from anywhere — not a better companion app, but the elimination of the browser as the control dependency.

V1 ships the experience layer: one curated theme, named dashboard creation and switching, all core entity types controllable, local-first WebSocket connectivity (Nabu Casa cloud fallback), and home screen widgets. V2 ships the platform: an open SDK + curated marketplace where Compose developers sell signed, versioned dashboard templates. V1 architecture treats all component contracts and data models as public API decisions — V2 SDK compatibility is a V1 constraint, not a V2 retrofit.

### What Makes This Special

The companion app treats entities as data points. HaNative treats them as the vocabulary of a home — inputs to a context engine that understands what "pool lights on at 7pm" means differently than "pool lights on at 7am." The interface responds with physical fidelity: spring physics, haptics choreographed per entity type, micro-animations authored as part of the theme component contract. This is not decoration — it is the trust signal that the system understood your intent.

The deeper competitive position is the designer marketplace. No HA client has a curated template ecosystem. The open SDK resolves open-source community resentment (free to build, optional to sell); the marketplace provides a revenue model and a quality flywheel. Power users who can't design get premium aesthetics; Compose developers who can design get a ready-made audience and recurring passive income.

The falsifiable V1 assumption: HA power users will change their primary client if the alternative respects their mental model.

## Project Classification

- **Project Type:** Mobile app (platform-foundation) — KMP + Compose Multiplatform, iOS + Android, wall tablet kiosk, widget surfaces. V1 architecture is V2 SDK foundation.
- **Domain:** Consumer / Smart Home + Developer Tools (V2)
- **Complexity:** High — multi-surface, HA WebSocket integration, premium control UI layer (coexists with companion app), context rules engine, KMP/CMP maturity risk, V2 SDK backward-compatibility constraint
- **Project Context:** Greenfield with established ecosystem constraints (HA WebSocket API, App Store / Play Store compliance, existing user mental models)

## User Journeys

### Journey 1: First Launch — From Install to First Dashboard

**Persona:** Marcus, 34. Backend engineer. Runs Home Assistant on a Raspberry Pi 5 with 180 entities — lights, climate zones, door sensors, media players. He's been using the companion app since 2021. It works, mostly. He's opened a browser tab on his phone more times than he can count to do things the app handles badly. He saw the HaNative beta post on r/homeassistant. Downloaded it in under a minute.

**Opening scene:** Marcus opens HaNative for the first time. He expects a login screen and a dump of all 180 entities. Instead the app asks for his HA instance URL and nothing else. Connection established. The app reads his entity list silently.

**Rising action:** He's dropped into an empty dashboard state — not a blank error, a designed empty state that shows a single prompt: "Add your first card." He taps it. An entity picker surfaces, filtered by the 10 core domains, sorted by how recently HA has seen activity on each entity. His living room lights are first. He adds a light group card. Then the thermostat. Then the TV media player. Five cards in under three minutes — no documentation consulted, no YAML written.

**Climax:** He taps the light card. The light in his living room turns off. The card state updates before he consciously registers the change. He taps it again. The light comes back on. He does it a third time, not because he needs to — because the feedback feels different from anything he's used before.

**Resolution:** Marcus has a working dashboard in 8 minutes. It's not comprehensive. It's not polished yet. But it's his — his entities, his layout, responding like they're supposed to. He screenshots it and posts to the thread he found the app in. "First dashboard, 8 minutes. This feels right."

**Capabilities revealed:** Entity picker with activity-sort, card-based dashboard builder, WebSocket entity state subscription, immediate state reflection on control action, designed empty state.

---

### Journey 2: Daily Control — The Retention Loop

**Persona:** Same Marcus, three weeks later.

**Opening scene:** 6:47am. Marcus picks up his phone. HaNative is already on his home screen. He opens it without thinking — the same reflex that used to open the companion app, or worse, a browser tab.

**Rising action:** He's on his Morning dashboard — the one he built last week with his bedroom lights, thermostat, and coffee maker switch. He taps the bedroom lights off (already off, the house beat him to it via an automation — the card shows the truth). He nudges the thermostat up one degree. He turns on the coffee maker. Three taps, 11 seconds.

**Climax:** His partner calls from the kitchen — "can you turn on the living room lights?" Marcus switches to his Living Room dashboard with one tap. Two taps total including the light. Done before the sentence finished echoing.

**Resolution:** He puts his phone down. He did not open a browser. He did not open the companion app. He did not wait for a page to load. HaNative is now his home's control surface — not because it has more features, but because it never makes him wait or wonder.

**Capabilities revealed:** Named dashboard persistence, one-tap dashboard switching, reliable WebSocket state accuracy, control response within perceptible threshold.

---

### Journey 3: Wall Tablet Setup — The Kiosk *(Growth)*

**Persona:** Marcus again. He bought a Fire HD 10 for the kitchen wall. His plan: mount it, run HaNative in kiosk mode as a permanent kitchen control panel.

**Opening scene:** Marcus installs HaNative on the tablet, connects to HA. He creates a new dashboard — "Kitchen Wall" — designed for the larger screen: bigger touch targets, climate front and center, the kettle switch, the kitchen lights, a media player for the kitchen speaker.

**Rising action:** He enables kiosk mode. The OS chrome disappears. The app fills the screen. He locks the dashboard — it won't accidentally switch if someone swipes. The display dims after 5 minutes of inactivity; a tap anywhere wakes it without unlocking the phone or navigating away.

**Climax:** His partner walks into the kitchen, glances at the panel, taps the kettle switch without breaking stride. Doesn't mention it. Doesn't need to. It's just there, and it works.

**Resolution:** The tablet has been on the kitchen wall for two weeks. Nobody in the house thinks about it as an "app" anymore. It's part of the kitchen. The browser kiosk hack Marcus had running before required a reboot every few days. HaNative hasn't needed one.

**Capabilities revealed:** Kiosk mode (fullscreen, OS chrome hidden), dashboard lock, dim-on-idle with tap-to-wake, layout optimized for tablet touch targets, persistent authenticated session.

---

### Journey 4: Entity Misbehaves — Edge Case and Recovery

**Persona:** Marcus. Tuesday evening. His internet goes out mid-control session.

**Opening scene:** Marcus taps his living room lights off. The card doesn't update. He taps again. Nothing. He glances at his router — orange light. Internet is down.

**Rising action:** HaNative detects the local WebSocket is still live (HA is on his LAN). State continues updating from the local connection — the internet outage is irrelevant to local control. The lights card reflects reality. He turns off the lights successfully.

**Alternate path — full local outage (router down):** Marcus taps a card. The action registers but state doesn't confirm. HaNative shows the last known state with a visual indicator: "Last updated 43 seconds ago." It doesn't crash. It doesn't show a broken state. It shows what it knows and when it knew it.

**Climax:** Marcus glances at the status indicator, understands immediately — HA is unreachable. He goes to the breaker room, resets the router. HA reconnects. HaNative's WebSocket reconnects automatically within seconds. Entity states refresh. No manual intervention in the app required.

**Resolution:** The outage lasted 4 minutes. Marcus never left HaNative. He never saw an error screen. He saw honest state and automatic recovery. The app behaved like something that understood his infrastructure, not something that panicked when the network blinked.

**Capabilities revealed:** Local WebSocket priority over cloud, stale-state indicator with timestamp, automatic WebSocket reconnection, graceful degradation without crash or error screen, last-known-state display during disconnection.

---

### Journey Requirements Summary

| Capability | Revealed By |
|---|---|
| Entity picker with activity-sort | Journey 1 |
| Card-based dashboard builder | Journey 1 |
| Designed empty state | Journey 1 |
| Immediate state reflection on control | Journey 1, 2 |
| Named dashboard persistence | Journey 2 |
| One-tap dashboard switching | Journey 2 |
| Kiosk mode (fullscreen, chrome-hidden) | Journey 3 |
| Dashboard lock | Journey 3 |
| Dim-on-idle + tap-to-wake | Journey 3 |
| Persistent authenticated session | Journey 3 |
| Local WebSocket priority (LAN-first) | Journey 4 |
| Stale-state indicator with timestamp | Journey 4 |
| Automatic WebSocket reconnection | Journey 4 |
| Last-known-state graceful display | Journey 4 |

## Success Criteria

### User Success

- HaNative becomes the primary device control surface — user opens it instead of the browser or companion app for entity control
- First meaningful dashboard created within 10 minutes of first launch, without external help or documentation
- Core entity types feel better to control in HaNative than any alternative — interactions are faster and confirmed before attention shifts
- No entity in the core surface requires fallback to another interface for basic control

### Business Success

- Primary signal: retention over companion/browser usage — users default to HaNative for control, not passive install count
- Community names HaNative as the answer to "what do you use for device control on mobile?" in HA forums and r/homeassistant
- At least one qualitative community signal (unprompted post, screenshot share, "how do I get that?" thread) within 30 days of beta
- Platform-pivot tripwire: if 5+ users independently request custom components or template sharing within 6 months, accelerate SDK scoping for V2
- Zero paid acquisition — all growth from HA forums, r/homeassistant, YouTube reviewers

### Technical Success

- All 10 core entity domains fully controllable: `light`, `switch`, `climate`, `cover`, `media_player`, `sensor`/`binary_sensor` (read-only display), `input_boolean`, `input_select`, `script`, `scene`
- Entity state reflects HA truth promptly after control action — no stale state shown
- Graceful degradation for non-core entity types — display state where possible, no crashes
- One theme complete and consistent across phone and widget surfaces

### Measurable Outcomes

| Outcome | Metric | Bar |
|---|---|---|
| Primary control surface | Opens HaNative for control, not browser | Consistent across beta users |
| Onboarding speed | Time to first meaningful dashboard | ≤10 minutes, unaided |
| Entity coverage | Core domains fully actionable | 10/10 entity domains |
| Community signal | Unsolicited organic mention | ≥1 within 30 days of beta |
| Platform signal | Custom component / sharing requests | 5+ = accelerate V2 SDK |

## Product Scope

### MVP

- One curated theme (phone + widget)
- 10 core entity domains fully controllable via HA WebSocket
- Named dashboard creation and one-tap manual switching
- Local WebSocket connection (Nabu Casa cloud fallback)

### Growth Features (Post-MVP)

- Context-aware dashboard engine — automatic switching based on HA entity state rules
- Context-aware widgets (context rules drive widget content)
- Demo-first onboarding with simulated entity data and theme preview
- App registered as HA device entity (HA automations trigger context switches)

### Vision (V2+)

- Open SDK for Compose developers (component contract published)
- Curated template marketplace — Apple IAP + Google Play Billing
- Designer analytics dashboard
- Apple Watch / WearOS companion

## Domain-Specific Requirements

### HA Ecosystem Constraints

- The HA WebSocket API is community-maintained with no SLA. Breaking changes can occur across HA releases. HaNative must handle API version mismatches gracefully and degrade rather than crash.
- Entity domain coverage is bounded by HA's own domain model. New entity types introduced in HA releases should be addable without requiring an app update where possible (extensible entity renderer architecture).
- Nabu Casa terms of service govern the cloud relay fallback path. Cloud connectivity is user-initiated and optional — HaNative does not depend on it for core functionality.

### App Store / Platform Compliance

- V2 marketplace must use Apple IAP and Google Play Billing for in-app digital goods purchases. No alternative payment flow is permissible inside the app without risking guideline violation.
- Kiosk mode behavior on iOS must comply with App Store review guidelines for single-app mode and display lock patterns.
- Background execution constraints on iOS limit future sensor or location integration — companion app remains responsible for those OS-level capabilities.

### Privacy — Data Minimization

- HA instance URL and auth token are stored locally on-device only. No credentials are synced to any external server.
- Entity state data remains on-device and on the local network. HaNative does not transmit home topology or entity state to external servers beyond the Nabu Casa relay (which is user-configured and user-controlled).
- No analytics may expose entity names, entity counts, or home topology. Crash reporting and usage analytics must be aggregated and anonymized.

## Innovation & Novel Patterns

### Detected Innovation Areas

**1. The Interface as Automation Target**

HaNative registers itself as a device entity within Home Assistant. It exposes `active_context` and `current_dashboard` as controllable HA entities. This means HA automations — the same YAML-driven rules that control lights, climate, and locks — can trigger interface transitions in HaNative. Arriving home triggers a context switch. A motion sensor at midnight triggers a different dashboard than one at noon.

This inverts the normal relationship between smart home app and smart home platform. The app is no longer a passive viewer of HA state — it is a participant in HA's automation graph. No existing HA client has this architecture.

**2. Entity-State-Driven Dashboard Engine**

Dashboards in HaNative switch automatically based on user-defined rules evaluated against live HA entity states. The rule language mirrors HA's own automation condition syntax — familiar to power users, zero learning curve. The result: the mobile interface adapts to context without user interaction. This is not a manual dashboard switcher with a shortcut; it is a reactive UI layer driven by the same entity state graph that drives the physical home.

**3. Compose Multiplatform as Designer Distribution Medium**

Compose Multiplatform's composable architecture enables a novel distribution model: designers author complete component sets (cards, state variants, size targets, haptic contracts) as signed, versioned packages. Compose Remote Layout (currently in beta) extends this further — potentially enabling server-driven UI updates without app releases. The V2 marketplace distributes these packages to end users via standard platform billing. This is unprecedented in mobile app theming: themes that are not CSS overrides or asset bundles, but full native component implementations with guaranteed rendering fidelity.

### Market Context & Competitive Landscape

- No existing HA mobile client (companion app, Ovio, or others) implements UI-as-HA-entity or entity-driven dashboard switching.
- Mobile app theme marketplaces exist (e.g., icon packs, launcher themes on Android) but distribute asset bundles. HaNative's marketplace distributes compiled Compose component implementations — fundamentally different in capability and quality guarantee.
- Compose Remote Layout is pre-production. HaNative is positioned to be an early production consumer of this capability, with marketplace distribution as the compelling use case.

### Validation Approach

- **UI-as-HA-entity:** V1 validation — does the HA community treat this as a meaningful automation target? Signal: are context switches appearing in shared automation YAML examples in community posts?
- **Context engine:** V1 validation — do beta users create context rules and use them daily, or do they build dashboards and switch manually? Retention pattern reveals whether the engine is used or ignored.
- **Marketplace architecture:** V2 pre-validation — recruit 3-5 Compose developers before SDK launch. If they can implement a complete component set against the SDK contract without support, the contract is sound.

### Risk Mitigation

| Risk | Mitigation |
|---|---|
| Compose Remote Layout doesn't reach production stability | V2 marketplace works without it — packages are distributed as compiled components, not remote layouts. Remote Layout is an enhancement, not a dependency. |
| HA breaks the WebSocket entity registration API | App-as-entity is a Growth feature; V1 core (WebSocket control) is unaffected |
| Context engine adoption is low | Context engine is additive — users who don't use it still have a fully functional dashboard app |
| Designer marketplace chicken-and-egg | Pre-recruit designers before SDK launch; seed with 5-10 templates on day one |

## Mobile App Specific Requirements

### Project-Type Overview

HaNative is a KMP + Compose Multiplatform mobile app targeting Android and iOS, with wall tablet (Android) as a third supported surface. The shared KMP business layer covers HA WebSocket client, entity state management, context rules engine, and dashboard persistence. The Compose Multiplatform UI layer targets phone, widget, and kiosk surfaces from a single codebase with platform-specific adaptations where necessary.

### Platform Requirements

| Surface | Platform | Status |
|---|---|---|
| Phone | Android | V1 launch target |
| Phone | iOS | V1 — timing TBD (pending CMP stability assessment) |
| Home screen widget | Android (Glance) | V1 |
| Home screen widget | iOS (WidgetKit) | V1 with iOS launch |
| Wall tablet kiosk | Android | Growth |
| Watch | WearOS / Apple Watch | V3 |

iOS ships when polished — not on a fixed deadline. CMP on iOS is production-capable but ecosystem maturity (navigation, multiplatform testing, platform interop) requires ongoing assessment. Android is the primary development target; iOS follows when quality bar is met.

### Device Permissions

| Permission | Purpose | Required |
|---|---|---|
| Haptic feedback | Entity control confirmation | No permission required (API-level) |
| Home screen widget | Glanceable entity state | Standard widget permission |
| Always-on display | Wall tablet kiosk keep-awake | `WAKE_LOCK` (Android) |
| Network access | HA WebSocket + Nabu Casa relay | Standard |

Explicitly out of scope for HaNative: camera, microphone, location, background sensors, push notification registration. These remain with the companion app.

### Offline Mode

HaNative is not a fully offline app — entity control requires an active connection to HA. Offline behavior is defined as graceful degradation:

- **Local network available, internet down:** Full functionality via LAN WebSocket. Internet outage is transparent to the user.
- **HA unreachable (local + cloud):** Last known entity states displayed with a staleness indicator (timestamp of last update). No crash, no empty state, no error screen. Dashboard remains navigable.
- **WebSocket reconnection:** Automatic on connection restore — no user action required. State refreshes on reconnect.

### Push Notification Strategy

Push notifications are not in HaNative scope. The HA companion app handles all push notification registration, delivery, and management. HaNative does not register for APNs or FCM. This is a deliberate architectural boundary: HaNative owns the experience layer; companion app owns the infrastructure layer.

### Store Compliance

**V1:**
- Standard App Store and Google Play review compliance
- Kiosk mode implementation must comply with App Store guidelines for display-lock patterns on iOS
- No in-app purchases in V1

**V2:**
- Apple IAP required for all digital goods sold within the iOS app
- Google Play Billing required for all digital goods sold within the Android app
- No alternative payment flow permissible inside the app without risking guideline violation
- Template pricing set by designers; platform takes a percentage cut via standard billing APIs

### Implementation Considerations

- KMP shared module covers: HA WebSocket client, entity domain model, context rules engine, dashboard persistence, auth token storage
- Compose Multiplatform UI module covers: all screens, card components, theme system, animation/haptic contracts
- Platform-specific `actual` implementations required for: haptic engine (Android `VibrationEffect` vs iOS `UIFeedbackGenerator`), widget rendering (Glance vs WidgetKit), always-on display management
- V1 internal theme must implement the full component contract that V2 SDK will publish — internal theme is the reference implementation and proof-of-contract

## Project Scoping & Phased Development

### MVP Strategy & Philosophy

**MVP Approach:** Experience MVP — the minimum that makes power users say "this feels different." Not feature-complete. Quality-complete on the core control loop. A solo developer building for their own daily use; scope must be achievable without a fixed deadline or team.

**Resource Requirements:** Solo developer (KMP + Compose Multiplatform). No design contractor required for V1 — one curated theme shipped by the developer.

### MVP Feature Set (Phase 1)

**Core User Journeys Supported:**
- Journey 1: First launch → first dashboard (onboarding to first meaningful dashboard in ≤10 min)
- Journey 2: Daily control loop (primary device control surface, replacing browser)
- Journey 4: Edge case recovery (WebSocket drop, stale state, automatic reconnect)

**Must-Have Capabilities:**
- HA WebSocket connection (local LAN-first, Nabu Casa cloud fallback)
- 10 core entity domains fully controllable: `light`, `switch`, `climate`, `cover`, `media_player`, `sensor`/`binary_sensor` (read-only), `input_boolean`, `input_select`, `script`, `scene`
- Named dashboard creation and one-tap manual switching
- Card-based dashboard builder with entity picker (activity-sorted)
- Designed empty state and first-run experience
- Immediate entity state reflection on control action
- Stale-state indicator with timestamp on disconnection
- Automatic WebSocket reconnection on restore
- Home screen widget (phone — static or last-known state)
- One curated theme (phone + widget)
- Persistent authenticated session (no re-login on relaunch)

### Post-MVP Features

**Phase 2 — Growth:**
- Context-aware dashboard engine (auto-switching based on HA entity state rules, HA-pattern rule builder)
- Context-aware widget (widget content driven by active context rules)
- Demo-first onboarding (simulated entity data + theme preview before HA connection)
- App registered as HA device entity (exposes `active_context`, `current_dashboard` — HA automations can trigger context switches)
- Wall tablet kiosk mode (fullscreen, OS chrome hidden, dashboard lock, dim-on-idle + tap-to-wake)
- Rule gallery (pre-built context rule templates: "Weekday morning", "Someone arrives", "Movie time")

**Phase 3 — V2 Platform:**
- Open SDK published (component contract: card types, state variants, size buckets, haptic contracts)
- Curated template marketplace (Apple IAP + Google Play Billing, editorial discovery, live preview trial)
- Versioned signed designer packages with update notifications
- Marketplace inside onboarding flow

**Phase 4 — V3 Expansion:**
- Apple Watch + WearOS companion (context-driven top-3 entities, zero separate config)
- Designer analytics dashboard (install count, active users, entity mapping patterns)
- User-as-publisher flow (build and publish custom templates)
- iPad adaptive layouts (split-pane dashboard)
- Seasonal/event themes

### Risk Mitigation Strategy

**Technical Risks:**
- *KMP/CMP cross-platform maturity* — widget surfaces (Glance vs WidgetKit) and haptic platform bridges are the highest-risk V1 integrations. Mitigation: implement Android widget and haptics first; iOS widget follows when CMP maturity allows. iOS launch timing is not fixed.
- *HA WebSocket API stability* — unofficial API, no SLA. Mitigation: isolate WebSocket client behind an abstraction layer; API version mismatches fail gracefully without crashing.

**Market Risks:**
- *Power users don't switch primary control surface* — the falsifiable V1 assumption. Mitigation: instrument session patterns from beta day one; if users open HaNative then immediately open companion or browser, investigate why before Growth investment.

**Resource Risks:**
- *Solo developer capacity* — MVP scope is intentionally narrow. Kiosk mode, context engine, and demo onboarding are deferred to Growth precisely because they each represent sprint-scale work. If capacity is further constrained, widget can also slide to Growth without breaking the core value proposition.

## Functional Requirements

### HA Connection & Authentication

- **FR1:** User can connect the app to their HA instance by providing its URL
- **FR2:** User can authenticate with their HA instance using a long-lived access token or OAuth
- **FR3:** App maintains an authenticated session across relaunches without requiring re-login
- **FR4:** User can disconnect from a HA instance and connect to a different one

### Entity State & Control

- **FR5:** User can view real-time state of HA entities within all supported domains
- **FR6:** User can control HA entities within supported domains (toggle, set value, trigger)
- **FR7:** App reflects entity state change immediately following a user control action
- **FR8:** App reflects entity state changes triggered by HA automations or other clients
- **FR9:** Sensor and binary_sensor entities display read-only state without control affordances
- **FR10:** App supports the following entity domains: `light`, `switch`, `climate`, `cover`, `media_player`, `sensor`, `binary_sensor`, `input_boolean`, `input_select`, `script`, `scene`
- **FR11:** App displays non-core entity types with last-known state in read-only mode without crashing

### Dashboard Management

- **FR12:** User can create a named dashboard
- **FR13:** User can rename a dashboard
- **FR14:** User can delete a dashboard
- **FR15:** User can switch between named dashboards with a single interaction
- **FR16:** App persists all dashboard configurations across sessions and relaunches

### Card Configuration & Entity Picker

- **FR17:** User can add an entity card to a dashboard via an entity picker
- **FR18:** Entity picker presents entities filtered to supported domains, sorted by recent HA activity
- **FR19:** User can remove an entity card from a dashboard
- **FR20:** User can reorder entity cards within a dashboard
- **FR21:** User can configure entity-specific card properties where applicable (e.g., light brightness range, climate temperature limits)

### Theme & Physical Design Language

- **FR22:** App applies one curated visual theme consistently across all screens and surfaces
- **FR23:** App provides haptic feedback per entity domain on control interactions
- **FR24:** App provides micro-animations on entity state transitions as defined by the theme contract
- **FR24b:** App provides animated transitions between dashboards as defined by the theme contract
- **FR25:** Theme visual, haptic, and animation behavior is authored as part of the component contract — not configurable by the user in V1

### Home Screen Widget

- **FR26:** User can add a home screen widget that displays entity state from their dashboards
- **FR27:** Widget displays last known entity state when HA is unreachable
- **FR28:** Widget reflects current entity state when HA connectivity is available

### Connectivity & Resilience

- **FR29:** App connects to HA via local LAN WebSocket as the primary connection method
- **FR30:** App falls back to Nabu Casa cloud relay when local WebSocket connection is unavailable
- **FR31:** App displays a staleness indicator with last-updated timestamp when entity state cannot be refreshed
- **FR32:** App automatically reconnects to the HA WebSocket on connection restore without user action
- **FR33:** App displays last-known entity states during disconnection — no crash, no empty screen, no error state
- **FR34:** App handles HA API version mismatches gracefully without crashing

### Privacy & Data

- **FR35:** App stores HA credentials (URL and auth token) locally on-device only — no external sync
- **FR36:** App does not transmit entity names, entity state, or home topology to any external server beyond the user-configured Nabu Casa relay
- **FR37:** App does not request location, camera, microphone, or push notification permissions

### Context Engine *(Growth)*

- **FR38:** User can define a named context using entity-state conditions with an HA-pattern rule builder (AND/OR logic, entity picker, condition blocks)
- **FR39:** App evaluates active context rules against live entity state and transitions the active dashboard automatically when a matching context is detected
- **FR40:** When multiple contexts match simultaneously, the first-created rule takes priority
- **FR41:** User can select from a gallery of pre-built context rule templates
- **FR42:** App exposes `active_context` and `current_dashboard` as controllable entities within HA, allowing HA automations to trigger context switches

### Kiosk Mode *(Growth)*

- **FR43:** User can enable kiosk mode to display the app fullscreen with OS chrome hidden
- **FR44:** User can lock the active dashboard in kiosk mode to prevent accidental switching
- **FR45:** App dims the display after a configurable idle period in kiosk mode and wakes on any tap

### Onboarding *(Growth)*

- **FR46:** App presents a demo experience with simulated entity data and theme preview before HA connection is required
- **FR47:** App auto-generates a starter dashboard populated with the user's most-recently-active entities immediately after HA connection

## Non-Functional Requirements

### Performance

Control responsiveness is the primary competitive differentiator — "the app responds before your thumb lifts" is the stated UX success moment. Performance targets are not aspirational; they are the product's core value proposition.

- Entity control action to visible state confirmation: ≤500ms on local LAN WebSocket connection
- App launch to first dashboard visible (warm start / session resume): ≤1 second
- Dashboard switch (named dashboard, one-tap): ≤200ms
- Entity picker open and populated: ≤500ms for up to 500 entities
- UI animations run at 60fps on supported hardware — dropped frames are a theme contract failure, not a performance edge case

### Security

- HA auth token stored in platform secure storage — Android Keystore on Android, iOS Keychain on iOS. Not in SharedPreferences, UserDefaults, or unencrypted local storage.
- All Nabu Casa cloud relay traffic over HTTPS/TLS — no unencrypted fallback
- Auth token and HA instance URL must not appear in crash reports, analytics payloads, or log output

### Reliability

The "trust" emotional requirement from the vision: users must never doubt whether an action registered or whether the state they see is real.

- WebSocket reconnection attempted within 5 seconds of connection loss detection
- App must not crash on receipt of unknown entity types, malformed HA API responses, or unexpected WebSocket message formats
- Dashboard configuration persists locally — connection loss or app restart must not clear or corrupt dashboard data
- Stale state display is preferable to an error screen in all disconnection scenarios
- App startup must succeed even when HA is unreachable — cached dashboard layout renders, entity state shows as stale
