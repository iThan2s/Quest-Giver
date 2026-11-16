package io.github.ithan2s.questgiver.command;

import io.github.ithan2s.questgiver.config.Messages;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A small subcommand router for /quest. Leaves are registered by path ("giver create"); the remaining arguments are
 * handed to the leaf. Usage text for each leaf comes from messages.yml ("usage.giver-create").
 */
public final class CommandTree implements BasicCommand {
    @FunctionalInterface
    public interface Handler {
        void run(CommandSender sender, String[] args);
    }

    @FunctionalInterface
    public interface Completer {
        List<String> complete(CommandSender sender, String[] args);
    }

    public record Leaf(String path, String permission, boolean playerOnly, Handler handler, Completer completer) {
        public String usageKey() {
            return "usage." + (path.isEmpty() ? "root" : path.replace(' ', '-'));
        }
    }

    private final Map<String, Leaf> leaves = new LinkedHashMap<>();
    private final Supplier<Messages> messages;
    private final Logger logger;

    public CommandTree(Supplier<Messages> messages, Logger logger) {
        this.messages = messages;
        this.logger = logger;
    }

    public void register(String path, String permission, boolean playerOnly, Handler handler, Completer completer) {
        leaves.put(path, new Leaf(path, permission, playerOnly, handler, completer));
    }

    public void register(String path, String permission, boolean playerOnly, Handler handler) {
        register(path, permission, playerOnly, handler, null);
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        Leaf leaf = null;
        int depth = 0;
        for (int d = Math.min(args.length, 3); d >= 0; d--) {
            String path = String.join(" ", Arrays.copyOf(args, d)).toLowerCase(Locale.ROOT);
            Leaf found = leaves.get(path);
            if (found != null) {
                leaf = found;
                depth = d;
                break;
            }
        }
        if (leaf == null || (leaf.path().isEmpty() && args.length > 0)) {
            sendHelp(sender, args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "");
            return;
        }
        if (leaf.permission() != null && !sender.hasPermission(leaf.permission())) {
            messages.get().send(sender, "general.no-permission");
            return;
        }
        if (leaf.playerOnly() && !(sender instanceof Player)) {
            messages.get().send(sender, "general.players-only");
            return;
        }
        try {
            leaf.handler().run(sender, Arrays.copyOfRange(args, depth, args.length));
        } catch (UsageException e) {
            messages.get().send(sender, "general.usage", "usage", messages.get().raw(leaf.usageKey()));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error running /quest " + String.join(" ", args), e);
            messages.get().send(sender, "general.error");
        }
    }

    public void sendHelp(CommandSender sender, String group) {
        Messages m = messages.get();
        m.sendPlain(sender, "help.header");
        boolean groupMatches = leaves.keySet().stream().anyMatch(p -> p.startsWith(group + " "));
        for (Leaf leaf : leaves.values()) {
            if (leaf.permission() != null && !sender.hasPermission(leaf.permission())) {
                continue;
            }
            if (groupMatches && !leaf.path().startsWith(group + " ")) {
                continue;
            }
            m.sendPlain(sender, "help.line", "usage", m.raw(leaf.usageKey()));
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        String[] a = args.length == 0 ? new String[]{""} : args;
        List<String> out = new ArrayList<>();
        // Subcommand names at this depth.
        String prefix = String.join(" ", Arrays.copyOf(a, a.length - 1)).toLowerCase(Locale.ROOT);
        String partial = a[a.length - 1].toLowerCase(Locale.ROOT);
        for (Leaf leaf : leaves.values()) {
            if (leaf.permission() != null && !sender.hasPermission(leaf.permission())) {
                continue;
            }
            String[] parts = leaf.path().isEmpty() ? new String[0] : leaf.path().split(" ");
            if (parts.length < a.length) {
                continue;
            }
            String leafPrefix = String.join(" ", Arrays.copyOf(parts, a.length - 1));
            if (leafPrefix.equals(prefix) && parts[a.length - 1].startsWith(partial)
                    && !out.contains(parts[a.length - 1])) {
                out.add(parts[a.length - 1]);
            }
        }
        // Arguments of the deepest matching leaf.
        for (int d = Math.min(a.length - 1, 3); d >= 1; d--) {
            Leaf leaf = leaves.get(String.join(" ", Arrays.copyOf(a, d)).toLowerCase(Locale.ROOT));
            if (leaf != null) {
                if (leaf.completer() != null
                        && (leaf.permission() == null || sender.hasPermission(leaf.permission()))) {
                    String[] rest = Arrays.copyOfRange(a, d, a.length);
                    String last = rest[rest.length - 1].toLowerCase(Locale.ROOT);
                    for (String s : leaf.completer().complete(sender, rest)) {
                        if (s.toLowerCase(Locale.ROOT).startsWith(last)) {
                            out.add(s);
                        }
                    }
                }
                break;
            }
        }
        return out;
    }

    /** Thrown by handlers to print the leaf's usage line. */
    public static final class UsageException extends RuntimeException {
        public UsageException() {
            super(null, null, false, false);
        }
    }

    public static void require(boolean condition) {
        if (!condition) {
            throw new UsageException();
        }
    }
}
