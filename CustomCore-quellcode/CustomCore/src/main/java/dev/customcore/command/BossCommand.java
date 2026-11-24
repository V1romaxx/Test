package dev.customcore.command;

import dev.customcore.CustomCorePlugin;
import dev.customcore.boss.BossInstance;
import dev.customcore.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BossCommand implements TabExecutor {

    private final CustomCorePlugin plugin;

    public BossCommand(CustomCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Text.mm("<yellow>/boss spawn <id> | list | killall | reload"));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> sender.sendMessage(Text.mm("<gold>Bosse: <white>" + String.join(", ", plugin.bosses().ids())
                    + " <gray>(aktiv: " + plugin.bosses().activeCount() + ")"));
            case "reload" -> {
                plugin.bosses().reload();
                sender.sendMessage(Text.mm("<green>Bosse neu geladen."));
            }
            case "killall" -> sender.sendMessage(Text.mm("<green>" + plugin.bosses().killAll() + " Boss(e) entfernt."));
            case "spawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Text.mm("<red>Nur als Spieler nutzbar."));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Text.mm("<red>Nutzung: /boss spawn <id>"));
                    return true;
                }
                BossInstance boss = plugin.bosses().spawn(args[1], player.getLocation());
                sender.sendMessage(boss == null
                        ? Text.mm("<red>Unbekannter Boss: " + args[1])
                        : Text.mm("<green>Boss gespawnt."));
            }
            default -> sender.sendMessage(Text.mm("<red>Unbekannter Unterbefehl."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("spawn", "list", "killall", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            out.addAll(plugin.bosses().ids());
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
