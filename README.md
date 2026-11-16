# QuestGiver

A Paper 26.3 quest plugin built around NPC quest-givers. A permanent town quest-giver offers a menu of quests,
rarer wandering quest-givers appear in the wild with one exclusive quest each, players can team up in squads,
quest lines level up instead of repeating, and a weekly d20 login reward keeps people coming back.
The full design is in [DESIGN.md](DESIGN.md).

## Requirements

| | |
| --- | --- |
| Server | Paper **26.3** (built against `io.papermc.paper:paper-api:26.3.build.253-stable`) |
| Java | **25** (what Paper 26.3 requires) |
| Required | [Vault](https://www.spigotmc.org/resources/vault.34315/) plus an economy plugin (EssentialsX) |
| Optional | [DecentHolograms](https://www.spigotmc.org/resources/decentholograms.96927/) for hologram leaderboards |

No Citizens and no NMS: player-model givers use the vanilla **mannequin** entity. Storage is SQLite
(`plugins/QuestGiver/data.db`) through the driver Paper already ships.

## Install

1. Drop `QuestGiver-<version>.jar` into `plugins/` (with Vault, EssentialsX and optionally DecentHolograms).
2. Start the server once. `config.yml`, `quests.yml`, `givers.yml` and `messages.yml` are created, each with comments.
3. Stand where the town quest-giver should be and run `/quest giver create`, then for example
   `/quest giver name &6&lQuest Master` and `/quest giver skin Notch`.
4. Optional: `/quest leaderboard create` where the leaderboard hologram should float.

`/quest reload` re-reads all four files without a restart and without touching player progress.

## Building

```bash
./gradlew build
```

The shaded jar is written to `build/libs/QuestGiver-<version>.jar`. Gradle downloads a Java 25 toolchain
automatically if one isn't installed. `./gradlew build` also runs the unit tests.

GitHub Actions (`.github/workflows/build.yml`) builds and tests every push and pull request and keeps the jar as
a workflow artifact. Pushing a version tag publishes a GitHub Release with the jar attached:

```bash
git tag v1.0.0
```

```bash
git push origin v1.0.0
```

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/quest` | `questgiver.quest.use` | Lists your active quests in chat |
| `/quest list` | `questgiver.quest.list` | Menu of your and your squad's quests (purple = in progress, green = ready) |
| `/quest track <quest>` | `questgiver.quest.track` | Picks the quest shown in the boss bar / sidebar |
| `/quest abandon <quest>` | `questgiver.quest.abandon` | Drops a quest (10-minute cooldown before it can be taken again) |
| `/quest top` | `questgiver.quest.top` | Leaderboard in chat |
| `/quest help` | - | Commands you can use |
| `/quest squad assemble <name>` | `questgiver.squad.use` | Creates a squad you lead |
| `/quest squad add <player>` | `questgiver.squad.use` | Invites a player (leader only) |
| `/quest squad accept [squad]` | `questgiver.squad.use` | Joins the squad that invited you |
| `/quest squad leave` | `questgiver.squad.use` | Leaves; a leaving leader is replaced by the next member |
| `/quest squad kick <player>` | `questgiver.squad.use` | Removes a member (leader only) |
| `/quest squad list` | `questgiver.squad.use` | Members and squad quests |
| `/quest squad disband` | `questgiver.squad.use` | Ends the squad (leader only) |
| `/quest giver create [id]` | `questgiver.admin.giver` | Places a new town quest-giver where you stand |
| `/quest giver name <name>` | `questgiver.admin.giver` | Renames it (colour codes allowed) |
| `/quest giver skin <player>` | `questgiver.admin.giver` | Copies a player's skin (looked up off the main thread) |
| `/quest giver type <player\|villager>` | `questgiver.admin.giver` | Mannequin (player model) or villager |
| `/quest giver move` | `questgiver.admin.giver` | Moves it to you |
| `/quest giver spawn` / `despawn` | `questgiver.admin.giver` | Spawns it at your position / removes the body but keeps the giver |
| `/quest giver remove` | `questgiver.admin.giver` | Deletes the giver (player progress is untouched) |
| `/quest giver select <id>` / `list` | `questgiver.admin.giver` | Picks which giver the commands above affect / lists givers |
| `/quest leaderboard create [id]` | `questgiver.admin.leaderboard` | Places a DecentHolograms leaderboard where you stand |
| `/quest leaderboard remove <id>` / `movehere <id>` / `list` | `questgiver.admin.leaderboard` | Manages leaderboards |
| `/quest event start <name>` / `stop <name>` / `list` | `questgiver.admin.event` | Seasonal events |
| `/quest wander spawn [here\|near\|random]` / `list` / `clear` | `questgiver.admin.wander` | Spawns or clears wandering givers (for testing) |
| `/quest reload` | `questgiver.admin.reload` | Reloads every file |
| `/quest admin reset <player> [quest]` | `questgiver.admin.reset` | Resets all progress, or one quest |
| `/quest admin complete <player> <quest>` | `questgiver.admin.complete` | Completes a quest and pays it (offline: on next login) |
| `/quest admin give <player> <quest>` | `questgiver.admin.give` | Gives a quest directly |
| `/quest admin progress <player>` | `questgiver.admin.progress` | Shows a player's quests and stats |
| `/quest admin stats` | `questgiver.admin.stats` | Money, XP and items paid today, this week and all-time, and the top-earning quests |

Admin commands work on offline players. Permission groups: `questgiver.quest.*` (everyone by default) and
`questgiver.admin.*` (ops by default). `questgiver.limit.<n>` (for example `questgiver.limit.8`) raises a player's
active-quest cap from the default 5; the highest one they have wins.

## How it plays

- **Giver menu** (right-click the giver):
  - Row 1: 5 quests on offer. At most 2 are squad-only. While an event runs, the last slot holds an event quest.
  - Row 2: quests ready to turn in (green).
  - Rows 3-4: quests in progress (purple). Click one to track it; shift-right-click to abandon it.
  - Bottom row: history, weekly reward, your stats, dragon egg bounty, close.
- **Quest types:** advancements (never offered if you already have it), kills, and fetch (items are taken;
  rare items ask first). Also exploring biomes, breeding, building a snow golem, curing a zombie villager, and
  "finish N event quests".
- **Levels and chains:** level N of a line unlocks after level N-1. Chains: We Need to Go Deeper → A Terrible
  Fortress → Into Fire / Blaze Breaker I, and The End? → Free the End → Remote Getaway / Sky's the Limit /
  Dragonslayers. Medium, Hard and Legendary unlock at 5, 15 and 30 completed quests.
- **Rewards:**
  - Money (Vault), XP levels added to your current level, items (enchantments above vanilla max are given as
    written), and optional console commands.
  - Each death during a quest docks its money and XP by 10%, up to 50%. Loot is never docked.
- **Fetch turn-ins refuse** items from creative mode, from item commands (`/give`, `/i`, ...) and from the
  plugin's own rewards.
- **Squads:**
  - The roster locks when the leader accepts a quest. Every member's kills count, and any member can hand in
    fetch items.
  - Everyone on the roster gets the full reward. Members who had already finished the quest get half the money
    and XP (loot in full), on top of their own death penalty.
  - Offline members are paid when they next log in.
- **Wandering givers:**
  - They spawn like wandering traders: an attempt every 20 minutes, 25% chance rising to 75%. They appear near a
    player or at a random spot.
  - Each offers one quest. The first player or squad to accept claims it and the wanderer leaves; unclaimed
    wanderers leave after an hour.
  - Once accepted there's a 1-hour real-time limit with a boss bar countdown, and it keeps running while you're
    offline. Kill quests must be finished in time (hand in whenever); fetch quests must be delivered in time.
    Hand in at the town giver.
- **Weekly reward:**
  - Claimable 7 days after your last claim: a base reward plus a d20 rolled in the title.
  - Results: 1 critical fail · 2-9 base · 10-15 bonus · 16-19 big bonus · 20 jackpot (fireworks and a
    server-wide announcement).
  - +1 to the roll per consecutive week, up to +5. A natural 1 still fails.
- **Dragon egg:**
  - Re-killed dragons leave a new egg on the exit portal.
  - The bounty pays $5,000 and 20 levels, once every 7 days per player, after a confirmation prompt.
- **Extras:**
  - Silk Touch on an iron, diamond or netherite pickaxe picks up spawners and keeps their mob type.
  - Creepers, zombies, skeletons and piglins killed by players drop their head 1 time in 10. Those heads count
    for the head quests.

## Unit tests

`./gradlew test` covers the pure rules: reward math, the death penalty and squad-repeat multiplier, the reward-cap
validator, d20 tiers, streaks and cooldowns, offer-slot rules (5 slots, max 2 squad, event slot), squad leader
promotion and roster locking, wandering spawn odds, night-kill resets, tier unlocks, event date windows, duration
formatting, and a catalog check of `quests.yml` against the design (counts, caps, levels, chains, squad flags).

## Manual test steps

Use a test server with Vault + EssentialsX (and DecentHolograms for the leaderboard steps), and two accounts (A, B)
plus a third for squad quests.

1. **Startup:** the console shows "Loaded 150 quests", no `[reward caps]` warnings, and "Hooked into economy".
2. **Giver:**
   1. Run `/quest giver create`, `/quest giver name &6&lQuest Master`, `/quest giver skin Notch`. The mannequin
      shows the skin.
   2. Hit it, push it, splash it with lava, and strike it with lightning (`/summon lightning_bolt`). It doesn't
      move or die.
   3. Restart the server: it's still there with its skin.
   4. `/kill @e[type=mannequin]`: it comes back within a few seconds.
   5. `/quest giver type villager`, then `type player`.
   6. `/quest giver despawn`, then `spawn`. `/quest giver remove`, then `create`: your progress is unchanged.
3. **Menu:**
   1. Right-click the giver: 5 offers, with no squad-only quests beyond 2.
   2. Accept Skeleton Hunter I: its slot refills, and it appears purple in rows 3-4 and in the boss bar.
   3. Kill 3 skeletons: the action bar counts and you get a "Quest complete" chat line. The pane turns green in
      row 2.
   4. Click it: you get money, 1 level on top of your current level, 16 arrows, sound and particles, and a
      history entry.
   5. Skeleton Hunter II now appears in the offers.
4. **Advancement quests:** a player who already has "Stone Age" never sees it offered. With "Stone Age" active,
   mining stone completes it.
5. **Fetch:**
   1. Take Harvest Season, gather 64 wheat and hand it in: the wheat is taken.
   2. Repeat with wheat from the creative inventory or `/give`: it's refused, and the tooltip says why.
   3. Rare quests (e.g. Heart of the Ocean) ask for confirmation first.
6. **Death penalty:** take a quest, die twice and finish it. You get 80% of the money and XP, the items in full,
   and a chat note.
7. **Cap:** accept 5 quests; the 6th is refused. Grant `questgiver.limit.7`: 7 are allowed.
8. **Abandon:** `/quest abandon skeleton_hunter_2` removes it, and it can't be taken again for 10 minutes.
9. **Squads:**
   1. A runs `/quest squad assemble Raiders` and `/quest squad add B`; B clicks the invite.
   2. A accepts a quest and chooses "Squad". B sees it in `/quest list`.
   3. B's kills count. B hands in; both are paid. A third player who joins afterwards isn't on that roster.
   4. Have B already finish a quest solo, then do it as a squad: B gets half the money and XP.
   5. A leaves: B becomes leader.
   6. Take a squad-only quest with 2 members: refused. With 3: allowed.
   7. With B offline at hand-in, B is paid on login.
   8. With `squad.range-check.enabled: true`, a far-away member gets nothing.
10. **Wanderers:**
    1. `/quest wander spawn here`, then right-click and accept: the wanderer vanishes and a countdown boss bar
       appears.
    2. Log out for 2 minutes and back in: the timer kept running.
    3. Set `wandering.quest-time-limit-minutes: 1`, reload and let a quest expire: it fails with a message.
    4. Finish a kill wanderer quest in time and hand it in at the town giver later: accepted.
    5. `/quest wander spawn random` with `announce: true` broadcasts rough coordinates.
    6. Unclaimed wanderers leave after `despawn-minutes`.
11. **Weekly:**
    1. Click the chest: the title rolls with clicks, then shows the result and pays out.
    2. Click again: "ready in 6d 23h".
    3. Log out mid-roll: the roll is paid on login.
    4. Set `weekly.cooldown-days: 0`, reload and claim repeatedly to see the streak bonus and a natural 1.
12. **Dragon:**
    1. Kill the dragon, then respawn it with end crystals and kill it again: a new egg appears on the portal.
    2. Hand the egg to the bounty: you get a confirmation, $5,000 and 20 levels, and then a 7-day cooldown.
    3. A creative-mode egg is refused.
13. **Leaderboard:**
    1. `/quest leaderboard create`: the hologram lists only players with at least 1 completion, and updates
       within a second of a turn-in.
    2. `movehere` and `remove` work, and the hologram reappears after a restart.
    3. Without DecentHolograms, `/quest leaderboard ...` says it isn't installed and `/quest top` still works.
14. **Events:**
    1. `/quest event start winterfest`: the last offer slot shows a Winterfest quest.
    2. Finish one: its loot has the "Winterfest 2026" lore.
    3. `/quest event stop winterfest`: unfinished Winterfest quests are removed, including for players who were
       offline (on their next login).
15. **Admin:**
    1. `/quest admin give A zombie_slayer_1`, `progress A`, `complete A zombie_slayer_1`, `reset A
       zombie_slayer_1`, `reset A`.
    2. Run `complete` for an offline player: they're paid on login.
    3. `/quest admin stats` shows today, this week and all-time totals, and the top quests.
16. **Extras:**
    1. Mine a spawner with a Silk Touch iron pickaxe: it drops with "Spawner: Zombie". Place it: it spawns
       zombies.
    2. A Silk Touch gold pickaxe, or no Silk Touch: no drop.
    3. Kill creepers until a head drops (10%); it counts for Headhunter: Creeper.
17. **Reload and restarts:**
    1. Change a message and run `/quest reload`: it updates.
    2. Restart mid-quest: progress, squads, timers, cooldowns and streaks are kept.
    3. Fill your inventory and claim a reward: the overflow drops at your feet.
