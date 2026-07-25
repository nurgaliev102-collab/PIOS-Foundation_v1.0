rootProject.name = "pios-backend"

// Each module below is its own independently buildable and deployable unit,
// consistent with MODULE_STRUCTURE.md and ADR-023/ADR-026. No shared module
// is declared here; each module depends on no other.
include("driver-management")
include("passenger-experience")
include("order-management")
include("dispatch")
include("network-management")
