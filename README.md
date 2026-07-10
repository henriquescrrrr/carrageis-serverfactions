# ServerFactions

A modern faction system for **Paper 1.21+** with custom roles, GUI-driven management, and a public API for other plugins to hook into. Inspired by Clash Royale clans and Fortnite parties — simple to use but deep.

## Features

- **Create & Dissolve Factions** — name + tag via Sign GUI, public/private toggle
- **Custom Roles** — configurable permissions, hierarchy-based (Leader → Officer → Member → Recruit)
- **GUI-Driven** — all interactions through inventory GUIs; text input via Sign editor (never chat)
- **Invite System** — invite players, auto-expiring invites, accept/decline via GUI
- **Faction Chat** — toggle chat that sends messages only to faction members
- **Friendly Fire Protection** — always-on damage prevention between faction members
- **Public API** — `ServerFactionsApi` registered in `ServicesManager` for other plugins
- **Custom Events** — `FactionCreateEvent`, `FactionDisbandEvent`, `FactionMemberJoinEvent`, `FactionMemberLeaveEvent`, `FactionLeaderChangeEvent`
- **Economy Integration** — MultiBank (primary) + Vault (fallback), 100% async
- **MultiBank Hook** — auto-creates faction group bank accounts
- **PlaceholderAPI** — 8 placeholders for scoreboards, tab, chat
- **Multi-Language** — per-player via PlayerSettings API; ships with `en_US`, `pt_PT`, `fr_FR`
- **SQLite Persistence** — in-memory cache for fast reads, async writes
- **Brigadier Commands** — `/fac`, `/f`, `/faction` with full tab completion

## Requirements

| Dependency | Required | Purpose |
|---|---|---|
| Paper 1.21+ | ✅ | Server platform |
| Java 21+ | ✅ | Runtime |
| MultiBank | ❌ | Primary economy + faction bank accounts |
| Vault | ❌ | Fallback economy |
| PlayerSettings | ❌ | Per-player language |
| PlaceholderAPI | ❌ | Placeholders |

## Commands

| Command | Description | Permission |
|---|---|---|
| `/fac` | Open the faction menu | `serverfactions.use` |
| `/fac create` | Start faction creation flow (Sign GUIs) | `serverfactions.use` |
| `/fac invite <player>` | Quick-invite a player | `serverfactions.use` |
| `/fac chat` | Toggle faction chat | `serverfactions.use` |
| `/fac info [faction]` | View faction info | `serverfactions.use` |
| `/fac list` | Browse public factions | `serverfactions.use` |
| `/fac invites` | View pending invites | `serverfactions.use` |
| `/fac leave` | Leave your faction | `serverfactions.use` |
| `/fac admin disband <faction>` | Force-disband a faction | `serverfactions.admin` |
| `/fac admin reload` | Reload config & lang files | `serverfactions.admin` |

**Aliases:** `/f`, `/faction`

## Permissions

| Permission | Description | Default |
|---|---|---|
| `serverfactions.use` | Base faction commands | `true` |
| `serverfactions.admin` | Admin commands (disband, reload) | `op` |

## PlaceholderAPI Placeholders

| Placeholder | Description |
|---|---|
| `%factions_tag%` | Raw tag (e.g., `TIW`) |
| `%factions_tag_formatted%` | Formatted with colors (e.g., `<gray>[<red>TIW<gray>]`) |
| `%factions_name%` | Faction name |
| `%factions_role%` | Player's role name |
| `%factions_leader%` | Faction leader's name |
| `%factions_members%` | Member count |
| `%factions_is_public%` | `true` / `false` |
| `%factions_color%` | Primary MiniMessage color |

## API Usage

Other plugins can access the API via Bukkit's `ServicesManager`:

```java
RegisteredServiceProvider<ServerFactionsApi> rsp =
    Bukkit.getServicesManager().getRegistration(ServerFactionsApi.class);
if (rsp != null) {
    ServerFactionsApi api = rsp.getProvider();
    boolean sameFaction = api.areInSameFaction(player1, player2);
    String factionName = api.getFactionName(api.getFactionId(player));
}
```

### API Methods

```java
boolean areInSameFaction(UUID player1, UUID player2);
String getFactionId(UUID player);
String getFactionName(String factionId);
String getFactionTag(String factionId);
UUID getFactionLeader(String factionId);
List<UUID> getFactionMembers(String factionId);
boolean isInFaction(UUID player);
String getFactionBankAccountId(String factionId);
void setFactionBankAccountId(String factionId, String accountId);
```

### Custom Events

```java
// Listen for faction creation
@EventHandler
public void onFactionCreate(FactionCreateEvent event) {
    String factionId = event.getFactionId();
    UUID leader = event.getLeaderUuid();
}
```

Events: `FactionCreateEvent`, `FactionDisbandEvent`, `FactionMemberJoinEvent`, `FactionMemberLeaveEvent`, `FactionLeaderChangeEvent`

## Role System

Roles use a **priority hierarchy** — lower number = higher rank:

| Priority | Default Role | Permissions |
|---|---|---|
| 0 | **Leader** | All (immutable) |
| 1 | Officer | invite, kick, promote, demote, bank |
| 2 | Member | invite |
| 3 | Recruit | none |

Leaders can create custom roles with any combination of permissions:
`canInvite`, `canKick`, `canEditSettings`, `canManageRoles`, `canPromote`, `canDemote`, `canAccessBank`

Members can only kick/promote/demote players with a **higher priority number** (lower rank).

## Configuration

### config.yml

```yaml
language: en_US          # Global fallback language
faction:
  creation-cost: 100.0   # Economy cost to create a faction (0 = free)
  max-tag-length: 4
  max-name-length: 24
  min-name-length: 3
  invite-expiry-hours: 24
  absolute-max-members: 50
  friendly-fire: true    # Prevent damage between faction members
economy:
  priority: ["MultiBank", "Vault"]  # Provider priority order
```

### Language Files

Located in `plugins/ServerFactions/lang/`. Ships with:
- `en_US.yml` — English
- `pt_PT.yml` — Portuguese
- `fr_FR.yml` — French

All messages use **MiniMessage** format with full color/click/hover support.

## Building

```bash
./gradlew jar
```

Output: `build/libs/ServerFactions-1.0.0.jar`

## Project Structure

```
pt.henrique.serverfactions
├── ServerFactions.java          # Main plugin class
├── api/                         # Public API interface + events
├── command/                     # Brigadier command registration
├── config/                      # ConfigManager + LangManager
├── economy/                     # Async economy (MultiBank/Vault)
├── gui/                         # All GUI screens
├── hook/                        # Optional plugin hooks (MultiBank, PAPI, PlayerSettings)
├── listener/                    # Chat, connection, friendly fire listeners
├── manager/                     # FactionManager (core logic + cache)
├── model/                       # Data models (Faction, Member, Role, Invite)
├── storage/                     # SQLite DatabaseManager
└── util/                        # SignInputUtil
```

## License

All rights reserved. © Henrique

