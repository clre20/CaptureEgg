package clre20.captureEgg.listener;

import clre20.captureEgg.CaptureEgg;
import clre20.captureEgg.update.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 管理員登入 3 秒後的新版本提醒；玩家離線時清除快取與排程。 */
public final class PlayerListener implements Listener {

    private final CaptureEgg plugin;
    private final Map<UUID, BukkitTask> pending = new HashMap<>();

    public PlayerListener(CaptureEgg plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.settings().notifyOnJoin() || !player.hasPermission("captureegg.version")) {
            return;
        }
        UUID id = player.getUniqueId();
        BukkitTask old = pending.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pending.remove(id);
            plugin.updateChecker().check(result -> {
                Player p = Bukkit.getPlayer(id);
                if (p == null || result.status() != UpdateChecker.Status.NEW_VERSION) {
                    return;
                }
                plugin.messages().send(p, "update.join-notify", plugin.updateVars(result));
            });
        }, 60L));
        if (old != null) {
            old.cancel();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        BukkitTask task = pending.remove(id);
        if (task != null) {
            task.cancel();
        }
        plugin.captureService().forget(id);
    }

    public void clear() {
        pending.values().forEach(BukkitTask::cancel);
        pending.clear();
    }
}
