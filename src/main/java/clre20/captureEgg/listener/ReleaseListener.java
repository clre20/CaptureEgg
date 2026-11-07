package clre20.captureEgg.listener;

import clre20.captureEgg.CaptureEgg;
import clre20.captureEgg.capture.CaptureService;
import clre20.captureEgg.capture.CaptureService.ReleaseResult;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** 捕捉蛋的放出：玩家右鍵方塊、發射器；並擋下對生怪磚與生物的使用。 */
public final class ReleaseListener implements Listener {

    private final CaptureEgg plugin;
    private final CaptureService service;

    public ReleaseListener(CaptureEgg plugin, CaptureService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        ItemStack item = event.getItem();
        if (!service.isCaptureEgg(item)) {
            return;
        }
        // 已被其他插件（領地保護等）禁止使用物品 → 尊重，不放出
        if (event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        if (action == Action.RIGHT_CLICK_AIR || clicked == null) {
            // 原版生怪蛋對空氣使用會在水中生成全新的生物，一律擋下
            event.setUseItemInHand(Event.Result.DENY);
            return;
        }
        Material type = clicked.getType();
        if (type == Material.SPAWNER || type == Material.TRIAL_SPAWNER) {
            event.setCancelled(true);
            plugin.messages().send(player, "release.spawner");
            return;
        }
        // 箱子、門等可互動方塊：沒蹲下時照常開啟方塊，不放出
        if (type.isInteractable() && !player.isSneaking() && event.useInteractedBlock() != Event.Result.DENY) {
            event.setUseItemInHand(Event.Result.DENY);
            return;
        }
        event.setCancelled(true);
        if (!player.hasPermission("captureegg.release")) {
            plugin.messages().send(player, "release.no-permission");
            return;
        }
        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }
        BlockFace face = event.getBlockFace();
        Block target = clicked.isPassable() ? clicked : clicked.getRelative(face);
        Location at = target.getLocation().add(0.5D, 0D, 0.5D);
        float yaw = player.getLocation().getYaw() + 180F;

        ItemStack inHand = player.getInventory().getItem(hand);
        if (!service.isCaptureEgg(inHand)) {
            return;
        }
        Component typeName = service.typeName(inHand);
        ReleaseResult result = service.release(inHand, at, yaw);
        switch (result) {
            case SUCCESS -> {
                int left = inHand.getAmount() - 1;
                if (left <= 0) {
                    player.getInventory().setItem(hand, null);
                } else {
                    inHand.setAmount(left);
                    player.getInventory().setItem(hand, inHand);
                }
                plugin.messages().send(player, "release.success", Map.of("entity", typeName));
            }
            case CORRUPTED -> plugin.messages().send(player, "release.corrupted");
            case FAILED -> plugin.messages().send(player, "release.failed");
            case PEACEFUL -> plugin.messages().send(player, "release.peaceful", Map.of("entity", typeName));
        }
    }

    /** 捕捉蛋不能對生物使用（原版會生出幼年的全新生物）。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (!service.isCaptureEgg(item)) {
            return;
        }
        event.setCancelled(true);
        plugin.messages().send(event.getPlayer(), "release.on-entity");
    }

    /** 發射器射出捕捉蛋 → 放出原本的生物。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        ItemStack item = event.getItem();
        if (!service.isCaptureEgg(item)) {
            return;
        }
        // 先取消原版行為（原版會生成一隻全新的生物）
        event.setCancelled(true);
        if (!plugin.settings().dispenserRelease()) {
            return;
        }
        Block block = event.getBlock();
        if (!(block.getBlockData() instanceof Directional directional)) {
            return;
        }
        if (!(block.getState(false) instanceof InventoryHolder holder)) {
            return;
        }
        // 找到發射器裡對應的那一格；原版取消後會把同一個物品物件寫回格子，所以直接改這個物件的數量
        Inventory inv = holder.getInventory();
        int slot = -1;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s != null && s.isSimilar(item)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            return;
        }
        BlockFace facing = directional.getFacing();
        Block front = block.getRelative(facing);
        Location at = front.getLocation().add(0.5D, 0D, 0.5D);
        float yaw = switch (facing) {
            case NORTH -> 180F;
            case EAST -> -90F;
            case WEST -> 90F;
            default -> 0F;
        };
        if (service.release(item, at, yaw) != ReleaseResult.SUCCESS) {
            return;
        }
        ItemStack stack = inv.getItem(slot);
        if (stack != null) {
            stack.setAmount(stack.getAmount() - 1);
        }
    }
}
