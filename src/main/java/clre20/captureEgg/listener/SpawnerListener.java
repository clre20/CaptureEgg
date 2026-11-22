package clre20.captureEgg.listener;

import clre20.captureEgg.CaptureEgg;
import clre20.captureEgg.capture.CaptureService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.Map;

/**
 * 生怪磚（不含試煉生怪磚）互動監聽器：
 * 1. 空手右鍵生怪磚：將蛋取出來，標記成取出者捕捉，並將生怪磚重置為空。
 * 2. 手持原生捕捉蛋右鍵生怪磚：放入蛋（只有原生捕捉蛋可以放）。
 */
public final class SpawnerListener implements Listener {

    private final CaptureEgg plugin;
    private final CaptureService service;

    public SpawnerListener(CaptureEgg plugin, CaptureService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.SPAWNER) {
            return;
        }

        // 領地與保護插件防護檢查
        if (event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();

        if (!(clicked.getState() instanceof CreatureSpawner spawner)) {
            return;
        }

        // 情況 A：手持捕捉蛋 -> 放入生怪磚 (僅限原生生物蛋)
        if (service.isCaptureEgg(held)) {
            event.setCancelled(true);

            if (!player.hasPermission("captureegg.spawner.insert")) {
                plugin.messages().send(player, "spawner.no-insert-permission");
                return;
            }

            // 若生怪磚已有生物蛋，提示玩家先空手取出
            if (spawner.getSpawnedType() != null) {
                plugin.messages().send(player, "spawner.already-has-mob");
                return;
            }

            // 根據需求：只有原生的可以放
            if (!service.isNativeEgg(held)) {
                plugin.messages().send(player, "spawner.not-native");
                return;
            }

            EntityType type = service.getEntityType(held);
            if (type == null) {
                plugin.messages().send(player, "release.corrupted");
                return;
            }

            // 放入生怪磚
            spawner.setSpawnedType(type);
            spawner.resetTimer();
            spawner.update(true, true);

            // 扣除手持的捕捉蛋
            if (held.getAmount() > 1) {
                held.setAmount(held.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }

            // 視覺與音效
            Location center = clicked.getLocation().add(0.5, 0.5, 0.5);
            clicked.getWorld().playSound(center, Sound.BLOCK_ANVIL_USE, 0.8F, 1.2F);
            clicked.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center, 15, 0.3, 0.3, 0.3, 0.1);

            plugin.messages().send(player, "spawner.insert-success", Map.of("entity", service.typeName(held)));
            return;
        }

        // 情況 B：空手右鍵 -> 從生怪磚取出蛋 (標記成取出者捕捉)
        if (held == null || held.getType().isAir()) {
            event.setCancelled(true);

            if (!player.hasPermission("captureegg.spawner.extract")) {
                plugin.messages().send(player, "spawner.no-extract-permission");
                return;
            }

            EntityType spawnedType = spawner.getSpawnedType();
            if (spawnedType == null) {
                plugin.messages().send(player, "spawner.empty");
                return;
            }

            Material eggMat = Bukkit.getItemFactory().getSpawnEgg(spawnedType);
            if (eggMat == null) {
                plugin.messages().send(player, "deny.no-spawn-egg", Map.of("entity", Component.translatable(spawnedType.translationKey())));
                return;
            }

            org.bukkit.entity.EntitySnapshot snapshot = spawner.getSpawnedEntity();
            ItemStack egg = service.buildSpawnerExtractedEgg(eggMat, spawnedType, snapshot, player, clicked.getWorld());
            if (egg == null) {
                plugin.messages().send(player, "deny.error");
                return;
            }

            // 清空生怪磚
            spawner.setSpawnedType(null);
            spawner.setSpawnedEntity((org.bukkit.entity.EntitySnapshot) null);
            spawner.setPotentialSpawns(Collections.emptyList());
            spawner.update(true, true);

            // 給予玩家蛋
            service.giveEgg(player, clicked.getLocation().add(0.5, 1.0, 0.5), egg);

            // 視覺與音效
            Location center = clicked.getLocation().add(0.5, 0.5, 0.5);
            clicked.getWorld().playSound(center, Sound.ENTITY_ITEM_PICKUP, 1.0F, 0.8F);
            clicked.getWorld().spawnParticle(Particle.POOF, center, 16, 0.3, 0.3, 0.3, 0.05);

            plugin.messages().send(player, "spawner.extract-success", Map.of("entity", Component.translatable(spawnedType.translationKey())));
            return;
        }
    }
}
