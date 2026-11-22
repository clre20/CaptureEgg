package clre20.captureEgg.listener;

import clre20.captureEgg.capture.CaptureService;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;

/** 玩家丟出的雞蛋打中生物 → 捕捉。 */
public final class CaptureListener implements Listener {

    private final CaptureService service;

    public CaptureListener(CaptureService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEggHit(ProjectileHitEvent event) {
        // 快速過濾：只處理雞蛋、打中的是生物、丟的人是玩家
        if (!(event.getEntity() instanceof Egg egg)) {
            return;
        }
        Entity hit = event.getHitEntity();
        if (!(hit instanceof LivingEntity target) || hit instanceof Player) {
            return;
        }
        if (!(egg.getShooter() instanceof Player player)) {
            return;
        }
        // 不論成功或被拒絕，雞蛋都直接消失：不孵小雞、不造成擊退
        event.setCancelled(true);
        egg.remove();
        service.tryCapture(player, target);
    }

    /** 記錄生物生成來源（生怪磚、試煉生怪磚），供捕捉時判斷。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            entity.getPersistentDataContainer().set(service.getKeySpawner(), PersistentDataType.BYTE, (byte) 1);
        } else if (reason == CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER) {
            entity.getPersistentDataContainer().set(service.getKeyTrialSpawner(), PersistentDataType.BYTE, (byte) 1);
        }
    }

}
