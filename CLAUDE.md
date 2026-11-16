# Build brief: Quest-Giver plugin for Paper 26.3

You are building a Paper server plugin from an existing design plan. The plan is in `DESIGN.md` in this folder (exported from the "Quest-Giver Plugin — Design Plan" doc). **DESIGN.md is the source of truth**: every feature, command, number, and every quest in the catalog comes from it. If this brief and DESIGN.md disagree, follow DESIGN.md. If something in DESIGN.md is ambiguous, ask me before guessing.

The final deliverable is one shaded `.jar` that I drop into `plugins/` on a Paper 26.3 server.

## Before writing code

1. Read DESIGN.md completely.
2. Check the current facts instead of trusting memory, since Minecraft's versioning changed in 2026:
   - The exact Paper API artifact and version string for 26.3 (repo `https://repo.papermc.io/repository/maven-public/`).
   - The Java version Paper 26.3 requires. Set the Gradle toolchain to match.
   - The Paper API for the **Mannequin** entity (player-shaped NPC with a settable skin/profile, added in 1.21.9). It's used for player-model quest-givers.
   - The current advancement keys for every advancement quest in the catalog. Fix any mismatches and tell me.
   - Vault API coordinates, and DecentHolograms API coordinates and usage.
3. Propose a short plan (package layout plus milestone order) and wait for my OK.

## Tech stack

- Gradle (Kotlin DSL) with the Shadow plugin. `./gradlew build` produces the jar in `build/libs/`.
- Paper API only (`compileOnly`). No NMS and no Citizens.
- Dependencies:
  - **Vault:** required (`depend`). It's the economy bridge to EssentialsX.
  - **DecentHolograms:** soft dependency (`softdepend`). The leaderboard command says it isn't installed if it's missing.
- Storage: SQLite through the driver Paper bundles, or shade one if it doesn't. Do database work off the main thread and apply results back on it.
- Plugin name `QuestGiver`, root command `/quest`. Permissions follow `questgiver.<area>.<action>`, with admin commands under `questgiver.admin.*`.

## Architecture (suggested)

- `npc`: town and wandering quest-givers. Covers mannequin or villager models, skin lookup by player name, invulnerable/immovable/persistent setup, and the spawn/despawn/rename/reskin/move commands. Never store player data on the entity: re-binding a respawned giver must not reset anything.
- `quest`: quest definitions loaded from `quests.yml`, covering types (advancement, kill, fetch, plus multi-condition ones like "visit biomes" and "finish N event quests"), levels and chains, tiers, and the town, wandering and event pools.
- `progress`: per-player active quests, progress counters, history, cooldowns, weekly streaks and death counts. This is persistent.
- `squad`: assemble, add, accept, leave, kick, list and disband. Also the roster lock on accept, shared progress, and the optional range check at turn-in.
- `reward`: Vault money, XP levels added to the current level, items (including custom enchants above vanilla max), commands, the death penalty (−10% money/XP per death, max −50%, loot never docked), and reward-cap validation on config load (warn if any quest exceeds the dragon egg's $5,000 / 20 levels).
- `gui`: the quest-giver menu (5 offers with at most 2 squad quests and 1 event slot during events, a turn-in row, history, weekly reward, dragon egg bounty), the `/quest list` menu (purple panes for in progress, green for ready, with reward/giver/time-left tooltips), and the d20 roll animation.
- `wander`: wandering-giver spawn cycle that copies the wandering trader's (20-minute attempts, 25% chance rising to 75%), both spawn modes, 1-hour despawn, and the 1-hour real-time quest timer with a boss bar that keeps running while the player is offline.
- `weekly`: 7-day reward counted from each player's last claim, d20 tiers, streak bonus (+1 per week, max +5, a natural 1 still fails), and a login reminder.
- `dragon`: extra dragon egg drop on re-kills (configurable chance) and the per-player 7-day turn-in cooldown.
- `leaderboard`: DecentHolograms leaderboard (create/remove/movehere/list) showing completed quests and money earned from quests only. Hide players with 0 completed. Update live. Also `/quest top`.
- `event`: seasonal events with start/stop commands and optional dates, the Winterfest quest set, and event lore on loot.
- `admin`: reload, reset, complete, give, progress and stats (payouts by day, week and all-time).
- `extras`: Silk Touch spawner pickup (iron, diamond or netherite Silk Touch pickaxe only; keep the mob type on drop and on placement) and the 1-in-10 head drops for creepers, zombies, skeletons and piglins killed by players (configurable per mob).
- `itemtag`: PersistentDataContainer tags on items from creative mode, `/give` and quest rewards, so fetch turn-ins can refuse them. Watch creative inventory events and command item grants.

## Config files

`config.yml`, `quests.yml` (all 100 town, 8 head, 30 wandering and 12 Winterfest quests, transcribed exactly from DESIGN.md), `givers.yml`, and `messages.yml` (every player-facing string, with color codes, including NPC dialogue). Generate them with comments explaining each option. `/quest reload` must reload everything without a restart.

## Milestones (build and test after each)

1. Project skeleton, config loading, storage, and the `/quest` command framework.
2. Town quest-giver NPC and its admin commands.
3. Quest engine: advancement, kill and fetch types, levels, rewards and Vault payouts.
4. Menus: the giver menu and `/quest list`.
5. Squads.
6. Wandering quest-givers and timers.
7. Weekly reward and d20, then the dragon egg features.
8. Leaderboard, seasonal events, admin tools, spawner pickup and head drops.
9. Polish: messages, the reward-cap validator, edge cases, and a README.

## Quality bar

- No main-thread blocking: async for the database and skin lookups.
- Survives restarts and `/quest reload` with no lost progress.
- Handles players logging out mid-quest, squads where the leader leaves (promote the next member), and full inventories (drop overflow at the player's feet).
- Write unit tests for pure logic (reward math, death penalty, d20 tiers, streaks, menu slot rules, cooldowns). List manual test steps for everything else.
- When done, give me the jar path, a list of commands and permissions, and anything you changed from DESIGN.md and why.
