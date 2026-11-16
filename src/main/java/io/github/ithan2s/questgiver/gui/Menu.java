package io.github.ithan2s.questgiver.gui;

import io.github.ithan2s.questgiver.util.Items;
import io.github.ithan2s.questgiver.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** A chest menu whose items can't be taken. Each slot can have a click handler. */
public abstract class Menu implements InventoryHolder {
    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> handlers = new HashMap<>();

    protected Menu(int rows, String title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, Text.color(title));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> handler) {
        inventory.setItem(slot, item);
        if (handler != null) {
            handlers.put(slot, handler);
        } else {
            handlers.remove(slot);
        }
    }

    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    protected void fill(int from, int to, Material pane) {
        ItemStack filler = Items.icon(pane, " ", List.of());
        for (int i = from; i <= to; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    void click(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> handler = handlers.get(event.getRawSlot());
        if (handler != null) {
            handler.accept(event);
        }
    }
}
