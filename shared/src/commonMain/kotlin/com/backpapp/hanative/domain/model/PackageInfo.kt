package com.backpapp.hanative.domain.model

/*
 * DOMAIN LAYER IMPORT RULES (NON-NEGOTIABLE)
 *
 * This package is pure Kotlin. The following imports are FORBIDDEN:
 *   - io.ktor.*
 *   - app.cash.sqldelight.*
 *   - android.*
 *   - androidx.*
 *   - platform.*  (use interfaces in domain/repository/ instead)
 *
 * Domain models: immutable data classes, all `val`, no platform deps.
 */
