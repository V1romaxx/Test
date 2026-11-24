package dev.customcore.command;

import dev.customcore.CustomCorePlugin;
import dev.customcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WeaponCommand implements TabExecutor {

    private final CustomCorePlugin plugin;

    public WeaponCommand(CustomCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Text.mm("<yellow>/weapon give [spieler] <id> [anzahl] | list | reload"));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> sender.sendMessage(Text.mm("<gold>Waffen: <white>" + String.join(", ", plugin.weapons().ids())));
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(Text.mm("<green>Waffen und Bosse neu geladen."));
            }
            case "give" -> give(sender, args);
            default -> sender.sendMessage(Text.mm("<red>Unbekannter Unterbefehl."));
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        Player target;
        String id;
        int amount = 1;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[1]);
            id = args[2];
            if (args.length >= 4) {
                try {
                    amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                } catch (NumberFormatException ignored) {
                    // Standardwert 1
                }
            }
        } else if (args.length == 2 && sender instanceof Player self) {
            target = self;
            id = args[1];
        } else {
            sender.sendMessage(Text.mm("<red>Nutzung: /weapon give [spieler] <id> [anzahl]"));
            return;
        }
        if (target == null) {
            sender.sendMessage(Text.mm("<red>Spieler nicht gefunden."));
            return;
        }
        ItemStack item = plugin.weapons().create(id);
        if (item == null) {
            sender.sendMessage(Text.mm("<red>Unbekannte Waffe: " + id));
            return;
        }
        item.setAmount(amount);
        target.getInventory().addItem(item).values()
                .forEach(rest -> target.getWorld().dropItem(target.getLocation(), rest));
        sender.sendMessage(Text.mm("<green>" + target.getName() + " hat " + amount + "x " + id + " erhalten."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("give", "list", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            out.addAll(plugin.weapons().ids());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            out.addAll(plugin.weapons().ids());
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
