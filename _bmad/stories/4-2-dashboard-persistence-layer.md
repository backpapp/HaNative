# Story 4.2: Dashboard Persistence Layer

Status: done

## Story

As a developer,
I want dashboard and card configuration persisted in SQLDelight and exposed via repository and use cases,
so that dashboard CRUD operations in subsequent stories have a complete data layer to call.

## Acceptance Criteria

1. `Dashboard.sq` defines `dashboard` table: `id TEXT PRIMARY KEY NOT NULL`, `name TEXT NOT NULL`, `position INTEGER NOT NULL`, `created_at INTEGER NOT NULL` — no custom type adapter required (`created_at` stored as epoch millis Long).
2. `DashboardCard.sq` defines `dashboard_card` table: `id TEXT PRIMARY KEY NOT NULL`, `dashboard_id TEXT NOT NULL` (FK → `dashboard.id`), `entity_id TEXT NOT NULL`, `position INTEGER NOT NULL`, `config TEXT NOT NULL`.
3. `DashboardRepositoryImpl` implements create, read, update, delete, and reorder operations via SQLDelight generated queries — all mutations return `Result<Unit>` and run on `Dispatchers.Default`.
4. `GetDashboardsUseCase` returns `Flow<List<Dashboard>>` where each `Dashboard` carries its `List<DashboardCard>` — the flow re-emits whenever dashboard or card table data changes (via SQLDelight `asFlow()` + `combine`).
5. `SaveDashboardUseCase` handles both create and update (upsert) for a `Dashboard`.
6. `DeleteDashboardUseCase` deletes a dashboard by ID; cards for that dashboard are deleted first in the same transaction.
7. `AddCardUseCase` inserts a `DashboardCard` into the `dashboard_card` table.
8. `RemoveCardUseCase` deletes a card by ID from `dashboard_card`.
9. `ReorderCardsUseCase` accepts `dashboardId: String, cardIds: List<String>` and updates each card's `position` to its index in the list — executed in a single SQLDelight transaction.
10. Dashboard config survives process death — verified by: write dashboard, kill app, relaunch, dashboard present (NFR11).
11. All use cases registered in `DomainModule`; `DashboardRepositoryImpl` registered in `DataModule` using `bind` syntax.
12. `DashboardRepository` interface lives in `domain/repository/`; `DashboardRepositoryImpl` in `data/repository/` — no SQLDelight imports above the data layer.

## Tasks / Subtasks

- [x] Task 1: Create `Dashboard.sq` SQLDelight schema (AC: 1)
  - [x] 1.1: Create `shared/src/commonMain/sqldelight/com/backpapp/hanative/Dashboard.sq`:
    ```sql
    CREATE TABLE IF NOT EXISTS dashboard (
        id TEXT PRIMARY KEY NOT NULL,
        name TEXT NOT NULL,
        position INTEGER NOT NULL,
        created_at INTEGER NOT NULL
    );

    insertOrReplaceDashboard:
    INSERT OR REPLACE INTO dashboard(id, name, position, created_at) VALUES (?, ?, ?, ?);

    updateDashboardName:
    UPDATE dashboard SET name = ? WHERE id = ?;

    selectAllDashboards:
    SELECT * FROM dashboard ORDER BY position ASC;

    selectDashboardById:
    SELECT * FROM dashboard WHERE id = ?;

    deleteDashboard:
    DELETE FROM dashboard WHERE id = ?;
    ```
  - [x] 1.2: No `AS` type annotations → no new adapter required → `DatabaseModule.kt` does NOT change. After Gradle sync, `HaNativeDatabase` gains a `dashboardQueries: DashboardQueries` property but its constructor signature is unchanged.

- [x] Task 2: Create `DashboardCard.sq` SQLDelight schema (AC: 2)
  - [x] 2.1: Create `shared/src/commonMain/sqldelight/com/backpapp/hanative/DashboardCard.sq`:
    ```sql
    CREATE TABLE IF NOT EXISTS dashboard_card (
        id TEXT PRIMARY KEY NOT NULL,
        dashboard_id TEXT NOT NULL,
        entity_id TEXT NOT NULL,
        position INTEGER NOT NULL,
        config TEXT NOT NULL
    );

    insertOrReplaceCard:
    INSERT OR REPLACE INTO dashboard_card(id, dashboard_id, entity_id, position, config) VALUES (?, ?, ?, ?, ?);

    selectCardsByDashboard:
    SELECT * FROM dashboard_card WHERE dashboard_id = ? ORDER BY position ASC;

    selectAllCards:
    SELECT * FROM dashboard_card ORDER BY position ASC;

    deleteCard:
    DELETE FROM dashboard_card WHERE id = ?;

    deleteCardsByDashboard:
    DELETE FROM dashboard_card WHERE dashboard_id = ?;

    updateCardPosition:
    UPDATE dashboard_card SET position = ? WHERE id = ?;
    ```
  - [x] 2.2: No FK enforcement pragma needed at schema level — cascade delete is handled manually in `DashboardRepositoryImpl.deleteDashboard()` (deleteCardsByDashboard then deleteDashboard in one transaction). Do NOT add `REFERENCES` constraint — SQLite doesn't enforce FKs by default and adding them without `PRAGMA foreign_keys = ON` creates misleading schema.

- [x] Task 3: Create `Dashboard.kt` and `DashboardCard.kt` domain models (AC: 12)
  - [x] 3.1: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/model/Dashboard.kt`:
    ```kotlin
    package com.backpapp.hanative.domain.model

    data class Dashboard(
        val id: String,
        val name: String,
        val position: Int,
        val createdAt: Long,
        val cards: List<DashboardCard>,
    )
    ```
  - [x] 3.2: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/model/DashboardCard.kt`:
    ```kotlin
    package com.backpapp.hanative.domain.model

    data class DashboardCard(
        val id: String,
        val dashboardId: String,
        val entityId: String,
        val position: Int,
        val config: String,
    )
    ```
  - [x] 3.3: Architecture rule — `val` only, no infrastructure imports. Both files are pure Kotlin.

- [x] Task 4: Create `DashboardRepository` interface in domain layer (AC: 12)
  - [x] 4.1: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/repository/DashboardRepository.kt`:
    ```kotlin
    package com.backpapp.hanative.domain.repository

    import com.backpapp.hanative.domain.model.Dashboard
    import com.backpapp.hanative.domain.model.DashboardCard
    import kotlinx.coroutines.flow.Flow

    interface DashboardRepository {
        fun getDashboards(): Flow<List<Dashboard>>
        suspend fun saveDashboard(dashboard: Dashboard): Result<Unit>
        suspend fun deleteDashboard(dashboardId: String): Result<Unit>
        suspend fun addCard(card: DashboardCard): Result<Unit>
        suspend fun removeCard(cardId: String): Result<Unit>
        suspend fun reorderCards(dashboardId: String, cardIds: List<String>): Result<Unit>
    }
    ```
  - [x] 4.2: No SQLDelight, Ktor, or Android imports. Pure Kotlin only.

- [x] Task 5: Create `DashboardRepositoryImpl` in data layer (AC: 3, 6, 9, 10)
  - [x] 5.1: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/data/repository/DashboardRepositoryImpl.kt`.
  - [x] 5.2: Class signature:
    ```kotlin
    class DashboardRepositoryImpl(
        private val database: HaNativeDatabase,
    ) : DashboardRepository
    ```
    No `CoroutineScope` needed — mutations are suspend, reactive flow is from SQLDelight.
  - [x] 5.3: `getDashboards()` — reactive combine of both table flows:
    ```kotlin
    override fun getDashboards(): Flow<List<Dashboard>> = combine(
        database.dashboardQueries.selectAllDashboards()
            .asFlow()
            .mapToList(Dispatchers.Default),
        database.dashboardCardQueries.selectAllCards()
            .asFlow()
            .mapToList(Dispatchers.Default),
    ) { dashboards, cards ->
        val cardsByDashboard = cards.groupBy { it.dashboard_id }
        dashboards.map { d ->
            Dashboard(
                id = d.id,
                name = d.name,
                position = d.position.toInt(),
                createdAt = d.created_at,
                cards = (cardsByDashboard[d.id] ?: emptyList()).map { c ->
                    DashboardCard(
                        id = c.id,
                        dashboardId = c.dashboard_id,
                        entityId = c.entity_id,
                        position = c.position.toInt(),
                        config = c.config,
                    )
                },
            )
        }
    }
    ```
    Imports: `app.cash.sqldelight.coroutines.asFlow`, `app.cash.sqldelight.coroutines.mapToList`.
  - [x] 5.4: `saveDashboard()` — upsert with Mutex + Default dispatcher:
    ```kotlin
    private val dbMutex = Mutex()

    override suspend fun saveDashboard(dashboard: Dashboard): Result<Unit> = runCatching {
        withContext(Dispatchers.Default) {
            dbMutex.withLock {
                database.dashboardQueries.insertOrReplaceDashboard(
                    id = dashboard.id,
                    name = dashboard.name,
                    position = dashboard.position.toLong(),
                    created_at = dashboard.createdAt,
                )
            }
        }
    }
    ```
  - [x] 5.5: `deleteDashboard()` — delete cards then dashboard in one transaction:
    ```kotlin
    override suspend fun deleteDashboard(dashboardId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.Default) {
            dbMutex.withLock {
                database.dashboardCardQueries.transaction {
                    database.dashboardCardQueries.deleteCardsByDashboard(dashboardId)
                    database.dashboardQueries.deleteDashboard(dashboardId)
                }
            }
        }
    }
    ```
    Note: `transaction {}` on either queries object covers both — SQLDelight uses the same underlying DB connection.
  - [x] 5.6: `addCard()`:
    ```kotlin
    override suspend fun addCard(card: DashboardCard): Result<Unit> = runCatching {
        withContext(Dispatchers.Default) {
            dbMutex.withLock {
                database.dashboardCardQueries.insertOrReplaceCard(
                    id = card.id,
                    dashboard_id = card.dashboardId,
                    entity_id = card.entityId,
                    position = card.position.toLong(),
                    config = card.config,
                )
            }
        }
    }
    ```
  - [x] 5.7: `removeCard()`:
    ```kotlin
    override suspend fun removeCard(cardId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.Default) {
            dbMutex.withLock {
                database.dashboardCardQueries.deleteCard(cardId)
            }
        }
    }
    ```
  - [x] 5.8: `reorderCards()` — batch position update in transaction:
    ```kotlin
    override suspend fun reorderCards(
        dashboardId: String,
        cardIds: List<String>,
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.Default) {
            dbMutex.withLock {
                database.dashboardCardQueries.transaction {
                    cardIds.forEachIndexed { index, cardId ->
                        database.dashboardCardQueries.updateCardPosition(
                            position = index.toLong(),
                            id = cardId,
                        )
                    }
                }
            }
        }
    }
    ```

- [x] Task 6: Create use cases (AC: 4–9)
  - [x] 6.1: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/GetDashboardsUseCase.kt`:
    ```kotlin
    package com.backpapp.hanative.domain.usecase

    import com.backpapp.hanative.domain.model.Dashboard
    import com.backpapp.hanative.domain.repository.DashboardRepository
    import kotlinx.coroutines.flow.Flow

    class GetDashboardsUseCase(private val repository: DashboardRepository) {
        operator fun invoke(): Flow<List<Dashboard>> = repository.getDashboards()
    }
    ```
  - [x] 6.2: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/SaveDashboardUseCase.kt`:
    ```kotlin
    package com.backpapp.hanative.domain.usecase

    import com.backpapp.hanative.domain.model.Dashboard
    import com.backpapp.hanative.domain.repository.DashboardRepository

    class SaveDashboardUseCase(private val repository: DashboardRepository) {
        suspend operator fun invoke(dashboard: Dashboard): Result<Unit> =
            repository.saveDashboard(dashboard)
    }
    ```
  - [x] 6.3: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/DeleteDashboardUseCase.kt`:
    ```kotlin
    package com.backpapp.hanative.domain.usecase

    import com.backpapp.hanative.domain.repository.DashboardRepository

    class DeleteDashboardUseCase(private val repository: DashboardRepository) {
        suspend operator fun invoke(dashboardId: String): Result<Unit> =
            repository.deleteDashboard(dashboardId)
    }
    ```
  - [x] 6.4: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/AddCardUseCase.kt`:
    ```kotlin
    package com.backpapp.hanative.domain.usecase

    import com.backpapp.hanative.domain.model.DashboardCard
    import com.backpapp.hanative.domain.repository.DashboardRepository

    class AddCardUseCase(private val repository: DashboardRepository) {
        suspend operator fun invoke(card: DashboardCard): Result<Unit> =
            repository.addCard(card)
    }
    ```
  - [x] 6.5: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/RemoveCardUseCase.kt`:
    ```kotlin
    package com.backpapp.hanative.domain.usecase

    import com.backpapp.hanative.domain.repository.DashboardRepository

    class RemoveCardUseCase(private val repository: DashboardRepository) {
        suspend operator fun invoke(cardId: String): Result<Unit> =
            repository.removeCard(cardId)
    }
    ```
  - [x] 6.6: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/ReorderCardsUseCase.kt`:
    ```kotlin
    package com.backpapp.hanative.domain.usecase

    import com.backpapp.hanative.domain.repository.DashboardRepository

    class ReorderCardsUseCase(private val repository: DashboardRepository) {
        suspend operator fun invoke(dashboardId: String, cardIds: List<String>): Result<Unit> =
            repository.reorderCards(dashboardId, cardIds)
    }
    ```

- [x] Task 7: Register in DI modules (AC: 11)
  - [x] 7.1: Add `DashboardRepositoryImpl` to `serverManagerModule()` in `DataModule.kt` using `bind` syntax (same pattern used for `EntityRepository`):
    ```kotlin
    single { DashboardRepositoryImpl(get()) } bind DashboardRepository::class
    ```
    Add imports: `com.backpapp.hanative.data.repository.DashboardRepositoryImpl`, `com.backpapp.hanative.domain.repository.DashboardRepository`, `org.koin.core.module.dsl.bind`.
  - [x] 7.2: Add all 6 use case `factory` bindings to `DomainModule.kt`:
    ```kotlin
    factory { GetDashboardsUseCase(get()) }
    factory { SaveDashboardUseCase(get()) }
    factory { DeleteDashboardUseCase(get()) }
    factory { AddCardUseCase(get()) }
    factory { RemoveCardUseCase(get()) }
    factory { ReorderCardsUseCase(get()) }
    ```
    Add imports for all 6 use case classes.

- [x] Task 8: Verify build and write tests (AC: 10)
  - [x] 8.1: Run `./gradlew :shared:generateCommonMainHaNativeDatabaseInterface` — confirm `dashboardQueries` and `dashboardCardQueries` appear on `HaNativeDatabase`. No adapter errors expected.
  - [x] 8.2: Run `./gradlew :shared:compileKotlinAndroid` — full compile.
  - [x] 8.3: Run `./gradlew :shared:testAndroidHostTest` — existing tests must pass.
  - [x] 8.4: Write `shared/src/commonTest/kotlin/com/backpapp/hanative/domain/usecase/GetDashboardsUseCaseTest.kt` using `FakeDashboardRepository` — verify flow emits correctly.
  - [x] 8.5: Write `shared/src/commonTest/kotlin/com/backpapp/hanative/domain/usecase/DashboardUseCaseTest.kt` — verify `SaveDashboardUseCase`, `DeleteDashboardUseCase`, `AddCardUseCase`, `RemoveCardUseCase`, `ReorderCardsUseCase` delegate to repository and return `Result<Unit>`.
  - [x] 8.6: Confirm no SQLDelight or Ktor imports in `domain/` — `grep -r "sqldelight\|ktor" shared/src/commonMain/kotlin/com/backpapp/hanative/domain/` must return empty.

## Dev Notes

### Architecture Compliance

- **NEVER hand-author `HaNativeDatabase.kt`** — SQLDelight-generated. Adding `Dashboard.sq` and `DashboardCard.sq` causes codegen to add `dashboardQueries` and `dashboardCardQueries` properties. Constructor signature is unchanged (no new adapters — no `AS` annotations in these schemas). [Source: architecture.md#Enforcement]
- **No SQLDelight or Ktor imports in `domain/`** — `DashboardRepository` is pure Kotlin. `DashboardRepositoryImpl` in `data/repository/` may import both.
- **`val` only in domain models** — `Dashboard.kt` and `DashboardCard.kt` use all `val`. [Source: architecture.md#Enforcement]
- **`DatabaseModule.kt` does NOT need changes** — `Dashboard.sq` and `DashboardCard.sq` have no `AS` type annotations so no adapter parameters are added to `HaNativeDatabase`. The existing `entity_stateAdapter` wiring is unchanged.
- **`bind` syntax for Koin** — use `single { DashboardRepositoryImpl(get()) } bind DashboardRepository::class` not the double-binding anti-pattern. [Source: Story 4.1 review patch]
- **Mutex + `withContext(Dispatchers.Default)` for all DB writes** — reuse the same pattern patched in Story 4.1. `DashboardRepositoryImpl` needs its own `private val dbMutex = Mutex()` — do not share the `EntityRepositoryImpl`'s mutex. [Source: Story 4.1 review patches]
- **`runCatching { }` wrapping all suspend mutations** — DB exceptions become `Result.failure` instead of propagating. [Source: Story 4.1 review patches]

### SQLDelight 2.3.2 Reactive Queries

`sqldelight-coroutines` (`app.cash.sqldelight:coroutines-extensions`) is already in `libs.versions.toml` — confirmed in Story 4.1 Task 1.3. Use:
```kotlin
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
```
`.asFlow()` on a SQLDelight query returns a `Flow` that re-emits the full result set whenever any write to that table occurs. `mapToList(Dispatchers.Default)` maps the cursor to `List<T>` on the Default dispatcher — correct pattern for non-blocking reads.

### Generated Type Names

SQLDelight maps `snake_case` table names to `PascalCase` with underscores preserved for compound names:
- `dashboard` → `Dashboard` (query class: `DashboardQueries`, accessed as `database.dashboardQueries`)
- `dashboard_card` → `Dashboard_card` (query class: `DashboardCardQueries`, accessed as `database.dashboardCardQueries`)

Column name mappings in generated cursor: `dashboard_id`, `entity_id`, `created_at` etc. match the SQL column names exactly.

### `deleteDashboard` Transaction Note

SQLDelight `transaction {}` can be called on any `*Queries` object — they all share the same underlying `SqlDriver`. Calling `database.dashboardCardQueries.transaction { ... }` and writing to both `dashboardCardQueries` and `dashboardQueries` inside is valid and atomic.

### ID Generation

`DashboardRepositoryImpl` does not generate IDs — the domain model carries `id` as a String. Callers (use case or ViewModel) are responsible for generating unique IDs (e.g., `kotlin.random.Random.nextLong().toString()` or UUID equivalent). This story does not create a ViewModel; ID generation will be addressed in Story 4.6/4.7 where the dashboard management UI is built.

### `config TEXT` Field

`DashboardCard.config` is a JSON string for future card-level overrides (display name, custom icon, etc.). For MVP (Stories 4.3–4.7), callers pass `"{}"` as the default. Do not add a JSON parsing step in this story — treat it as an opaque String.

### No `CoroutineScope` Injection

Unlike `EntityRepositoryImpl`, `DashboardRepositoryImpl` does NOT need an injected `CoroutineScope`. There is no background polling or event collection — all reads are reactive via SQLDelight `asFlow()`, and all writes are suspend functions called by use cases from the ViewModel's scope. [Source: architecture.md#Data Architecture]

### Project Structure — New Files

```
shared/src/commonMain/sqldelight/com/backpapp/hanative/
  ├── EntityState.sq                                   ← existing
  ├── Dashboard.sq                                     ← NEW
  └── DashboardCard.sq                                 ← NEW

shared/src/commonMain/kotlin/com/backpapp/hanative/
  ├── domain/
  │   ├── model/
  │   │   ├── Dashboard.kt                             ← NEW
  │   │   └── DashboardCard.kt                         ← NEW
  │   ├── repository/
  │   │   └── DashboardRepository.kt                   ← NEW
  │   └── usecase/
  │       ├── GetDashboardsUseCase.kt                  ← NEW
  │       ├── SaveDashboardUseCase.kt                  ← NEW
  │       ├── DeleteDashboardUseCase.kt                ← NEW
  │       ├── AddCardUseCase.kt                        ← NEW
  │       ├── RemoveCardUseCase.kt                     ← NEW
  │       └── ReorderCardsUseCase.kt                   ← NEW
  └── data/
      └── repository/
          └── DashboardRepositoryImpl.kt               ← NEW
```

Modified files:
- `shared/src/commonMain/kotlin/com/backpapp/hanative/di/DataModule.kt` — add `DashboardRepositoryImpl` binding
- `shared/src/commonMain/kotlin/com/backpapp/hanative/di/DomainModule.kt` — add 6 use case factories

**Do NOT modify:**
- Any `DatabaseModule.kt` (commonMain/androidMain/iosMain) — no new adapters
- `EntityRepositoryImpl.kt` — no changes
- `EntityState.sq` — no changes
- `HaNativeDatabase.kt` — never hand-authored, SQLDelight-generated

### Previous Story Intelligence (Story 4.1)

- **Story 4.1 review patches are complete** — `EntityRepositoryImpl` now uses `SupervisorJob` scope, `Mutex`, `withContext(Dispatchers.Default)`, `runCatching`, `bind` Koin syntax, `distinctUntilChanged()`. Apply all of these proactively in this story — do not repeat the same review findings.
- **`entity_state` has `last_changed` column** — the D1 decision from Story 4.1 review added `last_changed INTEGER AS kotlinx.datetime.Instant NOT NULL` to `EntityState.sq`. This is now canonical; `toDomain()` maps it to `HaEntity.lastChanged`. Mentioned for context only — do not touch.
- **`data/repository/.gitkeep` still present** — `DashboardRepositoryImpl.kt` goes alongside `EntityRepositoryImpl.kt` in the same directory. Both `.gitkeep` files in `data/repository/` and `data/local/adapter/` are inert — leave them.
- **`serverManagerModule()` is where `EntityRepositoryImpl` is registered** — add `DashboardRepositoryImpl` there too, not in a separate module.
- **`CoroutineScope` already single-registered** — `single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Main) }` in `serverManagerModule()`. Do NOT register a second scope.

### Testing Standards

- Test framework: `kotlin.test` — never JUnit4/5 directly in `commonTest`
- Test task: `./gradlew :shared:testAndroidHostTest`
- Fake pattern: create `FakeDashboardRepository : DashboardRepository` in `commonTest` — not a mock. Return fake flows and `Result.success(Unit)` for mutations.
- `Flow` testing without turbine: `repository.getDashboards().first()` in `runTest {}` is sufficient for snapshot assertions. No need for turbine — it's not in `libs.versions.toml`.
- `assertEquals(expected, actual)` — not bare `assert(x == y)` (avoids opaque failure messages).
- Security rule: test fakes must not log credential values (not relevant here, but pattern applies).

### Git Intelligence

| Commit | What it established |
|--------|-------------------|
| `0adc61c` | Story 4.1 review patches: runtime safety, AC6/AC7, `last_changed` schema, `Mutex`, `withContext(Dispatchers.Default)`, `bind` syntax, `SupervisorJob` scope |
| `5afaf78` | Link sqlite3 in iosApp Xcode target |
| `3db55f0` | Link sqlite3 for iOS `NativeSqliteDriver` |
| `99f4ea3` | Story 4.1: SQLDelight plugin, `EntityState.sq`, `EntityRepositoryImpl`, use cases |
| `f91dbcd` | Story 3.5: `SessionRepository`, `OAuthCallbackBus`, `AuthViewModel`, `StartupViewModel` |

Relevant confirmed codebase state:
- `data/repository/` contains `EntityRepositoryImpl.kt` + `.gitkeep` — ready for `DashboardRepositoryImpl.kt`
- `DomainModule.kt` has `ObserveEntityStateUseCase` and `CallServiceUseCase` — add dashboard use cases alongside
- `sqldelight-coroutines` dep already in version catalog — `asFlow()` available without adding new deps
- SQLDelight version bumped to `2.3.2` in `libs.versions.toml` (from `2.0.2`) during Story 4.1 build fix

### References

- [Source: `_bmad/outputs/architecture.md#Data Architecture`] — SQLDelight table list: `entity_state`, `dashboard`, `dashboard_card`, `context_rule`
- [Source: `_bmad/outputs/architecture.md#Enforcement`] — Non-negotiable agent rules
- [Source: `_bmad/outputs/architecture.md#Complete Project Tree`] — `Dashboard.kt`, `DashboardCard.kt`, `DashboardRepository.kt`, `DashboardRepositoryImpl.kt` locations
- [Source: `_bmad/outputs/epics.md#Story 4.2`] — Full acceptance criteria
- [Source: `_bmad/stories/4-1-sqldelight-schema-entity-state-pipeline.md#Dev Notes`] — Review patches reference for patterns to carry forward
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/di/DataModule.kt`] — `serverManagerModule()` is where DashboardRepositoryImpl binds
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/di/DomainModule.kt`] — Existing use case factory pattern
- [Source: `gradle/libs.versions.toml`] — `sqldelight = "2.3.2"`, `sqldelight-coroutines` dep confirmed present

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Completion Notes List

- Created `Dashboard.sq` and `DashboardCard.sq` — SQLDelight codegen confirmed `dashboardQueries` and `dashboardCardQueries` on `HaNativeDatabase`. No adapter changes required.
- `DashboardRepositoryImpl` uses `Mutex` + `withContext(Dispatchers.Default)` + `runCatching` on all mutations (Story 4.1 patterns carried forward). No `CoroutineScope` injection needed.
- `getDashboards()` uses `asFlow()` + `combine` for reactive dual-table flow; re-emits on any write to either table.
- `deleteDashboard()` uses `transaction {}` on `dashboardCardQueries` to atomically delete cards then dashboard.
- `reorderCards()` updates each card's position to its list index in a single transaction.
- DI: two-line pattern `single { DashboardRepositoryImpl(get()) }` + `single<DashboardRepository> { get<DashboardRepositoryImpl>() }` — `bind` infix syntax not available in this Koin version.
- Domain layer purity verified: `grep -r "sqldelight\|ktor" domain/` returns only comments in `PackageInfo.kt` files.
- All tests pass: `./gradlew :shared:testAndroidHostTest` BUILD SUCCESSFUL.

### File List

- `shared/src/commonMain/sqldelight/com/backpapp/hanative/Dashboard.sq`
- `shared/src/commonMain/sqldelight/com/backpapp/hanative/DashboardCard.sq`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/model/Dashboard.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/model/DashboardCard.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/repository/DashboardRepository.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/data/repository/DashboardRepositoryImpl.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/GetDashboardsUseCase.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/SaveDashboardUseCase.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/DeleteDashboardUseCase.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/AddCardUseCase.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/RemoveCardUseCase.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/ReorderCardsUseCase.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/di/DataModule.kt` (modified)
- `shared/src/commonMain/kotlin/com/backpapp/hanative/di/DomainModule.kt` (modified)
- `shared/src/commonTest/kotlin/com/backpapp/hanative/domain/usecase/GetDashboardsUseCaseTest.kt`
- `shared/src/commonTest/kotlin/com/backpapp/hanative/domain/usecase/DashboardUseCaseTest.kt`

## Senior Developer Review (AI)

_To be completed after implementation._

### Review Findings

_Adversarial review (Blind Hunter + Edge Case Hunter + Acceptance Auditor) on uncommitted Story 4.2 diff, 2026-04-30._

**Decision needed:**

- [x] [Review][Decision] FK + cascade on `dashboard_card.dashboard_id` — RESOLVED 1b: added `FOREIGN KEY (dashboard_id) REFERENCES dashboard(id) ON DELETE CASCADE` in DashboardCard.sq; both DatabaseModule actuals execute `PRAGMA foreign_keys=ON` post-driver-construction.
- [x] [Review][Decision] `reorderCards` semantics for empty / duplicate / missing / cross-dashboard cardIds — RESOLVED 2a: validate-and-fail-fast via `require()` (non-empty list, no duplicates, set equals existing dashboard cards).
- [x] [Review][Decision] `saveDashboard` contract w.r.t. `Dashboard.cards` — RESOLVED 3c (deferred): save persists dashboard row only; cards managed via add/remove/reorder use cases. Document in repo KDoc as follow-up.
- [x] [Review][Decision] `created_at` preservation on update — RESOLVED 4a: split `insertDashboard` / `updateDashboard` SQL; repo selects existing row in transaction and dispatches insert vs update accordingly. `created_at` no longer overwritten on update.
- [x] [Review][Decision] `removeCard` position compaction — RESOLVED 5a: removeCard now compacts remaining cards in same transaction (looks up parent dashboard via new `selectCardById`, deletes, re-sequences siblings via `updateCardPositionInDashboard`).
- [x] [Review][Decision] AC11 — Koin `bind` infix syntax — RESOLVED 6a: Koin 4.0 `org.koin.dsl.bind` import works. `DataModule.kt:55-56` refactored to `single { EntityRepositoryImpl(get(), get(), get()) } bind EntityRepository::class` and `single { DashboardRepositoryImpl(get()) } bind DashboardRepository::class`. EntityRepository registration also fixed (was same anti-pattern). Build green on common, iOS sim arm64, AndroidHostTest.

**Patch (unambiguous fix):**

- [x] [Review][Patch] Per-dashboard card sort in `getDashboards()` Flow — APPLIED. `cardsByDashboard[d.id]` now `.sortedBy { it.position }` before mapping to domain.
- [x] [Review][Patch] Add index on `dashboard_card.dashboard_id` — APPLIED. `CREATE INDEX IF NOT EXISTS dashboard_card_dashboard_id ON dashboard_card(dashboard_id);` in DashboardCard.sq.
- [x] [Review][Patch] Validate non-blank IDs at repo boundary — APPLIED. `require(id.isNotBlank())` on saveDashboard, deleteDashboard, addCard, removeCard, reorderCards.
- [x] [Review][Patch] `reorderCards` scope `UPDATE` by `dashboardId` — APPLIED. New SQL `updateCardPositionInDashboard(position, id, dashboard_id)` replaces unscoped `updateCardPosition`.
- [x] [Review][Patch] `runCatching` preserves `CancellationException` — APPLIED. Custom inline `runCatchingCancellable` rethrows `CancellationException`, wraps other throwables in `Result.failure`.

**Deferred (real but out of scope or pre-existing decisions):**

- [x] [Review][Defer] `combine` exposes transient inconsistent state between `dashboard` and `card` flows [DashboardRepositoryImpl.kt:28-54] — deferred, architectural; requires single-source query.
- [x] [Review][Defer] `combine` re-emits on every card change globally; no `distinctUntilChanged` [DashboardRepositoryImpl.kt:28-54] — deferred, optimization.
- [x] [Review][Defer] `position` numeric edge cases (negative, Long.MAX truncation via `.toInt()`) [DashboardRepositoryImpl.kt:41,48] — deferred, defensive only.
- [x] [Review][Defer] `addCard` / `saveDashboard` lack position invariant; duplicate `position` allowed [DashboardRepositoryImpl.kt:56-67,80-92] — deferred, related to compaction decision.
- [x] [Review][Defer] No `UNIQUE(dashboard_id, position)` constraint [DashboardCard.sq] — deferred, would conflict with mid-reorder transient states.
- [x] [Review][Defer] `Dispatchers.Default` vs `Dispatchers.IO` for SQLite [DashboardRepositoryImpl.kt:31,34,57,...] — deferred, spec-conformant (Dev Note prescribes Default).
- [x] [Review][Defer] `config` column stored as raw `TEXT` with no JSON validation/adapter [DashboardCard.sq:5] — deferred, persistence-only scope.
- [x] [Review][Defer] No tests for repo, SQL, or `Result.failure` paths; only use-case delegation tested [DashboardUseCaseTest.kt, GetDashboardsUseCaseTest.kt] — deferred, manual verification per AC10.
- [x] [Review][Defer] `GetDashboardsUseCase` Flow has no `catch` operator [GetDashboardsUseCase.kt:8] — deferred, surface in ViewModel.
- [x] [Review][Defer] Use cases pass through with no input validation [AddCardUseCase.kt etc.] — deferred, caller responsibility.
- [x] [Review][Defer] Test names use backticks-with-spaces; KMP Native portability risk [DashboardUseCaseTest.kt, GetDashboardsUseCaseTest.kt] — deferred, currently runs on JVM only.
- [x] [Review][Defer] `dbMutex` redundant for SQLite write serialization and absent from read path [DashboardRepositoryImpl.kt:26,...] — deferred, no functional bug.

**Dismissed:** AC10 manual process-death verification (spec-allowed); `FakeDashboardRepository` named-class vs anonymous-object stylistic deviation.
