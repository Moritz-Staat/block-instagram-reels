# ADR-0002: DataStore instead of Room for persistence

- **Status:** Accepted
- **Date:** 2026-09-14
- **Supersedes:** —

## Context

The complete persistent state of this app is:

- master on/off
- per target: budget in minutes
- per target: consumed seconds, plus the budget day those seconds belong to
- rule cache: the cached `rules.json` body, its ETag and the last fetch timestamp

That is a handful of scalars plus one blob, written a few times a minute at most while a target is
active, and read once at service start plus whenever the UI is open. There are no queries, no
joins, no history, and no relations. Statistics and history charts are explicitly out of scope,
so no future feature is waiting behind a query engine.

## Decision

Use Jetpack DataStore. No Room, no SQLite, no schema, no migrations directory.

Preferences DataStore is sufficient and is the default choice; Proto DataStore is acceptable if the
per-target state ends up cleaner as a typed message. That choice belongs to the implementing issue
and does not need its own ADR.

The cached `rules.json` body is a **file** next to the DataStore, not a DataStore value — it is a
document, not a setting, and it is replaced wholesale.

## Consequences

**We get:**

- No schema, no migrations, no `Database.Callback`, no annotation processor beyond Hilt.
- Flow-based reads, which is what the UI wants anyway.
- Smaller APK, faster build, less to go wrong at 04:00 on a rollover.

**We give up:**

- Cheap history. If "how much did I watch last Tuesday" ever becomes a requirement, this needs
  revisiting with a new ADR — it would be a real rewrite of the storage layer, not an extension.
- Transactional multi-key updates. DataStore updates one preferences object atomically, which is
  enough here, but there is no cross-store transaction.

**We now have to live with:**

- Writes must be debounced. A naive write per tick would hammer the file during an active session;
  the target is one write every ~5 s while active, plus a final write when the target goes
  inactive. This is a correctness concern as much as a performance one, because a crash between
  writes loses the interval.
- The consumed-seconds value must always be stored **together with its budget day**, so a stale
  value from yesterday can never be mistaken for today's. See `DayBoundary`.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Room | Migrations, an annotation processor and a schema for four scalars. Pure overhead at this size, and it would only pay off for a history feature that is explicitly out of scope. |
| `SharedPreferences` | Synchronous disk I/O on the main thread by default and no Flow API. DataStore exists precisely to replace it. |
| Plain JSON file managed by hand | Would need its own locking and atomic-rename logic. DataStore already does that correctly. |
| Keep budget state in memory only | Fails R3 and the reboot requirement — a restart would hand back a full budget. |
