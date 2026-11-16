package io.github.ithan2s.questgiver.quest;

import java.util.List;

/**
 * @param money       Vault money
 * @param xp          experience levels, added on top of the player's current level
 * @param items       loot (never docked by the death penalty)
 * @param commands    console commands; {player}, {uuid} and {quest} are replaced
 * @param reputation  cosmetic reputation points, or -1 to use the tier default from config.yml
 */
public record RewardSpec(double money, int xp, List<ItemSpec> items, List<String> commands, int reputation) {
    public static final RewardSpec EMPTY = new RewardSpec(0, 0, List.of(), List.of(), -1);
}
