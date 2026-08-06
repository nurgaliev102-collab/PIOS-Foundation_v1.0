# Log Directory Policy — Sprint 1 Status

Short operational note, not an ADR. Documents a fact and states one hard
requirement for T-2. No implementation change made or proposed here.

## The question

Is `backend/logs/` (where T-1 actually writes) the long-term PIOS platform
logging standard, or a Sprint-1-scoped location that happens to work today?

## Answer: Sprint-1-scoped. Not yet the declared long-term standard.

### Why it landed there

`logging.file.name: ../logs/<module>.log`, in each of the five modules,
resolves relative to the process's own working directory. Under
`gradlew.bat :<module>:bootRun`, Gradle sets that directory to the module's
own subdirectory (`backend/<module>/`) by default, so `../logs/` lands at
`backend/logs/`, shared across all five. **That is a byproduct of Gradle's
own default, not a deliberate platform decision about where logs belong.**

### The concrete risk this creates for T-2

T-2 wraps these same five processes as Windows services via WinSW. WinSW's
XML sets each service's working directory explicitly — nothing guarantees it
defaults to `backend/<module>/` the way `bootRun` does. If T-2 sets a
different working directory (or leaves it as the service host's own
default), `../logs/<module>.log` silently resolves somewhere else. Not an
error — logs quietly appear in the wrong place, undetected until T-10
(errors endpoint) or a human goes looking and finds nothing.

This is not a T-1 defect: T-1's own Definition of Done never claimed to
survive the transition to Windows services, only that a log exists before
the `bootRun` console disappears (ADR-049 Decision 5). But it is a fact **T-2
must treat as a requirement, not inherit by accident.**

**Requirement added to T-2's own Definition of Done:** T-2's WinSW
configuration must set each of the five services' working directory to the
same module folder `bootRun` already uses (`backend/<module>/`), so the
existing relative path keeps resolving to `backend/logs/` without touching
any `application.yml` again. If T-2 needs a different working directory for
some other reason, the log path becomes T-2's problem to solve explicitly,
not something discovered later when T-10 can't find a file.

### The boundary question, named rather than defaulted

ADR-046 already decided `platform-ops` is a platform component, not part of
PIOS, placed at the repository root as a sibling of `backend/` and
`frontend/` specifically so it never reads as a sixth module. The five
modules' logs are consumed by exactly that kind of platform-level concern
(T-10's future errors endpoint), not by any module's own domain logic.
Nesting the shared log directory inside `backend/` sits slightly against
that same reasoning — a repository-root `logs/` (sibling to `backend/`,
`frontend/`, `platform-ops/`) would be the more consistent long-term home.

Not proposed now — it would mean touching the same five `application.yml`
files again, which is out of scope for this note. Named here so the choice
gets made deliberately later, not by nobody ever revisiting it.

## Recommendation

- **Sprint 1 / T-2:** `backend/logs/` stands, on the explicit condition that
  T-2's WinSW definitions preserve the working-directory invariant above.
- **Not decided:** whether `backend/logs/` is the permanent answer or
  migrates to a repository-root `logs/` once ADR-046's own boundary
  reasoning is applied to it directly. Recommend resolving this once,
  deliberately, before Sprint 2 — not by inertia.
