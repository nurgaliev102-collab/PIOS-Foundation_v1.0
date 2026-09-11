rootProject.name = "pios-backend"

// Each module below is its own independently buildable and deployable unit,
// consistent with MODULE_STRUCTURE.md and ADR-023/ADR-026. No shared module
// is declared here; each module depends on no other.
include("driver-management")
include("passenger-experience")
include("order-management")
include("dispatch")
include("network-management")
include("identity")

// PIOS Core — Slice 01 (Participant History Projection). A new bounded
// context, ratified in ADR-067, added to the set exactly as MODULE_STRUCTURE.md
// §8 requires for a new module (a decision extending ADR-017/ADR-018). Its own
// independently buildable and deployable unit, its own PostgreSQL database
// (pios_core), depending on no other module — a read-only downstream consumer
// of events Taxi modules already publish.
include("core")
