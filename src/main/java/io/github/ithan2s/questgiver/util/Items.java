package io.github.ithan2s.questgiver.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/** Inventory helpers: overflow-safe giving, counting and removing items. */
public final class Items {
    private Items() {
    }

    /** Gives items, splitting oversized stacks and dropping anything that doesn't fit at the player's feet. */
    public static void give(Player player, Collection<ItemStack> items) {
        PlayerInventory inv = player.getInventory();
        for (ItemStack item : items) {
            for (ItemStack part : split(item)) {
                for (ItemStack overflow : inv.addItem(part).values()) {
                    player.getWorld().dropItem(player.getLocation(), overflow, drop -> {
                        drop.setOwner(player.getUniqueId());
                        drop.setPickupDelay(0);
                    });
                }
            }
        }
    }

    /** Splits a stack whose amount exceeds its max stack size (e.g. 4 potions) into legal stacks. */
    public static List<ItemStack> split(ItemStack item) {
        List<ItemStack> out = new ArrayList<>();
        int max = Math.max(1, item.getMaxStackSize());
        int left = item.getAmount();
        while (left > 0) {
            ItemStack part = item.clone();
            int n = Math.min(max, left);
            part.setAmount(n);
            out.add(part);
            left -= n;
        }
        return out;
    }

    /** Counts items in the player's storage slots that match the predicate. */
    public static int count(Player player, Predicate<ItemStack> match) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && !stack.isEmpty() && match.test(stack)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Removes up to {@code amount} matching items from storage slots. Returns how many were removed. */
    public static int remove(Player player, Predicate<ItemStack> match, int amount) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.isEmpty() || !match.test(stack)) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            left -= take;
            if (take >= stack.getAmount()) {
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
        }
        inv.setStorageContents(contents);
        return amount - left;
    }

    /** A simple menu icon. */
    public static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(Text.item(name));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(Text.itemLines(lore));
            }
            meta.addItemFlags(ItemFlag.values());
        });
        return stack;
    }

    public static ItemStack named(ItemStack base, Component name, List<Component> lore) {
        ItemStack stack = base.clone();
        stack.editMeta(meta -> {
            meta.displayName(name);
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
        });
        return stack;
    }
}
