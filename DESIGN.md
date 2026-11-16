# Quest-Giver Plugin — Design Plan

Nov 15, 2026 · @Ryan

Status: design only, no code yet. Target: Paper 26.3. Dependencies: EssentialsX + Vault (economy).

## Overview

A quest system built around NPC quest-givers. A permanent town quest-giver stands in one spot and offers a menu of quests; rarer wandering quest-givers spawn in the wild like wandering traders, each with one exclusive quest. Quests pay out items, XP and EssentialsX money. Players can team up in squads, quests level up instead of repeating, and a weekly login reward with a d20 roll keeps people coming back.

## Town quest-giver (NPC)

- Stays in one admin-chosen spot; can't be hurt, pushed or killed; survives restarts.
- Player model (vanilla mannequin entity, no Citizens needed) or villager model.
- Admin commands: `/quest giver create`, `/quest giver name <name>` (color codes allowed), `/quest giver skin <player>` (e.g. Notch), `/quest giver type <player|villager>`, `/quest giver move`, `/quest giver remove`.
- Built to support multiple givers later, each with its own quest pool (e.g. a hunter and a miner).
- Personality: custom dialogue lines for offering, accepting, turning in and coming back empty-handed, plus sounds and particles on completion.

## Quest menu

Right-clicking the quest-giver opens a chest-style menu:

- Top row: 5 available quests to pick from, with descriptions, rewards and difficulty. Accepting one fills its slot with a new quest.
- Your active quests and their progress; turn in from here.
- The weekly reward button (see Weekly reward).
- The dragon egg bounty, with its cooldown countdown when on cooldown.
- Second row: quests ready to turn in (green panes); click to claim rewards.
- Quest history button: opens a page of quests you've finished with this quest-giver, with the date and rewards earned.
- At most 2 squad-required quests can be in the 5 available slots at once, so solo quests are always on offer.

## Quest types

- **Advancement:** earn a vanilla advancement (e.g. "We Need to Go Deeper"). Never offered to players who already have it. One level only.
- **Kill:** kill X of a mob ("kill 20 skeletons", "kill a guardian").
- **Fetch:** bring an item; it is taken on turn-in. Rare items ask for confirmation first.
- **Easy future additions:** mine blocks, fish, breed animals, travel to a location.

## Leveling (no repeats)

- Quests are never repeated; instead a quest line levels up: Skeleton Hunter I (3 kills) → II (10) → III (25), and so on.
- Each level has its own, larger rewards. Finishing a level unlocks the next in the menu; the final level is completed permanently.
- Difficulty tiers (Easy / Medium / Hard) unlock as players complete more quests.
- Quest chains: finishing one quest can unlock a different follow-up (Enter the Nether → Find a Fortress → Kill a Blaze).

## Rewards

- Per quest in config: items (custom enchants, names, lore — e.g. a Protection V gold chestplate), XP levels, money via Vault/EssentialsX, and optional console commands.
- Fetch quests use rarity tiers (Common / Rare / Legendary) with default payouts, overridable per item. Legendary items like the dragon egg pay far more.
- Reputation points with the quest-giver; a `/quest top` leaderboard.
- Death penalty: each death while a quest is active docks that quest's money and XP rewards by 10%, up to a maximum of 50%. Loot rewards are never docked. In squads, each member's deaths only dock their own payout.
- Reputation and leaderboards are purely cosmetic; they unlock nothing.

## Hologram leaderboard (DecentHolograms)

- `/quest leaderboard create` (admin) instantly places a DecentHolograms leaderboard where the admin is standing. Also `remove`, `movehere` and `list`.
- Each line: rank, player name, quests completed, and total money earned from quests only (tracked by the plugin, not `/bal`).
- Only players with at least 1 completed quest appear. Sorted by quests completed; ties broken by quest earnings.
- Updates live when a player completes a quest. Top 10 by default (configurable); title and line format configurable.
- Squad quests count for every member who was rewarded.
- Same data backs `/quest top` in chat. DecentHolograms is a soft dependency: without it, the command just says it isn't installed.

## Active quests and tracking

- Players can hold multiple quests at once, capped (default 5), with higher caps by permission rank.
- Progress tracker: boss bar or sidebar ("Skeletons: 12/20"); `/quest track <quest>` picks which one shows.
- `/quest` lists active quests; `/quest abandon <quest>` drops one, with an optional cooldown.

### /quest list menu

`/quest list` opens an in-game chest menu of the player's quests (and their squad's):

- **Purple stained glass pane:** ongoing quest, with current progress.
- **Green stained glass pane:** completed, ready to turn in.
- Each pane's tooltip shows the reward you'll get, who gave you the quest (town giver's name or the wandering giver's), and the time left if it's time-limited.
- Clicking a pane could set it as the tracked quest in the boss bar.

## Wandering quest-givers

- Spawn like wandering traders: an attempt about every 20 minutes, starting at 25% and rising 25% per failure up to 75%. All numbers configurable.
- Two spawn modes, both on by default: near a player ("they come to you") and at random spots in the world to stumble upon.
- Random names and skins from a config pool (traveling knight, hooded stranger…).
- Each offers exactly one quest, drawn from an exclusive pool with better rewards than the town giver.
- The first player or squad to accept claims it; the NPC then despawns.
- 1-hour real-time limit to finish once accepted, shown as a boss bar countdown (real time rather than in-game days, since game time pauses and skips). If nobody accepts, the NPC leaves after about an hour.
- Turn-in happens at the town quest-giver.

## Squads

- Commands: `/quest squad assemble <name>`, `add <player>`, `accept`, `leave`, `kick <player>`, `list`, `disband`.
- The player who assembles the squad is its leader; only the leader adds, kicks, disbands or accepts quests for the squad.
- Shared progress: every member's kills count; any member can turn in fetch items.
- Everyone gets the full XP, loot and money.
- Exploit guards: the roster locks when a quest is accepted (only members at that moment are rewarded), and an optional range check at turn-in (e.g. within 100 blocks or same world).
- Works for town and wandering quests. Optional squad-only quests needing 3+ members (kill a Warden, raid a Woodland Mansion).

## Dragon egg bounty

- A permanent fetch quest at the town quest-giver.
- Per-player cooldown: each player can turn in one egg every 7 days.
- The plugin makes re-killed Ender Dragons drop a new egg (vanilla only gives one on the first kill). Drop chance configurable.
- Legendary-tier payout, with a confirmation prompt before the egg is taken.

## Weekly reward and d20 roll

- "Collect your weekly rewards" button in the quest-giver menu, claimable once every 7 days counted from each player's last claim.
- Base reward (configurable): $300, a few diamonds or emeralds, and some XP.
- Roll for initiative: a d20 with each face at 5%, animated in the title text with clicking sounds.
  - 1 — critical fail: base reward plus a joke line from the NPC.
  - 2–9 — base reward.
  - 10–15 — base plus extra money and emeralds.
  - 16–19 — big bonus such as diamonds and an enchanted book.
  - 20 — jackpot: rare item, fireworks and a server-wide announcement.
- Streak bonus: +1 to the roll per consecutive week claimed, capped at +5; missing a week resets it. A natural 1 still fails.
- Login reminder in chat when a weekly reward is waiting.
- All ranges and rewards configurable.

## Quest catalog

100 town quests and 30 wandering quests. Each level of a quest line counts as one quest. Rewards are money / XP levels (added to the player's current level) / loot.

**Reward caps:** no quest pays more than the dragon egg bounty ($5,000 + 20 levels). No loot is worth more than a few diamond blocks (top end: a Fortune III or Mending book or equivalent). Tiers: Easy $100–300, 1–2 levels · Medium $300–800, 3–5 · Hard $800–2,000, 5–10 · Legendary $2,000–3,500, 10–15 · Dragon egg is the single top payout.

### Town quests (100)

**Advancement quests (33)**

| # | Quest | Objective | Tier | Rewards |
| --- | --- | --- | --- | --- |
| 1 | Stone Age | Earn "Stone Age" | Easy | $100 / 1 / 16 bread |
| 2 | Getting an Upgrade | Earn "Getting an Upgrade" | Easy | $100 / 1 / iron pickaxe |
| 3 | Acquire Hardware | Earn "Acquire Hardware" | Easy | $150 / 1 / 8 iron ingots |
| 4 | Suit Up | Earn "Suit Up" | Easy | $150 / 1 / iron helmet |
| 5 | Monster Hunter | Earn "Monster Hunter" | Easy | $100 / 1 / 16 torches |
| 6 | Sweet Dreams | Earn "Sweet Dreams" | Easy | $100 / 1 / 8 cooked beef |
| 7 | Bee Our Guest | Earn "Bee Our Guest" | Easy | $200 / 2 / 4 honey bottles |
| 8 | Diamonds! | Earn "Diamonds!" | Medium | $400 / 3 / Efficiency II book |
| 9 | Enchanter | Earn "Enchanter" | Medium | $400 / 3 / 16 lapis + 8 bookshelves |
| 10 | Hired Help | Earn "Hired Help" | Medium | $500 / 3 / 16 iron ingots |
| 11 | The Healing Power of Friendship | Earn it with an axolotl | Medium | $400 / 3 / Respiration II book |
| 12 | Total Beelocation | Earn "Total Beelocation" | Medium | $500 / 3 / silk touch shears\* |
| 13 | We Need to Go Deeper | Enter the Nether | Medium | $700 / 3 / Protection V gold chestplate |
| 14 | A Terrible Fortress | Find a Nether Fortress | Medium | $600 / 4 / 8 blaze powder |
| 15 | Into Fire | Get a blaze rod | Medium | $600 / 4 / Fire Protection III book |
| 16 | Hot Tourist Destinations | Visit all Nether biomes | Medium | $800 / 5 / 4 gold blocks |
| 17 | Sniper Duel | Earn "Sniper Duel" | Medium | $500 / 3 / Power III book |
| 18 | Sneak 100 | Sneak past a sculk sensor | Medium | $500 / 3 / Swift Sneak I book |
| 19 | Minecraft: Trial(s) Edition | Enter a Trial Chamber | Medium | $600 / 4 / 2 trial keys |
| 20 | Cover Me with Diamonds | Full diamond armor | Hard | $1,000 / 5 / Unbreaking III book |
| 21 | Return to Sender | Kill a ghast with its fireball | Hard | $1,000 / 5 / Flame book |
| 22 | Eye Spy | Enter a stronghold | Hard | $1,200 / 6 / 8 ender pearls |
| 23 | Arbalistic | Earn "Arbalistic" | Hard | $1,200 / 6 / Multishot crossbow |
| 24 | Remote Getaway | Use an End Gateway | Hard | $1,200 / 6 / 4 ender chests |
| 25 | The End? | Enter the End | Hard | $1,500 / 8 / Feather Falling IV book |
| 26 | Hero of the Village | Win a raid | Hard | $1,500 / 8 / 16 emeralds + totem of undying |
| 27 | Over-Overkill | 50 hearts of damage with a mace | Hard | $1,500 / 8 / Density IV book |
| 28 | Free the End | Kill the Ender Dragon | Legendary | $3,000 / 12 / Fortune III book |
| 29 | Sky's the Limit | Find elytra | Legendary | $2,500 / 10 / 32 firework rockets + Unbreaking III book |
| 30 | Serious Dedication | Netherite hoe | Legendary | $3,000 / 12 / Mending book |
| 31 | Monsters Hunted | Kill every hostile mob type | Legendary | $3,500 / 15 / Looting III sword |
| 32 | Adventuring Time | Visit every biome | Legendary | $3,500 / 15 / Mending book |
| 33 | How Did We Get Here? | Have every effect at once | Legendary | $3,500 / 15 / 2 diamond blocks |
|  |  |  |  |  |

**Kill quests (49)**

| # | Quest | Objective | Tier | Rewards |
| --- | --- | --- | --- | --- |
| 34 | Skeleton Hunter I | Kill 3 skeletons | Easy | $100 / 1 / 16 arrows |
| 35 | Skeleton Hunter II | Kill 10 skeletons | Easy | $250 / 2 / bow |
| 36 | Skeleton Hunter III | Kill 25 skeletons | Medium | $500 / 3 / Power II bow |
| 37 | Skeleton Hunter IV | Kill 50 skeletons | Hard | $900 / 5 / Infinity book |
| 38 | Zombie Slayer I | Kill 5 zombies | Easy | $100 / 1 / 8 bread |
| 39 | Zombie Slayer II | Kill 15 zombies | Easy | $250 / 2 / iron sword |
| 40 | Zombie Slayer III | Kill 30 zombies | Medium | $500 / 3 / Smite III book |
| 41 | Zombie Slayer IV | Kill 60 zombies | Hard | $900 / 5 / Sharpness IV book |
| 42 | Spider Squasher I | Kill 5 spiders | Easy | $100 / 1 / 16 string |
| 43 | Spider Squasher II | Kill 15 spiders | Easy | $250 / 2 / 3 cobwebs + lead |
| 44 | Spider Squasher III | Kill 30 spiders | Medium | $500 / 3 / Bane of Arthropods IV book |
| 45 | Creeper Defuser I | Kill 3 creepers | Easy | $150 / 1 / 8 TNT |
| 46 | Creeper Defuser II | Kill 10 creepers | Medium | $400 / 3 / Blast Protection III book |
| 47 | Creeper Defuser III | Kill 25 creepers | Hard | $900 / 5 / 2 music discs |
| 48 | Enderman Hunter I | Kill 3 endermen | Easy | $200 / 2 / 4 ender pearls |
| 49 | Enderman Hunter II | Kill 10 endermen | Medium | $500 / 3 / 8 ender pearls |
| 50 | Enderman Hunter III | Kill 25 endermen | Hard | $1,000 / 6 / Silk Touch book |
| 51 | Blaze Breaker I | Kill 5 blazes | Medium | $400 / 3 / 8 blaze rods |
| 52 | Blaze Breaker II | Kill 15 blazes | Hard | $800 / 5 / Fire Aspect II book |
| 53 | Blaze Breaker III | Kill 30 blazes | Hard | $1,200 / 7 / 4 potions of Fire Resistance (8:00) |
| 54 | Wither Skeleton Reaper I | Kill 3 wither skeletons | Medium | $500 / 3 / 16 coal blocks |
| 55 | Wither Skeleton Reaper II | Kill 10 wither skeletons | Hard | $1,000 / 6 / Looting II book |
| 56 | Wither Skeleton Reaper III | Kill 25 wither skeletons | Hard | $1,800 / 9 / Looting III book |
| 57 | Guardian Slayer I | Kill 1 guardian | Medium | $300 / 3 / 16 prismarine shards |
| 58 | Guardian Slayer II | Kill 10 guardians | Hard | $900 / 5 / Depth Strider III book |
| 59 | Guardian Slayer III | Kill 25 guardians | Hard | $1,500 / 8 / Aqua Affinity book + 8 sea lanterns |
| 60 | Elder Guardian Bane (Squad) | Kill an elder guardian | Hard | $1,800 / 9 / Respiration III book + 4 sponges |
| 61 | Drowned Diver I | Kill 10 drowned | Easy | $250 / 2 / 8 cooked cod |
| 62 | Drowned Diver II | Kill 30 drowned | Medium | $700 / 4 / Impaling III book |
| 63 | Phantom Menace I | Kill 5 phantoms | Medium | $400 / 3 / 4 phantom membranes |
| 64 | Phantom Menace II | Kill 15 phantoms | Hard | $900 / 5 / 8 phantom membranes + Unbreaking II book |
| 65 | Pillager Patrol I | Kill 5 pillagers | Easy | $250 / 2 / crossbow |
| 66 | Pillager Patrol II | Kill 15 pillagers | Medium | $600 / 4 / Quick Charge III book |
| 67 | Pillager Patrol III | Kill 30 pillagers | Hard | $1,200 / 6 / 24 emeralds |
| 68 | Witch Hunter I | Kill 3 witches | Medium | $400 / 3 / 3 splash potions of Healing II |
| 69 | Witch Hunter II | Kill 10 witches | Hard | $900 / 5 / 2 potions of Strength II |
| 70 | Slime Splitter | Kill 20 slimes | Easy | $250 / 2 / 3 sticky pistons |
| 71 | Ghast Buster I | Kill 3 ghasts | Medium | $500 / 3 / 4 ghast tears |
| 72 | Ghast Buster II | Kill 10 ghasts | Hard | $1,000 / 6 / 3 potions of Regeneration |
| 73 | Brute Force | Kill 3 piglin brutes | Hard | $900 / 5 / 3 gold blocks |
| 74 | Breeze Breaker I | Kill 3 breezes | Medium | $500 / 3 / 6 breeze rods |
| 75 | Breeze Breaker II | Kill 10 breezes | Hard | $1,000 / 6 / Wind Burst I book |
| 76 | Shulker Hunter I | Kill 5 shulkers | Hard | $800 / 5 / 2 shulker shells |
| 77 | Shulker Hunter II | Kill 15 shulkers | Hard | $1,500 / 8 / shulker box + Unbreaking III book |
| 78 | Hoglin Hunter | Kill 10 hoglins | Medium | $500 / 3 / 16 cooked porkchops + saddle |
| 79 | Totem Hunter | Kill 3 evokers | Hard | $1,200 / 6 / 8 emeralds + Sharpness V book |
| 80 | Warden Slayer (Squad) | Kill a Warden | Legendary | $3,500 / 15 / Swift Sneak III book |
| 81 | Wither Slayer (Squad) | Kill the Wither | Legendary | $3,500 / 15 / Protection IV book + 2 diamond blocks |
| 82 | Dragonslayers (Squad) | Kill a re-summoned Ender Dragon | Legendary | $3,500 / 15 / dragon head + Feather Falling IV book |

**Fetch quests (18, items are taken)**

| # | Quest | Bring | Tier | Rewards |
| --- | --- | --- | --- | --- |
| 83 | Harvest Season | 64 wheat | Easy | $150 / 1 / iron hoe + 16 bone meal |
| 84 | Lumberjack | 64 oak logs | Easy | $150 / 1 / Efficiency I iron axe |
| 85 | Gone Fishing | 10 raw cod | Easy | $150 / 1 / Luck of the Sea I rod |
| 86 | Wool Gatherer | 32 white wool | Easy | $150 / 1 / 2 beds + shears |
| 87 | Pumpkin Patch | 16 pumpkins | Easy | $150 / 1 / 4 pumpkin pies |
| 88 | Sweet Tooth | 4 honey bottles | Medium | $300 / 3 / 2 honey blocks + campfire |
| 89 | Sniffer's Return | 1 sniffer egg | Medium | $600 / 4 / 8 torchflowers |
| 90 | Shell Collector | 5 nautilus shells | Medium | $700 / 4 / Lure III book |
| 91 | Deep Salvage | 4 ancient debris | Hard | $1,500 / 8 / netherite upgrade template |
| 92 | Heart of the Ocean | 1 heart of the sea | Hard | $1,200 / 6 / Riptide III book |
| 93 | Cheating Death | 1 totem of undying | Hard | $1,500 / 7 / 2 diamond blocks |
| 94 | Skull Collector | 3 wither skeleton skulls | Hard | $2,000 / 10 / Protection IV book |
| 95 | Shell Game | 4 shulker shells | Hard | $1,500 / 8 / Efficiency V book |
| 96 | Echoes of the Deep | 4 echo shards | Hard | $1,500 / 8 / Swift Sneak II book |
| 97 | Sea King's Weapon | 1 trident | Hard | $1,800 / 9 / Loyalty III + Channeling books |
| 98 | Notch's Apple | 1 enchanted golden apple | Legendary | $2,500 / 12 / Mending book |
| 99 | Star of the Nether | 1 nether star | Legendary | $3,500 / 15 / Fortune III book |
| 100 | Dragon Egg Bounty | 1 dragon egg (once per 7 days per player) | Bounty | $5,000 / 20 / Fortune III book + Mending book |

### Wandering quests (30)

One quest per wandering quest-giver, 1-hour limit once accepted, exclusive to wanderers.

| # | Quest | Objective | Tier | Rewards |
| --- | --- | --- | --- | --- |
| W1 | Midnight Watch | Kill 20 hostile mobs in one night | Medium | $800 / 5 / Night Vision potions x3 |
| W2 | Raid Captain Bounty | Kill 3 pillager captains | Hard | $1,500 / 7 / 2 ominous bottles |
| W3 | The Hunter's Trophy | Bring 1 wither skeleton skull | Hard | $1,200 / 6 / Looting III book |
| W4 | Silence the Deep (Squad) | Kill a Warden | Legendary | $3,500 / 15 / 2 diamond blocks + Swift Sneak III book |
| W5 | Sunken Relic | Bring 1 heart of the sea | Hard | $1,500 / 8 / Depth Strider III book + 8 sea lanterns |
| W6 | Merchant's Debt | Bring 16 emeralds | Medium | $900 / 4 / Efficiency IV book |
| W7 | Fire and Brimstone | Kill 10 blazes | Hard | $1,200 / 6 / Fire Protection IV book |
| W8 | Bone Collector | Bring 64 bones | Easy | $400 / 2 / Power III bow |
| W9 | Nest of Fangs | Kill 15 cave spiders | Medium | $800 / 4 / Bane of Arthropods V book |
| W10 | Deserter's Gold | Bring 32 gold ingots | Medium | $1,000 / 5 / 8 golden carrots + Fire Aspect II book |
| W11 | Ghost Ship | Kill 20 drowned | Medium | $900 / 5 / Impaling V book |
| W12 | Sky Pirates | Kill 10 phantoms | Hard | $1,200 / 6 / Feather Falling IV book |
| W13 | Ominous Omens | Bring 3 ominous trial keys | Hard | $1,800 / 9 / Wind Burst II book |
| W14 | The Mace Maker | Bring 1 heavy core | Legendary | $3,000 / 12 / Density V book + Breach IV book |
| W15 | Brute Squad | Kill 5 piglin brutes | Hard | $1,500 / 7 / 3 gold blocks |
| W16 | Slime Rancher | Bring 32 slimeballs | Medium | $700 / 4 / 4 slime blocks + Punch II bow |
| W17 | Witching Hour | Kill 5 witches | Hard | $1,200 / 6 / 4 potions of Strength II |
| W18 | Pearl Courier | Bring 16 ender pearls | Hard | $1,200 / 6 / ender chest + Silk Touch book |
| W19 | Prospector's Tip | Bring 5 diamonds | Medium | $900 / 5 / Fortune II book |
| W20 | Eye of the Storm | Kill 10 breezes | Hard | $1,500 / 8 / Wind Burst I book + 8 wind charges |
| W21 | Stampede | Kill 10 hoglins | Medium | $900 / 5 / Protection III book |
| W22 | Fangs in the Forest | Kill 3 evokers | Hard | $1,800 / 9 / totem of undying |
| W23 | Shulker Siege | Kill 10 shulkers | Hard | $1,800 / 9 / shulker box + Efficiency V book |
| W24 | The Wither Rising (Squad) | Kill the Wither | Legendary | $3,500 / 15 / Mending book + 2 diamond blocks |
| W25 | Zombie Doctor | Cure a zombie villager | Medium | $800 / 4 / golden apple x3 |
| W26 | Beekeeper's Plea | Bring 2 bee nests (with bees) | Medium | $800 / 4 / Silk Touch book |
| W27 | Golden Harvest | Bring 8 golden carrots | Easy | $500 / 3 / saddle + Unbreaking II book |
| W28 | Ancient Forge | Bring 2 netherite scrap | Hard | $1,800 / 9 / netherite upgrade template + 8 diamonds |
| W29 | The Archaeologist | Bring 3 different pottery sherds | Medium | $900 / 5 / brush + Unbreaking III book |
| W30 | Sniffer's Lost Seed | Bring 4 torchflower seeds | Medium | $800 / 4 / Efficiency IV book + 4 pitcher pods |

\*Loot item enchantments that can't normally exist (Protection V, silk touch shears) are given exactly as written by the plugin.

### Head quests (8, extra fetch quests, items are taken)

| # | Quest | Bring | Tier | Rewards |
| --- | --- | --- | --- | --- |
| H1 | Headhunter: Creeper I | 3 creeper heads | Medium | $500 / 3 / 8 TNT + Blast Protection II book |
| H2 | Headhunter: Creeper II | 8 creeper heads | Hard | $1,200 / 6 / Blast Protection IV book |
| H3 | Headhunter: Zombie I | 3 zombie heads | Medium | $400 / 3 / Smite III book |
| H4 | Headhunter: Zombie II | 8 zombie heads | Hard | $1,000 / 5 / Smite V book |
| H5 | Headhunter: Skeleton I | 3 skeleton skulls | Medium | $400 / 3 / Power III book |
| H6 | Headhunter: Skeleton II | 8 skeleton skulls | Hard | $1,000 / 5 / Power V book |
| H7 | Headhunter: Piglin I | 3 piglin heads | Medium | $600 / 4 / 2 gold blocks |
| H8 | Headhunter: Piglin II | 8 piglin heads | Hard | $1,500 / 7 / Fire Protection IV book + 2 gold blocks |

## Extra gameplay features

- **Silk Touch spawners:** mining a spawner with a Silk Touch pickaxe of iron, diamond or netherite drops the spawner and keeps its mob type. Wood, stone and gold pickaxes, or no Silk Touch, break it normally with no drop. Placed spawners keep their mob type.
- **Mob heads:** creepers, zombies, skeletons and piglins each have a 1 in 10 chance to drop their head when killed by a player. Chance configurable per mob. Vanilla charged-creeper head drops still work as normal.
- Heads from these drops are valid for head quests; heads from creative or `/give` are refused like any fetch item.

## Seasonal events

- Admin turns an event on or off: `/quest event start <name>`, `/quest event stop <name>`. Optional start/end dates in config so it runs on its own.
- Event quests are extra: they appear alongside the normal 100, never replacing them. While an event runs, 1 of the 5 menu slots can hold an event quest.
- Event loot carries a lore line with the event and year (e.g. "Winterfest 2026") so it works as a collectible. Same reward caps apply.
- Unfinished event quests are removed when the event ends.

### Winterfest (Christmas) quests

| # | Quest | Objective | Tier | Rewards |
| --- | --- | --- | --- | --- |
| X1 | Gingerbread Baker | Bring 32 cookies | Easy | $200 / 2 / 3 cakes |
| X2 | Snow Day | Bring 64 snowballs | Easy | $150 / 1 / 16 snow blocks + red & green leather boots |
| X3 | Frosty Friend | Build a snow golem | Easy | $200 / 2 / carved pumpkin + 8 snow blocks |
| X4 | Yule Log | Bring 32 spruce logs | Easy | $200 / 2 / 4 campfires |
| X5 | Deck the Halls | Bring 32 lanterns | Medium | $400 / 3 / 8 sea lanterns |
| X6 | Wrapping Paper | Bring 32 paper and 16 red wool | Medium | $400 / 3 / Unbreaking III book |
| X7 | Ice Harvester | Bring 32 packed ice | Medium | $500 / 3 / Frost Walker II boots ("Winterfest 2026") |
| X8 | Reindeer Roundup | Breed 10 goats | Medium | $500 / 4 / 2 goat horns |
| X9 | Polar Expedition | Visit Snowy Plains, Ice Spikes and Frozen Peaks | Medium | $600 / 4 / red & green leather armor set, Protection III |
| X10 | Krampus Hunt | Kill 25 strays | Hard | $1,200 / 6 / "Krampus's Claw" Sharpness IV iron sword |
| X11 | The Naughty List (Squad) | Kill 100 hostile mobs as a squad | Hard | $1,500 / 8 / 2 diamond blocks |
| X12 | Santa's Helper | Finish 6 other Winterfest quests | Legendary | $2,500 / 12 / "Santa's Hat" red leather cap, Protection IV + Mending |

## Admin tools and safeguards

- **Global:** quests and quest-givers work in every world (the town giver lives in your night-only world).
- **Kills:** spawner and trial-spawner kills count normally.
- **Fetch turn-in check:** items obtained through creative mode or `/give`, and items the plugin itself handed out as quest rewards, are tagged and refused at turn-in.
- **Timers keep running** while a player is offline.
- **Admin commands:** `/quest reload`, `/quest admin reset <player> [quest]`, `/quest admin complete <player> <quest>`, `/quest admin give <player> <quest>`, `/quest admin progress <player>`.
- **Economy stats:** `/quest admin stats` shows total money, XP and items paid out today, this week and all-time, plus the top-earning quests.
- **Quest-giver control:** `/quest giver despawn`, `/quest giver spawn` (at the admin's position), `/quest giver name`, `/quest giver skin`. All player progress, active quests, history, cooldowns and streaks are stored per player, not on the NPC, so removing and respawning the giver resets nothing.
- **messages.yml:** every line of chat text and NPC dialogue is editable, with color codes.

## Configuration files

- `config.yml`: general settings, caps, spawn rates, time limits, range checks, weekly reward and d20 tiers, dragon egg drop chance.
- `quests.yml`: every quest line, its levels, requirements and rewards; separate pools for town and wandering givers.
- `givers.yml`: NPC locations, names, skins and dialogue.
- Player data (progress, cooldowns, streaks, reputation) stored per player and saved across restarts.

## Open questions

-
-
-
-
