package io.github.ithan2s.questgiver.npc;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.github.ithan2s.questgiver.storage.Storage;
import io.github.ithan2s.questgiver.util.Durations;
import io.github.ithan2s.questgiver.util.Tasks;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Looks up player skins by name off the main thread and caches them (memory + database), so NPCs keep their skin
 * across restarts without contacting Mojang again. Results complete on the main thread.
 */
public final class SkinService {
    public record Skin(String name, String texture, String signature) {
        public ResolvableProfile profile() {
            ResolvableProfile.Builder b = ResolvableProfile.resolvableProfile().name(safeName(name));
            if (texture != null && !texture.isEmpty()) {
                b.addProperty(new ProfileProperty("textures", texture,
                        signature == null || signature.isEmpty() ? null : signature));
            }
            return b.build();
        }
    }

    private static final long REFRESH_AFTER = 7 * Durations.DAY;

    private final Storage storage;
    private final Map<String, Skin> memory = new ConcurrentHashMap<>();

    public SkinService(Storage storage) {
        this.storage = storage;
    }

    /** Profile names are limited to 16 characters of [A-Za-z0-9_]. */
    static String safeName(String name) {
        String cleaned = name == null ? "" : name.replaceAll("[^A-Za-z0-9_]", "");
        if (cleaned.isEmpty()) {
            cleaned = "QuestGiver";
        }
        return cleaned.length() > 16 ? cleaned.substring(0, 16) : cleaned;
    }

    public CompletableFuture<Skin> lookup(String playerName) {
        String key = playerName.toLowerCase(Locale.ROOT);
        CompletableFuture<Skin> result = new CompletableFuture<>();
        Skin cached = memory.get(key);
        if (cached != null) {
            result.complete(cached);
            return result;
        }
        storage.supply(c -> Storage.getKv(c, "skin:" + key)).whenComplete((stored, err) -> {
            if (stored != null) {
                String[] parts = stored.split("\\|", 4);
                if (parts.length == 4 && System.currentTimeMillis() - Long.parseLong(parts[0]) < REFRESH_AFTER) {
                    Skin skin = new Skin(parts[1], parts[2], parts[3]);
                    memory.put(key, skin);
                    Tasks.main(() -> result.complete(skin));
                    return;
                }
            }
            fetch(playerName, key, result);
        });
        return result;
    }

    private void fetch(String playerName, String key, CompletableFuture<Skin> result) {
        Tasks.async(() -> {
            try {
                PlayerProfile profile = Bukkit.createProfile(playerName);
                if (!profile.complete(true)) {
                    Tasks.main(() -> result.completeExceptionally(new IllegalArgumentException("Unknown player")));
                    return;
                }
                String texture = null;
                String signature = null;
                for (ProfileProperty property : profile.getProperties()) {
                    if (property.getName().equals("textures")) {
                        texture = property.getValue();
                        signature = property.getSignature();
                    }
                }
                if (texture == null) {
                    Tasks.main(() -> result.completeExceptionally(new IllegalArgumentException("No skin")));
                    return;
                }
                String realName = profile.getName() == null ? playerName : profile.getName();
                Skin skin = new Skin(realName, texture, signature == null ? "" : signature);
                memory.put(key, skin);
                String value = System.currentTimeMillis() + "|" + realName + "|" + texture + "|" + skin.signature();
                storage.run(c -> {
                    Storage.setKv(c, "skin:" + key, value);
                    return null;
                });
                Tasks.main(() -> result.complete(skin));
            } catch (Exception e) {
                Tasks.main(() -> result.completeExceptionally(e));
            }
        });
    }
}
