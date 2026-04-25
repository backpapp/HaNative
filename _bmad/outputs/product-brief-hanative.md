---
title: "Product Brief: HaNative (working title)"
status: "complete"
created: "2026-04-20"
updated: "2026-04-20"
inputs:
  - "_bmad/outputs/brainstorming/brainstorming-session-20260420-1918.md"
  - "web research: home-assistant.io, community.home-assistant.io"
---

# Product Brief: HaNative *(working title)*

## Executive Summary

Home Assistant is the world's most powerful self-hosted smart home platform — and its mobile app is its biggest embarrassment. Two million active users control their homes through a WebView wrapper that lags, stutters, and fails to take advantage of a single native platform capability. HA's own team just acknowledged this in a July 2025 blog post, hiring a dedicated Android developer after years of neglect. Even now, their roadmap is constrained by committee governance and web-first culture.

HaNative is a native KMP + Compose mobile app built for HA power users who believe their home deserves better. It replaces the companion app entirely — same sensors, same notifications, same local connectivity — but wrapped in an interface that feels physical. Spring physics. Haptic feedback choreographed per entity. Context-aware dashboards that shift automatically as life unfolds, with no tapping required. One meticulously crafted theme. The kind of app that makes opening your home feel like a product, not a workaround.

In V2, HaNative becomes a platform: an open SDK and curated marketplace where designers sell Compose dashboard templates to power users who want premium aesthetics without needing to be UI designers themselves.

## The Problem

The Home Assistant companion app is a WebView. That's not a technical detail — it's the entire experience. Every tap carries the latency of a page load. Animations don't exist. Haptics don't exist. Spring physics don't exist. The "app" is a browser with an icon.

Users who have spent hundreds of hours automating their homes — configuring entities, writing YAML, building complex automations — are handed a mobile interface that treats them like beginners. Dashboards require manual switching. Controls are buried three taps deep. The first screen after login dumps every entity in a list.

Power users have been tolerating this for years because there was no alternative. They don't want a different smart home platform — they want HA, the most capable platform on the market, in an interface that matches its capability.

Ovio, a recent indie attempt at a design-forward HA client, showed strong initial community reception — but ships without sensors, cameras, or alarm support, has no roadmap for a designer ecosystem, and depends entirely on one developer's bandwidth. The appetite is proven. The gap remains open.

## The Solution

HaNative is built around four words: **control, responsiveness, elegance, pleasant feedback**.

At its core is a **context-aware dashboard engine**. Users define contexts using the same if/then pattern they already know from HA automations — entity conditions, time windows, presence checks. The app evaluates these rules against live entity states and transitions the dashboard automatically. When multiple contexts are active simultaneously, the first-created rule takes priority — simple, predictable, no hidden logic. Pool Evening context: pool lights + music controls, full screen, nothing else. Morning context: thermostat, lights, calendar widget. The interface becomes a first-class automation target — the app even registers itself as a HA device entity, so HA automations can trigger context switches directly.

Every interaction is **physically designed**. Bottom sheets replace navigation. Context transitions use spring physics. Entity feedback — a light toggle, a lock closing, a thermostat dial turning — carries distinct haptics and micro-animations authored by the theme designer as part of the component contract, not bolted on afterward.

**Onboarding sells the vision before asking for credentials.** Users see a beautiful demo dashboard with a theme picker and simulated entity data. They choose their aesthetic. Then they connect HA — and their real entities populate the chosen theme instantly, auto-curated to 6–8 of the most relevant cards based on entity type heuristics (lights, climate, cameras, locks prioritised).

The app runs **local-first**: direct WebSocket to the HA instance on the home network, Nabu Casa cloud as fallback only. Faster, resilient when the internet drops — consistent with HA's own local-first philosophy.

## What Makes This Different

| | HA Companion | Ovio | HaNative |
|---|---|---|---|
| Native UI | ❌ WebView | Partial | ✅ Full Compose |
| Context-aware dashboards | ❌ | ❌ | ✅ |
| Full companion replacement | ✅ | ❌ | ✅ |
| Designer marketplace | ❌ | ❌ | ✅ V2 |
| Local-first | ❌ | Unknown | ✅ |
| Wall tablet kiosk | Browser hack | ❌ | ✅ |

The moat is not a single feature — it's the combination of quality execution, the designer ecosystem, and community network effects. The official HA team will improve the companion app, but they cannot ship opinionated aesthetics, a paid marketplace, or the commercial product velocity that a focused indie app can. This lane is structurally theirs to avoid.

## Who This Serves

**Primary: HA power users.** Technically fluent — developers, sysadmins, home automation enthusiasts. They've built complex HA setups. They live in GitHub, Reddit, HA forums. They know YAML. They've accepted years of mediocre mobile UX because the backend is unmatched. They are the r/homeassistant 800k member community. They will beta test, evangelize, and contribute back.

**Secondary (V2): Dashboard designers.** Compose developers with a design sensibility who want to monetize their craft. The SDK gives them a defined component contract and a ready-made audience. A designer who ships one well-crafted theme gains recurring passive income from a community hungry for premium aesthetics.

## Success Criteria

Success for V1 is simple: people use it daily and tell others about it. The HA community is vocal — good products spread organically through forums and YouTube reviews. Early signals: beta uptake from HA forum posts, app store ratings, community threads replacing "what companion app do you use?" with a single answer.

V2 success: a self-sustaining designer community where new templates appear without the core team's involvement.

## Roadmap Thinking

**V1 — The App**
Full HA companion replacement. One perfect theme. Context-aware dashboard engine with rule builder. Local-first connectivity. Demo-first onboarding. Widgets (context-aware). Wall tablet kiosk mode. App registers as HA device entity. iOS + Android via KMP + Compose Multiplatform.

*Explicitly out of V1:* Marketplace, Watch app, iPad split view, user-as-publisher flow.

**V2 — The Marketplace**
Open SDK published. Designer recruitment (5–10 templates at launch). Curated marketplace with editorial discovery (Staff Picks, Trending). Live preview trial using real entity data. Individual template purchases via Apple IAP / Google Play Billing. Designer-set pricing, app takes a platform cut. Template versioning with update notifications.

**V3 — The Platform**
Designer analytics. User-to-publisher flow. Apple Watch + WearOS. iPad adaptive layouts. Seasonal themes.

## Risk Register

| Risk | Severity | Mitigation |
|------|----------|------------|
| HA hires dedicated Android dev (July 2025) | Medium | Speed + design quality; they can't ship opinionated aesthetics |
| Template piracy (Compose code is distributable) | Low | Community norms + passion project tolerance |
| iOS CMP polish gaps | Medium | Timeline-managed; CMP stable since May 2025 |
| Designer chicken-egg at V2 launch | Medium | Pre-recruit 5–10 designers before launch |
| Apple IAP 30% cut | Low | Accepted; price templates to compensate |
| Open-source community resentment of paid marketplace | Medium | Open SDK resolves this; marketplace is optional premium, not a gate |
| Local connection failure UX | Low | Graceful fallback to Nabu Casa; stale state cached for glanceability |
| Template abandonment (designer stops maintaining) | Low | Version freeze policy; users keep last working version indefinitely |
| iOS CMP critical path | Medium | CMP stable since May 2025; iOS ships when polished, not on deadline |
