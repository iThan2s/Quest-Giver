package io.github.ithan2s.questgiver.gui;

import io.github.ithan2s.questgiver.npc.GiverDefinition;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Extra buttons other modules add to the giver menu (weekly reward, dragon egg bounty). */
public final class MenuHooks {
    @FunctionalInterface
    public interface Decorator {
        void decorate(GiverMenu menu, Player player, GiverDefinition giver);
    }

    private final List<Decorator> decorators = new ArrayList<>();

    public void add(Decorator decorator) {
        decorators.add(decorator);
    }

    void decorate(GiverMenu menu, Player player, GiverDefinition giver) {
        for (Decorator d : decorators) {
            d.decorate(menu, player, giver);
        }
    }
}
