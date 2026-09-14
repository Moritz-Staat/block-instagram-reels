# Architecture Decision Records

Short records of decisions that are expensive to reverse. One file per decision, numbered,
never deleted — a decision that stops being true gets superseded by a new ADR, not edited away.

## When to write one

Write an ADR **before** you change something this repository already decided. Concretely: if a
statement in [ARCHITECTURE.md](../ARCHITECTURE.md), in the issue you are working on, or in an
existing ADR is in your way, the ADR is the deliverable — not a quiet change in the code.

You do **not** need one for: picking a library version, naming, file layout inside a package, or
anything you could reverse in an afternoon.

## Records

| # | Title | Status |
|---|---|---|
| [0001](0001-accessibility-service-approach.md) | Detect short-video surfaces via an AccessibilityService | Accepted |
| [0002](0002-datastore-over-room.md) | DataStore instead of Room for persistence | Accepted |
| [0003](0003-no-foreground-service.md) | No foreground service alongside the AccessibilityService | Accepted |

## Process

1. Copy [`template.md`](template.md) to `NNNN-short-slug.md`, `NNNN` being the next free number.
2. Fill it in. Keep it under a page — if it needs more, the decision is probably several.
3. Add a row to the table above.
4. Commit it in the same PR as the change it justifies.

Status is one of `Proposed`, `Accepted`, `Superseded by ADR-NNNN` or `Rejected`. Rejected ADRs are
kept: knowing what was considered and thrown out is most of the value.
