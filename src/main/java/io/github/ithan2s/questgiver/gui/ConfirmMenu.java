package io.github.ithan2s.questgiver.gui;

import io.github.ithan2s.questgiver.config.Messages;
import io.github.ithan2s.questgiver.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Yes/no dialog, e.g. before rare fetch items or the dragon egg are taken. */
public final class ConfirmMenu extends Menu {
    public ConfirmMenu(Messages msg, String title, ItemStack info, Runnable onConfirm, Runnable onCancel) {
        super(3, title);
        set(11, Items.icon(Material.LIME_CONCRETE, msg.raw("menu.confirm"), msg.list("menu.confirm-lore")), e -> {
            e.getWhoClicked().closeInventory();
            onConfirm.run();
        });
        set(13, info);
        set(15, Items.icon(Material.RED_CONCRETE, msg.raw("menu.cancel"), List.of()), e -> {
            e.getWhoClicked().closeInventory();
            if (onCancel != null) onCancel.run();
        });
        fill(0, 26, Material.GRAY_STAINED_GLASS_PANE);
    }

    public static void ask(Messages msg, Player p, String title, ItemStack info, Runnable onConfirm, Runnable onCancel) {
        new ConfirmMenu(msg, title, info, onConfirm, onCancel).open(p);
    }
}
