package clre20.captureEgg.command;

import clre20.captureEgg.CaptureEgg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /captureegg [help|version|reload]（別名 /ce） */
public final class CaptureEggCommand implements TabExecutor {

    private final CaptureEgg plugin;

    public CaptureEggCommand(CaptureEgg plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        // label 只用於顯示，限制成指令名稱字元，避免奇怪內容出現在訊息裡
        String shownLabel = label.matches("[A-Za-z0-9_:\\-]{1,32}") ? label : "captureegg";
        if (!sender.hasPermission("captureegg.use")) {
            plugin.messages().send(sender, "command.no-permission");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, shownLabel);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "version" -> version(sender);
            case "reload" -> reload(sender);
            default -> plugin.messages().send(sender, "command.unknown",
                    Map.of("label", Component.text(shownLabel)));
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        Map<String, Component> vars = Map.of(
                "version", Component.text(plugin.updateChecker().current()),
                "label", Component.text(label));
        for (String line : plugin.messages().rawList("command.help")) {
            plugin.messages().sendLine(sender, line, vars);
        }
        if (sender.hasPermission("captureegg.version")) {
            plugin.messages().sendLine(sender, plugin.messages().raw("command.help-version"), vars);
        }
        if (sender.hasPermission("captureegg.reload")) {
            plugin.messages().sendLine(sender, plugin.messages().raw("command.help-reload"), vars);
        }
    }

    private void version(CommandSender sender) {
        if (!sender.hasPermission("captureegg.version")) {
            plugin.messages().send(sender, "command.no-permission");
            return;
        }
        plugin.messages().send(sender, "update.current",
                Map.of("current", Component.text(plugin.updateChecker().current())));
        plugin.messages().send(sender, "update.checking");
        // 玩家只記 UUID，回呼時重新取得，避免持有離線玩家
        UUID id = sender instanceof Player p ? p.getUniqueId() : null;
        plugin.updateChecker().check(result -> {
            CommandSender to = id == null ? Bukkit.getConsoleSender() : Bukkit.getPlayer(id);
            if (to == null) {
                return;
            }
            String path = switch (result.status()) {
                case NEW_VERSION -> "update.new-version";
                case UP_TO_DATE -> "update.up-to-date";
                case DEV_BUILD -> "update.dev-build";
                case NO_RELEASE -> "update.no-release";
                case FAILED -> "update.failed";
            };
            plugin.messages().send(to, path, plugin.updateVars(result));
        });
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("captureegg.reload")) {
            plugin.messages().send(sender, "command.no-permission");
            return;
        }
        boolean ok = plugin.reloadAll();
        plugin.messages().send(sender, ok ? "command.reloaded" : "command.reload-failed");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1 || !sender.hasPermission("captureegg.use")) {
            return out;
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        add(out, prefix, "help", true);
        add(out, prefix, "version", sender.hasPermission("captureegg.version"));
        add(out, prefix, "reload", sender.hasPermission("captureegg.reload"));
        return out;
    }

    private static void add(List<String> out, String prefix, String option, boolean allowed) {
        if (allowed && option.startsWith(prefix)) {
            out.add(option);
        }
    }
}
