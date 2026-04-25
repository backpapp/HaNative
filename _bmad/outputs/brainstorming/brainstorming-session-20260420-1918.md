---
stepsCompleted: [1, 2, 3a]
inputDocuments: []
session_topic: 'KMP + Compose native app for Home Assistant power users — replacing HA web portal with smooth, fast, fancy mobile interface'
session_goals: '1. Native HA control app (Android/iOS via KMP). 2. Marketplace to sell Compose dashboards'
selected_approach: 'progressive-flow'
techniques_used: ['What If Scenarios', 'Six Thinking Hats', 'SCAMPER Method', 'Decision Tree Mapping']
ideas_generated: []
context_file: ''
---

# Brainstorming Session Results

**Date:** 2026-04-20 19:18

## Session Overview

**Topic:** KMP + Compose native app for Home Assistant power users
**Goals:** Native HA control app (Android/iOS via KMP) + marketplace to sell Compose dashboards

### Session Setup

_Fresh session. No prior context file. Android/Kotlin project (HaNative)._

## Technique Selection

**Approach:** Progressive Technique Flow
**Journey Design:** Systematic development from exploration to action

**Progressive Techniques:**

- **Phase 1 - Exploration:** What If Scenarios — maximum idea generation without constraints
- **Phase 2 - Pattern Recognition:** Six Thinking Hats — comprehensive multi-angle analysis
- **Phase 3 - Development:** SCAMPER Method — systematic product refinement
- **Phase 4 - Action Planning:** Decision Tree Mapping — implementation pathways

**Journey Rationale:** HA app + marketplace has both product and platform dimensions. What If unlocks both; Six Hats covers technical/risk/user angles; SCAMPER refines the product; Decision Tree maps MVP vs marketplace expansion paths.

---

## Phase 1: What If Scenarios — Ideas Generated

### UX / Interface

**[UX #1]: The Bottom Sheet Doctrine**
_Concept:_ Every secondary action surfaces as a bottom sheet — instant, dismissible, no navigation stack. Tap a light → sheet slides up with controls. Done → flick down. Never leave the dashboard.
_Novelty:_ HA navigation is hierarchical. This is radial — everything within one gesture of the dashboard.

**[UX #2]: Opinionated Theme System**
_Concept:_ Not infinite components — curated themes with locked aesthetics. Pick "Midnight", "Clean", "Industrial". Everything — typography, card shape, color, motion — is designed as one system. No Frankenstein dashboards.
_Novelty:_ HA's power = endless customization. Your app's power = curation. Less choice = better taste.

**[UX #3]: Context-Aware Dashboards**
_Concept:_ Dashboard shifts automatically based on activity + location using entity-based rules. Morning → lights + temperature. Pool Evening → pool lights + music. Dashboard collapses to exactly what you need, exactly now.
_Novelty:_ HA dashboards are static views you manually switch. This is a living dashboard with no switching needed.

**[UX #32b]: HA-Pattern Rule Builder**
_Concept:_ Context rules use same condition/trigger pattern as HA automations — familiar mental model. Entity picker, condition blocks, AND/OR logic. No YAML but same concepts. Zero learning curve for HA users.
_Novelty:_ Approachable for power users who aren't developers. HA muscle memory = instant adoption.

### Context Engine

**[Context #4]: Entity-Driven Context Rules**
_Concept:_ User defines contexts as if/then rules using existing HA entities. "If media_player.pool is playing AND time is 18:00–23:00 → Pool Evening context." App reads entity states, evaluates rules, switches dashboard silently. Presence entities (person.X) are just another condition value.
_Novelty:_ HA automations control devices. No app uses the same engine to control the UI itself.

### Platform / Multi-Surface

**[Platform #11]: Design Language Choice**
_Concept:_ Themes come in Material and Cupertino variants. User picks preference once. Wall tablet = same Compose, full-screen kiosk mode, no OS chrome.
_Novelty:_ Not platform-forced — user-chosen aesthetic on any surface.

**[Platform #12]: Wall Tablet Kiosk Mode**
_Concept:_ App detects or allows "wall mode" — fullscreen, always-on with dimming, tap-to-wake. Dashboard optimized for 10" fixed viewing at wall distance.
_Novelty:_ Current HA wall setups are hacked browser kiosks. Native Compose wall mode = smooth animations, live state — impossible in a browser on a Fire tablet.

**[Surface #15b]: Context-Aware Widgets**
_Concept:_ Widget content shifts with active context — same rules engine drives widgets. Pool Evening → widget shows pool lights + music. One widget slot, infinite intelligence.
_Novelty:_ No widget system does this. Widget IS a live window into your current context.

**[Surface #16b]: Customizable Alert Surface**
_Concept:_ User defines which entities can push to lock screen / Dynamic Island / notification. Per-entity config. Designer themes include lock screen layouts.
_Novelty:_ Curated alerting, not notification spam. User controls signal-to-noise.

**[Surface #17b]: Watch as Context Remote**
_Concept:_ Watch shows exactly the active context's top 3 entities. Context switches on phone → watch updates instantly. Zero separate watch config.
_Novelty:_ Context rules do all the work — no extra setup.

**[LargeScreen #31]: iPad Split Dashboard**
_Concept:_ iPad layout uses split-pane — two dashboards simultaneously, or dashboard + entity/settings panel. Drag-and-drop entity card placement for layout editing.
_Novelty:_ Compose adaptive layouts make this natural. No HA app uses iPad screen real estate properly.

### Onboarding

**[Onboarding #13]: Instant Beautiful Default**
_Concept:_ After connecting HA, app scans entities and auto-generates a beautiful starter dashboard. Lights → elegant toggle cards. Camera → live thumbnail. Climate → big temperature dial. First screen looks better than anything they've built in HA.
_Novelty:_ First 10 seconds sell the entire product.

### HA Integration

**[Integration #19b]: App as HA Device Entity**
_Concept:_ App registers itself as a device in HA. Exposes entities: active_context, current_dashboard. HA automations can SET the context — arrive home → HA automation fires → app switches context. UI becomes an automation target.
_Novelty:_ No HA client has ever been a HA device. The app becomes part of the smart home.

**[Voice #19]: Context-as-Siri-Shortcut**
_Concept:_ Each user-defined context exposed as a Siri/Google shortcut. Voice triggers context switch. "Hey Siri, pool evening" → full environment shift.
_Novelty:_ Simpler than listing individual device commands. Name the moment.

### Household

**[Household #22/24]: Presence-Driven Context Layers**
_Concept:_ HA person entities are conditions in context rules. Only you home → your contexts rule. Guest profile → limited controls. Multiple people home → shared context takes priority. Implemented as standard entity-based context rules.
_Novelty:_ Presence + permission + UI layering in one system.

### Marketplace / Business

**[Business #9]: Simple Marketplace**
_Concept:_ Designers price their own templates. App takes a cut. Free stuff lives on GitHub. Clean storefront — same model as app stores.
_Novelty:_ Zero friction. Proven model.

**[Business #14]: Freemium Gate**
_Concept:_ App free forever — full HA control, default themes, context rules, kiosk mode. Marketplace = subscription. Free users can import GitHub templates manually. Paying users get one-tap install + updates.
_Novelty:_ Zero barrier to entry. Revenue from users who value curation + convenience.

**[Market #27]: Live Preview Trial**
_Concept:_ Marketplace templates previewed with user's own HA data before purchase. Entities auto-mapped. See exactly how it looks with YOUR home. Time-limited trial.
_Novelty:_ Live preview with real data = massive conversion boost. No other marketplace does this for UI templates.

**[GTM #33]: V1 = App Only**
_Concept:_ V1 ships without marketplace — focuses on core app quality. Community grows. V2 launches marketplace with 10–20 curated templates from recruited designers.
_Novelty:_ Don't build platform and product simultaneously. Nail product first.

**[GTM #34]: HA Community as Launch Channel**
_Concept:_ r/homeassistant (800k members), HA forums, YouTube reviewers. Pre-launch beta via those channels = free qualified distribution.
_Novelty:_ No paid acquisition needed. Audience already exists and is hungry.

### Designer / SDK

**[DevEx #29b]: Full Component Contract SDK**
_Concept:_ SDK defines every component a template must implement — card types, state variants, size buckets (phone/tablet/wall/widget). Designer implements all in Android Studio with preview harness + mock entity data. App verifies contract before marketplace acceptance.
_Novelty:_ Every template works everywhere. Quality guaranteed by contract.

**[Tech #25b]: Versioned Designer Packages**
_Concept:_ Templates distributed as compiled, versioned packages. Designers push updates/enhancements. Users get update notifications. Designer relationship continues post-sale — template is a living product.
_Novelty:_ Designer retains authorship and iteration ability post-sale.

**[Creator #23]: Designer Analytics Dashboard** _(V2)_
_Concept:_ Creators get web portal showing install count, active users, entity mapping patterns. Helps designers iterate.
_Novelty:_ App stores give zero UI-level analytics.

### Strategy

**[Strategy #28b]: HA Won't Compete**
_Concept:_ HA core team is web-first, open-source, community-governed. Native KMP is outside their scope and culture. Safe lane.

**Ideas generated: 26 confirmed**
**Killed: ambient screensaver, social feed, integration-official templates, rich notifications (HA already handles)**

---

## Phase 2: Six Thinking Hats — Analysis

### 🤍 White Hat — Facts
- HA ~1M+ active installs globally
- HA companion app exists — WebView UI, already has mobile sensor access
- KMP + CMP production-ready as of 2024
- No native-first, design-forward HA client exists today
- HA power users are developers/tech-savvy — GitHub, Reddit, forums are their habitat
- Compose marketplace = zero precedent

### 🔴 Red Hat — Emotions
- **Community resentment risk:** Paid marketplace on open-source platform feels wrong. HA ethos is "free, community-built." Business model must respect this.
- **Core emotional promise:** Control. Responsiveness. Elegance. Pleasant feedback. Every design decision measured against these 4 words.
- **Magic moment:** Context switches automatically without touching the phone.

### 🟡 Yellow Hat — Benefits
- Community flywheel: designers → templates → users → designers. Self-sustaining.
- KMP: one codebase, iOS + Android + wall tablet + watch
- First mover in proven hungry market. Category owner if quality lands.

### ⚫ Black Hat — Risks
- **Apple IAP Tax (30%):** Digital goods inside iOS app must use Apple IAP. Crushes margins. Mitigation: web-only purchase flow ("reader app" exemption).
- **Designer chicken-egg:** Marketplace launches empty without pre-recruited designers. Recruit 5-10 before V2 launch.
- **HA builds native:** Accepted. Moat = community + quality speed.
- **iOS CMP polish:** Accepted. Timeline gives CMP time to mature.
- **Risk profile:** Passion project first. No existential pressure. Build at right pace.

### 🟢 Green Hat — New Ideas
- **Open SDK + Closed Marketplace:** SDK open source (community freedom, no resentment). Marketplace curated + paid (quality bar, prestige). Best of both.
- **"Made with HaNative" badge:** Designer credibility + app visibility. Mutual benefit.
- **Position as companion app replacement:** Direct, confident. Power users know exactly what they're upgrading from.

### 🔵 Blue Hat — Process Summary
Key insight: Open SDK resolves open-source resentment. Marketplace = optional premium layer, not a gate. Core promise (control/responsiveness/elegance/feedback) must govern every decision.

---

## Phase 3: SCAMPER — Refinements

**S — Substitute**
- Floor plan UI: too complex, out
- Design tokens only: not enough for marketplace → becomes user preference setting (accent color, radius, light/dark). Templates stay as full SDK implementations.

**C — Combine**
- [C1] One rule definition → all surfaces (dashboard, widget, watch, lock screen) update together
- [C2] Marketplace inside onboarding — entity discovery → matched template suggestions → first install during setup. Free starter templates in V1 onboarding.
- [C3] Bidirectional HA entity loop — app reads entities for context triggers AND exposes itself as HA device entity for automation targeting

**A — Adapt**
- [A1] Spotify-style marketplace: Staff Picks, Trending, New Designers, "For Your Setup" editorial layer
- [A2] iOS Shortcuts-style context rule gallery: pre-built rule templates users start from ("Weekday morning", "Someone arrives", "Movie time")

**M — Modify**
- [M1] Feedback as designer domain: haptics + micro-animations part of SDK contract. Theme = look + feel + response.
- [M2] Minimal first launch: auto-generated dashboard = max 6–8 best-matched cards. Start clean, expand deliberately.
- [M3] Designed context transition: spring physics rearrangement, pop/sheet overlay, transition is a designed moment. Designer defines transition style per theme.

**P — Put to Other Uses**
- [P1] SDK as public Compose design system: openly published, developers build external HA tooling on it
- [P3] Wall tablet kiosk = incidental digital signage for offices/shops. Zero extra work.

**E — Eliminate**
- [E1] Replace companion app entirely: all companion features must exist. Not a dashboard viewer — full HA client.
- [E2] Local-first, cloud-optional: home WiFi → direct WebSocket. Nabu Casa = fallback only.

**R — Reverse**
- [R3] Demo-first onboarding: show beautiful demo with fake data + theme picker → THEN connect HA → entities populate chosen theme. Sell vision before asking for credentials.

---

## Phase 4: Decision Tree — Roadmap

### V1 — The App
- One built-in theme (perfected, not rushed)
- Demo-first onboarding → theme preview → connect HA → auto-generated starter dashboard (6–8 cards)
- Context rule builder (HA-pattern, rule gallery)
- Local-first WebSocket — Nabu Casa as fallback only
- Full companion app replacement: notifications, location sensors, all entity types
- Context-aware widgets
- App registered as HA device entity (bidirectional context control)
- Deferred: Watch, iPad split view, Marketplace

### V2 — The Marketplace
- Open SDK published and documented first (prerequisite gate)
- Recruit 5–10 designers pre-launch, target 10 quality templates day one
- Apple IAP accepted (30% cut, price to compensate) — smooth UX over web checkout
- Spotify-style editorial discovery (Staff Picks, Trending, New Designers)
- Live preview trial with real entity data before purchase
- Versioned, signed designer packages with update notifications
- Marketplace inside onboarding flow

### V3 — The Platform
- Designer analytics dashboard
- User → publisher flow (build and publish your own)
- Watch (WearOS + Apple Watch)
- iPad split view
- Seasonal/event themes

### Key Decisions Made
| Decision | Choice | Reason |
|----------|--------|--------|
| V1 themes | 1 | Perfection over breadth |
| Marketplace model | App free, marketplace paid | Respect OSS ethos |
| SDK | Open source | Community goodwill, no resentment |
| Apple IAP | Accept 30% | Smooth UX > margin |
| Cloud | Local-first, Nabu Casa fallback | Speed + privacy |
| Companion app | Full replacement | No reason to keep both |
| Onboarding | Demo-first | Sell vision before credentials |

---

## Session Summary

**Core promise:** Control. Responsiveness. Elegance. Pleasant feedback.

**Product:** Native KMP + Compose HA client. Replaces companion app. One perfect theme. Context-aware dashboards driven by HA entity rules. Local-first. Surfaces: phone, widget, wall tablet.

**Platform (V2):** Open SDK + curated marketplace. Designers build Composable component sets. Apple IAP. Editorial discovery. Live preview.

**Moat:** Design quality + community. HA won't compete (web-first culture). First mover in a proven, hungry market of 1M+ users.

**Session ideas generated:** 35+
**Session date:** 2026-04-20
