Status: Read-only environment audit, 2026-09-02. **No files were installed, uninstalled, updated, or modified to produce this report.** Every finding below is backed by a specific command or file inspected in this task; where evidence was inconclusive, the finding is marked UNKNOWN rather than assumed.

# 1. Current Claude Code environment

| | |
|---|---|
| Claude Code CLI version | **2.1.247** (`claude --version`) |
| CLI binary location | `C:\Users\Admin\.local\bin\claude` (`which claude`) |
| Model (this session) | `sonnet` (per `~/.claude/settings.json`) |
| Effort level | `high` |
| Platform | Windows 10 Pro, PowerShell primary + Git Bash available |

**AVAILABLE.**

# 2. Plugins

The user-level marketplace `claude-plugins-official` (github.com/anthropics/claude-plugins-official) is registered and cloned locally at `C:\Users\Admin\.claude\plugins\marketplaces\claude-plugins-official`, last updated 2026-09-02T13:58:34Z — its `plugins/` subdirectory contains **40+ plugin source trees**, including `frontend-design`, `claude-code-setup`, `code-review`, `playwright` (under `external_plugins/`), `feature-dev`, `hookify`, and many language-server plugins.

**Critically: running `claude plugin list` returns exactly `"No plugins installed. Use `claude plugin install` to install a plugin."`** — this is authoritative, not inferred. Having a plugin's source present in a registered marketplace clone does **not** mean it is installed or enabled; no plugin from this or any other marketplace is actually active in this environment.

| Plugin | Source in marketplace? | Actually installed/enabled? | Available in PIOS project? |
|---|---|---|---|
| (all 40+, including `frontend-design`, `claude-code-setup`, `playwright`) | Yes | **NOT AVAILABLE** — `claude plugin list` confirms zero installed | **NOT AVAILABLE** |

**NOT AVAILABLE** (confirmed by direct command output, not assumption).

# 3. Skills

Skills observed to be actually invokable in this session, by category:

- **Official Anthropic (bundled) skills**: confirmed **AVAILABLE** by direct use earlier in this session — `run` and `artifact-design` were both invoked and their instructions loaded (their base directory was `C:\Temp\claude\bundled-skills\<version>\...`, a session-scoped extraction path that no longer exists on disk at audit time, so a full bundled-skill inventory could not be re-enumerated read-only in this task — their *existence and invocability* is confirmed by this session's own prior use, but a complete list is **UNKNOWN**).
- **Project skills**: exactly one — `.claude/skills/graphify/SKILL.md` (confirmed via `Glob`), wired into `CLAUDE.md` ("any input to knowledge graph. Trigger: `/graphify`") and enforced session-wide via a `PreToolUse` hook (Section 9). **AVAILABLE.**
- **User/local skills** (outside the project, under `~/.claude/skills` or similar): no such directory was found under `~/.claude` in this task's listing (Section 6's directory dump shows no top-level `skills/` folder there). **NOT AVAILABLE** (or none configured).
- **Third-party skills**: none found; the only external source registered is the official Anthropic marketplace itself, and nothing from it is installed (Section 2). **NOT AVAILABLE.**

# 4. frontend-design

The `frontend-design` plugin's **source exists** in the official marketplace clone (`.../plugins/frontend-design`), but per Section 2's direct `claude plugin list` evidence, **no plugin is installed in this environment**. Its presence in documentation or in the marketplace repository must not be mistaken for it being usable here.

**NOT AVAILABLE.**

# 5. claude-code-setup

Same situation as Section 4 — source present in the marketplace clone, zero plugins actually installed.

**NOT AVAILABLE.**

# 6. CLAUDE.md hierarchy

| Location | Exists? | Affects this project? |
|---|---|---|
| `c:\Projects\PIOS-Foundation_v1.0\CLAUDE.md` (project root) | Yes | **Yes** — read at session start, defines PIOS's documentation-first/architecture-first mission |
| `c:\Projects\PIOS-Foundation_v1.0\.claude\CLAUDE.md` (project `.claude/`) | Yes | **Yes** — defines role separation (ChatGPT=architect, Claude=implementation engineer), the graphify trigger, and the 3-stage Architect/Developer/QA workflow |
| `c:\Projects\CLAUDE.md` (parent directory) | **No** — not found | N/A |
| `c:\CLAUDE.md` (drive root) | **No** — not found | N/A |
| `C:\Users\Admin\.claude\CLAUDE.md` (user-level) | **No** — not found | N/A |
| `C:\Users\Admin\CLAUDE.md` | **No** — not found | N/A |

Only the two project-level files exist and affect this project; no parent-directory or user-level `CLAUDE.md` exists anywhere on this machine.

**AVAILABLE** (2 files, both confirmed, both in effect).

# 7. `.claude/` configuration

Project `.claude/` directory contents (`c:\Projects\PIOS-Foundation_v1.0\.claude\`):

| Item | Purpose |
|---|---|
| `CLAUDE.md` | Project memory (Section 6) |
| `settings.json` | Tracked, shared permission allow/deny lists (git-tracked — see `.claude/settings.json.graphify-bak`, an untracked backup copy) + the graphify `PreToolUse` hooks (Section 9) |
| `settings.local.json` | A large, machine-local, untracked accumulation of previously-approved individual tool-call permissions (1000+ lines) — a history of prior session approvals, not a design/config surface |
| `agents/` | 5 project-defined subagent specs: `architect.md`, `developer.md`, `evidence-analyst.md`, `qa-reviewer.md`, `release-manager.md` (Section 10) |
| `AGENTS_WORKFLOW.md` | Documents the Architect → Developer → QA Reviewer workflow referenced by `.claude/CLAUDE.md` |
| `skills/graphify/` | The one project skill (Section 3) |
| `worktrees/` | Leftover directory from a prior session's `git worktree` use (a deployment task, per this session's own history) |

**AVAILABLE**, fully inspected.

# 8. MCP

- **Project-scoped** (`~/.claude.json` → `projects["c:/Projects/PIOS-Foundation_v1.0"]` and its case-variant key): `mcpServers: {}`, `enabledMcpjsonServers: []` — **zero MCP servers configured for this project**, confirmed for all 3 path-casing variants found in the config.
- **User-global** (`~/.claude.json` top-level `mcpServers`): also empty (`[]`).
- **No `.mcp.json`** file exists anywhere in the repository.
- **However**, this session's own tool surface has surfaced an MCP-style connector — "claude.ai Google Drive" — requiring authentication, per an earlier system reminder in this conversation. This does not come from any file found in this audit; it is most plausibly an **account-level connector managed by the claude.ai backend/harness**, exposed to this Claude Code session independently of local project/user JSON config, not something installed or configurable from within this repository.

**NOT AVAILABLE** at the project/local-config level; **UNKNOWN provenance, present but unauthenticated** at the account/harness level (Google Drive specifically).

# 9. Hooks

Exactly one hook family is configured, in the **project's own tracked** `.claude/settings.json`:

```json
"hooks": {
  "PreToolUse": [
    { "matcher": "Bash|Grep",  "hooks": [{ "type": "command", "command": "C:/Users/Admin/.local/bin/graphify.EXE hook-guard search" }] },
    { "matcher": "Read|Glob",  "hooks": [{ "type": "command", "command": "C:/Users/Admin/.local/bin/graphify.EXE hook-guard read" }] }
  ]
}
```

**Purpose**: before any `Bash`/`Grep` or `Read`/`Glob` call, `graphify.EXE hook-guard` injects a reminder that `graphify-out/graph.json` exists and should be queried first for code orientation (visible throughout this very session as the repeated "MANDATORY: graphify-out/graph.json exists..." system reminders). It is advisory context injection, not a blocking gate — every raw file read/grep in this session proceeded regardless, with the reminder attached.

**AVAILABLE**, confirmed by direct config inspection and by its own repeated, observable effect throughout this session.

# 10. Subagents

Two sources, both confirmed:

**Project-defined** (`.claude/agents/*.md`, 5 files):
- `architect` — architecture review, ADR authorship; blocks implementation crossing bounded-context boundaries
- `developer` — implements approved, scoped MVP work
- `evidence-analyst` — maintains the Evidence Log, checks Sprint claims against recorded observations
- `qa-reviewer` — independent end-to-end verification after `developer` reports completion
- `release-manager` — commit/tag readiness after QA sign-off

**Built into this Claude Code environment** (not from project files — general-purpose agent types offered by the harness itself): `claude`, `claude-code-guide`, `Explore`, `general-purpose`, `Plan`, `statusline-setup`.

All 11 were listed as available "Available agent types for the Agent tool" earlier in this session — that listing is the harness's own authoritative enumeration, not something this audit had to re-derive.

**AVAILABLE.**

# 11. Browser / visual verification

- **No browser-automation or screenshot tool is present in this session's own tool list** — the available tools are `Bash`, `PowerShell`, `Read`, `Write`, `Edit`, `Glob`, `Grep`, `Agent`, `Artifact`, `AskUserQuestion`, `ScheduleWakeup`, `Skill`, `ToolSearch`, `ReportFindings`, plus deferred tools (`CronCreate/Delete/List`, `DesignSync`, `EnterPlanMode`/`ExitPlanMode`, `EnterWorktree`/`ExitWorktree`, `Monitor`, `NotebookEdit`, `PushNotification`, `RemoteTrigger`, `SendMessage`, `SendUserFile`, `TaskOutput`, `TaskStop`, `WebFetch`, `WebSearch`). None of these render a page, drive a browser, or capture a screenshot.
- The `playwright` plugin exists as source in the official marketplace (Section 2) but is **not installed** (confirmed: zero plugins installed).
- A Python-installed `playwright.exe` binary is present on `PATH` (`C:\Users\Admin\AppData\Local\Programs\Python\Python312\Scripts\playwright.exe`) — this is a general-purpose system tool found via `where`, **not** a capability wired into this Claude Code session's own tool surface; using it would require manually shelling out via `Bash`/`PowerShell`, with no image/screenshot return path back into this conversation (no tool here accepts or displays a resulting screenshot except `Read` on an image file, which *could* work if a screenshot were produced and saved to disk by some external script, but no such pipeline exists today).
- The PIOS frontend itself has no Playwright dependency (`frontend/node_modules` has no `playwright*` entry; `package.json`'s `devDependencies` list confirms this — Section 13).
- `curl`/`Invoke-WebRequest` **can** reach the local running frontend (e.g. `http://localhost:5173`, `http://localhost:4173`, and the deployed `https://home-pc.tail385153.ts.net/`) for raw HTTP status/body inspection — proven repeatedly earlier in this session's own permission history (`.claude/settings.local.json`). This gives **HTTP-level** reachability, not **rendered/visual** verification.

| Capability | Status |
|---|---|
| Launch/access the local PIOS application (HTTP-level) | **AVAILABLE** (curl/Invoke-WebRequest, already used repeatedly) |
| Inspect rendered pages (DOM/visual) | **NOT AVAILABLE** |
| Browser/Playwright driven from within this session | **NOT AVAILABLE** (plugin not installed; no MCP browser server; no in-session tool) |
| Capture screenshots | **NOT AVAILABLE** |
| Visual/pixel-level regression verification | **NOT AVAILABLE** |

# 12. Existing PIOS design system

Searched the full repository for `DESIGN.md`, `tailwind.config.*`, `*.stories.*` (Storybook), theme/token files, and any component-library dependency:

- **No `DESIGN.md`** anywhere in the repository.
- **No Tailwind config**, no Tailwind dependency in `frontend/package.json`.
- **No Storybook** (`*.stories.*`) files.
- **No component-library dependency** (no `shadcn`, `MUI`, `Chakra`, `Ant Design`, etc. in `package.json`).
- **No shared theme/design-token file** (no `theme.ts`, `tokens.css`, `colors.ts`, or similar, anywhere under `frontend/src`).
- **Styling architecture found**: CSS Modules — 20 `*.module.css` files, one per component/page, plus a single global `frontend/src/index.css`. Each page/component owns its own scoped stylesheet; there is no shared, cross-cutting design-token or theme layer today.
- **No visual regression test suite** — `frontend/package.json`'s only test tooling is Vitest + Testing Library (component/logic tests), confirmed to have no visual/screenshot assertions.

**NOT AVAILABLE** — there is no existing design system, token set, or component library to build on; any PIOS design system introduced would start from zero shared infrastructure.

# 13. PIOS frontend architecture

| | |
|---|---|
| Framework | React 19.2.7 + TypeScript 6.0.2, built with Vite 8.1.1 |
| Routing | `react-router-dom` 7.18.1, a flat `RouteObject[]` table (`frontend/src/app/routes.tsx`) |
| Entry point | `frontend/index.html` → Vite → React app; route table above is the single source of truth for pages |
| Main routes | `/` (DriverHome), `/i/:driverCode` (PassengerLanding), `/i/:driverCode/request` (RideRequest), `/coordinator` (Coordinator), `/network-test` (NetworkTest), `/help/install` (InstallHelp, deliberately unauthenticated), `/owner` (OwnerControlCenter, self-gated login), `*` (NotFound) |
| Styling architecture | CSS Modules, one `.module.css` per component/page (Section 12) — no shared design-token layer |
| Component architecture | `frontend/src/components/` (reusable: `ActionButton`, `DriverCard`, `Header`, `OnboardingWalkthrough`, `PasswordInput`, `QRCard`, `Spinner`) + `frontend/src/pages/` (route-level, page-specific) + `frontend/src/features/install/` (a feature-scoped module) |
| Testing | Vitest 4.1.10 + `@testing-library/react` — component/unit tests, `*.test.tsx`/`*.test.ts` alongside source |
| Linting | `oxlint` (fast Rust-based ESLint alternative), config at `frontend/.oxlintrc.json` |
| PWA | `vite-plugin-pwa` is a dependency — the app is installable (matches the "PIOS Install v1" feature referenced in `routes.tsx`'s own comments) |
| Dev/start commands | `npm run dev` (Vite dev server, default port 5173), `npm run build` (`tsc -b && vite build`), `npm run preview` (serves the production build, historically observed on port 4173), `npm run lint` (oxlint), `npm run test` (`vitest run`) |
| Production | Runs as the `pios-frontend` WinSW service (confirmed running throughout Tasks 1–5B of this session) |

**AVAILABLE**, fully confirmed from `package.json`, `routes.tsx`, and directory structure.

# 14. Risks

Specific to introducing a controlled PIOS design system, given everything found above:

1. **No visual verification loop exists in this environment.** Any design-system work (new tokens, component restyling) cannot be screenshot-verified or visually diffed from within this Claude Code session — every change would need to be verified by the human, or by a much narrower proxy (HTTP status checks, DOM snapshot assertions in Vitest/Testing Library, which check structure/text, not appearance).
2. **No existing token/theme layer to extend** — introducing tokens means creating the first one, not integrating with something established; every one of the 20 existing `.module.css` files hardcodes its own colors/spacing today (confirmed pattern from this session's own history: a `grep -rhoE "#[0-9a-fA-F]{3,8}" --include="*.module.css"` was run earlier in this project, implying hardcoded hex colors scattered per-file).
3. **`frontend-design` and `claude-code-setup` are not installed** — any workflow assuming their presence (e.g., a design-review slash command, or setup automation) will silently fail or not exist; this must be planned around, not assumed.
4. **CLAUDE.md's own governance model is a hard constraint**: `.claude/CLAUDE.md` states architecture decisions belong to "the project architect (ChatGPT)" and that "Claude must not redesign architecture proactively" — introducing a design *system* (not just a single page's styling) is architecture-adjacent and should go through that same Architect → Developer → QA workflow, not be done unilaterally.
5. **CSS Modules + no bundler-level CSS framework** means a design system here is realistically hand-rolled CSS custom properties (`:root` variables) rather than a Tailwind/CSS-in-JS adoption — introducing either of the latter would be a build-tooling change requiring its own justification, not a drop-in.
6. **The one project skill (`graphify`) and its `PreToolUse` hooks** add friction/noise to every file read during design work (a reminder on every `Read`/`Grep`), though this is advisory only and does not block any operation.

# 15. Recommended next step

**Before any design-system code is written, produce a short, Product-Owner/Architect-approved design brief** (palette, typography, spacing scale, and where the resulting CSS custom properties live) as a lightweight architecture note — consistent with `.claude/CLAUDE.md`'s own requirement that Claude "must not redesign architecture proactively" — since this repository currently has zero shared design infrastructure to extend and no visual-verification tooling to check the result against, so getting the direction agreed in writing first is the only reliable checkpoint available in this environment.
