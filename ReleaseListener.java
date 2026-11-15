package clre20.captureEgg.listener;

import clre20.captureEgg.CaptureEgg;
import clre20.captureEgg.capture.CaptureService;
import clre20.captureEgg.capture.CaptureService.ReleaseResult;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CreatureSpawner;
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

import java.util.HashMap;
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
        if (item == null || item.getType().isAir()) {
            // 空手右鍵生怪磚：取出生物（其他情況快速略過）
            if (action == Action.RIGHT_CLICK_BLOCK && event.getHand() == EquipmentSlot.HAND
                    && event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.SPAWNER) {
                onSpawnerExtract(event);
            }
            return;
        }
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
            // 領地保護插件只禁止與方塊互動時也要尊重，避免在別人的領地改生怪磚
            boolean blocked = event.useInteractedBlock() == Event.Result.DENY;
            event.setCancelled(true);
            if (blocked) {
                return;
            }
            convertSpawner(player, event.getHand(), clicked);
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

    /** 把捕捉蛋放進空的生怪磚，成功才扣除捕捉蛋。 */
    private void convertSpawner(Player player, EquipmentSlot hand, Block clicked) {
        if (!plugin.settings().spawnerConvert()) {
            plugin.messages().send(player, "release.spawner");
            return;
        }
        if (!player.hasPermission("captureegg.spawner")) {
            plugin.messages().send(player, "release.spawner-no-permission");
            return;
        }
        if (hand == null) {
            return;
        }
        ItemStack inHand = player.getInventory().getItem(hand);
        if (!service.isCaptureEgg(inHand)) {
            return;
        }
        Component typeName = service.typeName(inHand);
        Map<String, Component> vars = Map.of("entity", typeName);
        switch (service.fillSpawner(inHand, clicked)) {
            case SUCCESS -> {
                int left = inHand.getAmount() - 1;
                if (left <= 0) {
                    player.getInventory().setItem(hand, null);
                } else {
                    inHand.setAmount(left);
                    player.getInventory().setItem(hand, inHand);
                }
                plugin.messages().send(player, "release.spawner-changed", vars);
            }
            case NOT_EMPTY -> plugin.messages().send(player, "release.spawner-not-empty");
            case FAILED -> plugin.messages().send(player, "release.failed");
        }
    }

    /** 空手右鍵有生物的生怪磚：生怪磚清空，玩家得到這種生物的捕捉蛋（捕捉者記為點擊的玩家）。 */
    private void onSpawnerExtract(PlayerInteractEvent event) {
        if (!plugin.settings().spawnerExtract() || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!(block.getState(false) instanceof CreatureSpawner spawner) || spawner.getSpawnedType() == null) {
            return;
        }
        Player player = event.getPlayer();
        event.setCancelled(true);
        if (!player.hasPermission("captureegg.spawner")) {
            plugin.messages().send(player, "release.spawner-no-permission");
            return;
        }
        // 領地檢查：模擬「拿著這種生物的生怪蛋右鍵生怪磚」交給領地插件判斷（Residence、WorldGuard 等都會檢查這個動作）
        Material probeEgg = Bukkit.getItemFactory().getSpawnEgg(spawner.getSpawnedType());
        if (plugin.settings().checkProtection() && probeEgg != null) {
            PlayerInteractEvent probe = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                    new ItemStack(probeEgg), block, event.getBlockFace(), EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(probe);
            if (probe.useInteractedBlock() == Event.Result.DENY || probe.useItemInHand() == Event.Result.DENY) {
                plugin.messages().send(player, "release.spawner-protected");
                return;
            }
            // 模擬事件期間方塊可能被其他插件改掉
            if (block.getType() != Material.SPAWNER) {
                return;
            }
        }
        Map<String, Component> vars = new HashMap<>();
        switch (service.extractSpawner(player, block, vars)) {
            case SUCCESS -> plugin.messages().send(player, "release.spawner-extracted", vars);
            case WORLD -> plugin.messages().send(player, "deny.world", vars);
            case BLACKLIST -> plugin.messages().send(player, "deny.blacklist", vars);
            case NO_SPAWN_EGG -> plugin.messages().send(player, "deny.no-spawn-egg", vars);
            case FAILED -> plugin.messages().send(player, "deny.error", vars);
            case EMPTY -> { }
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
