# Sprint 1 Retrospective

Written for process improvement, not as a historical record — see `SPRINT_001_REPORT.md` for what was actually done.

## Что было сделано

Tasks 2–5 executed in full; Task 1 correctly stopped at the Implementation Review before any code was touched, once a real conflict with `PERSISTENCE_ARCHITECTURE.md`/`PROJECT_CONSTITUTION.md` was found. Stage B produced `ADR-036` as a `Proposed` document instead of code, exactly as redirected. Documentation was synchronized to match already-ratified decisions the code had implemented but the domain docs hadn't caught up to. Full test suite run and every failure individually root-caused, not assumed.

## Что заняло больше времени, чем ожидалось

- **Root-causing test failures took longer than running the tests themselves.** Both the `dispatch` and (newly, this sprint) `driver-management` failures required re-running the same test class in isolation to distinguish "real defect" from "cross-test interference under full-suite execution" — a second full Gradle invocation per suspicious failure, not a one-line log read. This is inherent to the shared, non-reset PostgreSQL databases these integration tests use, not a one-off cost.
- **The Implementation Review itself** (checking Task 1 against `PROJECT_CONSTITUTION.md`/`PERSISTENCE_ARCHITECTURE.md` before writing anything) took real, deliberate reading time — re-reading the exact wording of `PERSISTENCE_ARCHITECTURE.md` §7–8 and `PROJECT_CONSTITUTION.md` §6–7 rather than relying on a remembered summary. This is the one cost that directly paid for itself: it is the reason Task 1 didn't get implemented against an unauthorized architecture change.
- **Deciding how much of Task 3's documentation sync was safe to write** took more judgment than expected. The line between "record an already-ratified fact" (safe) and "invent a Domain Decision no one made" (not safe) was not always obvious at first glance — for example, the Proposal *name* itself required tracing back to whether a real Ubiquitous Language Validation step legitimized it, versus the *invariant*, which was more clearly traceable to an already-ratified Product Decision (Concurrent Proposal Policy).

## Какие пробелы в документации обнаружены

- `DOMAIN_MODEL.md` and `EVENT_CATALOG.md` lagged the Proposal aggregate by several sprints' worth of implementation — now closed for this sprint, but the lag itself is a symptom worth naming: nothing in the current process forces a documentation-sync check when a domain concept graduates from "implemented" to "stable and widely referenced."
- `docs/README.md`'s own ADR index was already missing 8 real ADRs (027–034) and never caught it — an index that isn't verified against the directory it indexes will drift silently.
- `.claude/CLAUDE.md` contains a substantive governance conflict (an unratified "ChatGPT is the architect" claim) that had apparently been governing at least part of this project's actual working process without ever being reconciled against `PROJECT_CONSTITUTION.md`.
- `PERSISTENCE_ARCHITECTURE.md` is silent, not opposed, on cross-aggregate transactions within one domain — a genuine, load-bearing silence that blocked real work this sprint, not a hypothetical gap.

## Что стоит изменить в процессе перед Sprint 2

1. **Keep the Implementation Review as a mandatory first step**, not a one-off request — it caught a real, otherwise-easy-to-miss governance violation before any code was written. Make it standard for every sprint that touches architecture, not just this one.
2. **Add a lightweight "index verification" check** whenever a new `docs/ADR/*.md` or `docs/PRODUCT_DECISION_*.md` file is added — a one-line grep confirming `docs/README.md` actually links it would have caught the ADR-027–034 gap years earlier than this sprint did by accident.
3. **Treat "documentation lag behind stable code" as its own trackable category of technical debt**, not something only discovered incidentally while doing something else — the Proposal aggregate's naming lag was known informally for several sprints before this one actually closed it.
4. **Resolve the `.claude/CLAUDE.md` conflict before it causes a second, independent instruction stream to diverge further from `PROJECT_CONSTITUTION.md`** — the longer two "entry point" files coexist with different claims about decision authority, the more expensive reconciling them becomes.
5. **For cross-test interference specifically**: stop diagnosing it one failure at a time, per module, per sprint. It has now shown up in two modules with the identical signature (isolated pass, full-suite fail, shared non-reset database). One dedicated investigation in Sprint 2 will be cheaper than continuing to pay the isolation-rerun cost piecemeal every time it resurfaces.
