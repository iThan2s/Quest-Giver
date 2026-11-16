package io.github.ithan2s.questgiver.gui;

import io.github.ithan2s.questgiver.config.Messages;
import io.github.ithan2s.questgiver.util.Items;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Solo-or-squad choice shown to squad leaders when accepting a quest. */
public final class ChoiceMenu extends Menu {
    public ChoiceMenu(Messages msg, ItemStack info, String squadName, boolean soloAllowed, Runnable solo,
                      Runnable squad) {
        super(3, msg.raw("menu.choice-title"));
        set(13, info);
        if (soloAllowed) {
            set(11, Items.icon(Material.IRON_SWORD, msg.raw("menu.choice-solo"), msg.list("menu.choice-solo-lore")),
                    e -> {
                        e.getWhoClicked().closeInventory();
                        solo.run();
                    });
        }
        set(15, Items.icon(Material.SHIELD, msg.raw("menu.choice-squad", "squad", squadName),
                msg.list("menu.choice-squad-lore", "squad", squadName)), e -> {
            e.getWhoClicked().closeInventory();
            squad.run();
        });
        fill(0, 26, Material.GRAY_STAINED_GLASS_PANE);
    }
}
