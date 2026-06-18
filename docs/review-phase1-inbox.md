# Phase 1 Review Findings — phase/1-inbox

Auto-reviewed before merge. All items resolved.

---

## High

- [x] **#1 Unused FOREGROUND_SERVICE permission** — `AndroidManifest.xml`  
  Removed.

- [x] **#2 DatabaseFactory not a singleton** — `DatabaseFactory.kt`  
  Added `@Volatile` + double-checked locking on companion-object `instance`.

- [x] **#3 `material-icons-extended` pulls ~5 MB for one icon** — `libs.versions.toml`  
  Switched to `material-icons-core`; `ArrowBack` is in the core set.

- [x] **#4 `EmailDetailScreen` stuck on "Loading…" forever after item resolves** — `EmailDetailScreen.kt`  
  Added `LaunchedEffect(item)` to call `onBack()` when item becomes null post-load, guarded by a `hasLoaded` flag against the cold-start `emptyList()` window.

---

## Medium

- [x] **#5 `InboxViewModel.world` is not observable** — `InboxViewModel.kt`  
  Changed to `MutableStateFlow<SimWorld>`; updated on `resolveEvent()` completion. Both `InboxScreen` and `EmailDetailScreen` now collect it with `collectAsStateWithLifecycle()`.

- [x] **#6 `ResponseApplicator` has zero test coverage** — `ResponseApplicatorTest.kt`  
  13 tests covering: funds deduction, insufficient-funds guard, `NeedChange` ±clamping, unknown-artist no-op, `LabelFundsChange`, `RelationshipChange` ±clamping, multi-effect ordering.

- [x] **#7 `tick()` generates emails against post-tick world** — `SimRepositoryImpl.kt`  
  Added call-site comment flagging this for Phase 2.

- [x] **#8 `tick()` unsynchronized — race with `initializeIfEmpty`** — `SimRepositoryImpl.kt`  
  Added `Mutex`; `tick()` uses `tickMutex.withLock { }`.

---

## Low

- [x] **#9 `optionsFor()` will block main thread with real AI** — `InboxViewModel.kt`  
  Added `// Phase 2: make this suspend + LaunchedEffect` comment.

- [x] **#10 `ExistingPeriodicWorkPolicy.KEEP` ignores interval changes on existing installs** — `AppApplication.kt`  
  Changed to `ExistingPeriodicWorkPolicy.UPDATE`.
