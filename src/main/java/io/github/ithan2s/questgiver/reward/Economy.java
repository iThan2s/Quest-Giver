package io.github.ithan2s.questgiver.reward;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Logger;

/** Vault bridge to the server economy (EssentialsX). */
public final class Economy {
    private final Logger logger;
    private net.milkbowl.vault.economy.Economy economy;
    private boolean warned;

    public Economy(Logger logger) {
        this.logger = logger;
    }

    public void hook() {
        RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp =
                Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        economy = rsp == null ? null : rsp.getProvider();
        if (economy == null) {
            logger.warning("Vault found no economy plugin (is EssentialsX installed?). Money rewards will be skipped.");
        } else {
            logger.info("Hooked into economy: " + economy.getName());
        }
    }

    public boolean available() {
        return economy != null;
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (amount <= 0) {
            return true;
        }
        if (economy == null) {
            hook();
            if (economy == null) {
                if (!warned) {
                    warned = true;
                    logger.warning("No economy: could not pay " + amount + " to " + player.getName());
                }
                return false;
            }
        }
        return economy.depositPlayer(player, amount).transactionSuccess();
    }
}
