package clre20.captureEgg.capture;

import clre20.captureEgg.CaptureEgg;
import clre20.captureEgg.config.Messages;
import clre20.captureEgg.config.Settings;
import clre20.captureEgg.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** 捕捉與放出的核心邏輯。生物資料完整存在物品的 PDC 裡，插件本身不存任何資料檔。 */
public final class CaptureService {

    /** PDC 資料格式版本，日後格式變更時用來相容舊蛋。 */
    private static final int FORMAT = 1;
    /** 放出時的硬上限：壓縮後 1 MB、解壓後 16 MB。擋下創造模式偽造的壓縮炸彈，避免耗盡記憶體。 */
    private static final int MAX_STORED_BYTES = 1024 * 1024;
    private static final long MAX_INFLATED_BYTES = 16L * 1024L * 1024L;
    private static final DateTimeFormatter FALLBACK_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CaptureEgg plugin;
    private final NamespacedKey keyData;
    private final NamespacedKey keyType;
    private final NamespacedKey keyFormat;
    /** EggEmAll2 的捕捉蛋：生物以 SNBT 字串（EntitySnapshot）存在這個鍵。只讀取，不會產生這種蛋。 */
    private final NamespacedKey keyLegacyEggEmAll;
    private final Set<Material> spawnEggs = EnumSet.noneOf(Material.class);
    /** 拒絕提示冷卻：玩家 UUID → 下次可提示的時間（毫秒）。玩家離線時清除。 */
    private final Map<UUID, Long> denyCooldown = new HashMap<>();

    public CaptureService(CaptureEgg plugin) {
        this.plugin = plugin;
        this.keyData = new NamespacedKey(plugin, "entity");
        this.keyType = new NamespacedKey(plugin, "type");
        this.keyFormat = new NamespacedKey(plugin, "format");
        this.keyLegacyEggEmAll = new NamespacedKey("eggemall2", "eggemall_entity_data");
        for (Material m : Material.values()) {
            if (!m.isLegacy() && m.isItem() && m.name().endsWith("_SPAWN_EGG")) {
                spawnEggs.add(m);
            }
        }
    }

    private Settings settings() {
        return plugin.settings();
    }

    private Messages messages() {
        return plugin.messages();
    }

    // ------------------------------------------------------------------ 判斷

    /** 低成本判斷：物品是不是本插件的捕捉蛋。 */
    public boolean isCaptureEgg(ItemStack item) {
        if (item == null || !spawnEggs.contains(item.getType())) {
            return false;
        }
        var pdc = item.getPersistentDataContainer();
        return pdc.has(keyData, PersistentDataType.BYTE_ARRAY)
                || (settings().eggEmAllCompat() && pdc.has(keyLegacyEggEmAll, PersistentDataType.STRING));
    }

    public void forget(UUID player) {
        denyCooldown.remove(player);
    }

    public void clear() {
        denyCooldown.clear();
    }

    // ------------------------------------------------------------------ 捕捉

    /** 玩家丟的雞蛋打中生物。呼叫前已確認 target 不是玩家。 */
    public void tryCapture(Player player, LivingEntity target) {
        if (target.isDead() || !target.isValid()) {
            return;
        }
        Settings s = settings();
        Map<String, Component> vars = new HashMap<>();
        vars.put("entity", entityName(target));

        if (!player.hasPermission("captureegg.capture")) {
            deny(player, "deny.no-permission", vars);
            return;
        }
        if (!s.worldAllowed(target.getWorld().getName())) {
            deny(player, "deny.world", vars);
            return;
        }
        if (target.hasMetadata("NPC")) {
            deny(player, "deny.npc", vars);
            return;
        }
        if (s.blacklist().contains(target.getType())) {
            deny(player, "deny.blacklist", vars);
            return;
        }
        if (s.preventBaby() && target instanceof Ageable ageable && !ageable.isAdult()) {
            deny(player, "deny.baby", vars);
            return;
        }
        if (s.preventNamed() && target.customName() != null) {
            deny(player, "deny.named", vars);
            return;
        }
        if (target instanceof Tameable pet && pet.isTamed()) {
            UUID owner = pet.getOwnerUniqueId();
            if (owner != null && !owner.equals(player.getUniqueId())
                    && !s.allowOthersPets() && !player.hasPermission("captureegg.bypass.tamed")) {
                vars.put("owner", Text.plain(ownerName(owner)));
                deny(player, "deny.others-pet", vars);
                return;
            }
        }
        Material eggType = Bukkit.getItemFactory().getSpawnEgg(target.getType());
        if (eggType == null || !spawnEggs.contains(eggType)) {
            deny(player, "deny.no-spawn-egg", vars);
            return;
        }
        if (s.checkProtection() && !canAttack(player, target)) {
            deny(player, "deny.protected", vars);
            return;
        }
        // 捕捉途中被其他插件（保護事件的監聽者）移除
        if (target.isDead() || !target.isValid()) {
            return;
        }

        detach(target);

        byte[] data;
        try {
            data = Bukkit.getUnsafe().serializeEntity(target);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "無法序列化生物 " + target.getType() + "，取消捕捉", e);
            deny(player, "deny.error", vars);
            return;
        }
        if (data == null || data.length == 0) {
            deny(player, "deny.error", vars);
            return;
        }
        // 資料上限：防止背包塞滿界伏盒、捕捉蛋層層套娃，造成封包過大踢人或區塊損壞
        if (data.length > s.maxDataBytes()) {
            vars.put("size", Text.plain(Integer.toString((data.length + 1023) / 1024)));
            vars.put("max", Text.plain(Integer.toString(s.maxDataBytes() / 1024)));
            deny(player, "deny.too-large", vars);
            return;
        }

        ItemStack egg = buildEgg(eggType, target, player, data);
        Location loc = target.getLocation();
        double height = target.getHeight();
        double width = target.getWidth();
        target.remove();

        giveEgg(player, loc, egg);
        if (s.captureEffects()) {
            World w = loc.getWorld();
            Location center = loc.clone().add(0, height / 2D, 0);
            w.spawnParticle(Particle.POOF, center, 16, width / 2D, height / 2D, width / 2D, 0.02D);
            w.playSound(center, Sound.ENTITY_ITEM_PICKUP, 1F, 0.8F);
        }
        messages().send(player, "capture.success", vars);
    }

    /** 模擬「玩家攻擊這隻生物」，交給領地/保護插件判斷。 */
    @SuppressWarnings("UnstableApiUsage")
    private boolean canAttack(Player player, LivingEntity target) {
        DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK)
                .withCausingEntity(player)
                .withDirectEntity(player)
                .build();
        EntityDamageByEntityEvent check = new EntityDamageByEntityEvent(
                player, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 0D);
        Bukkit.getPluginManager().callEvent(check);
        return !check.isCancelled();
    }

    /** 捕捉前讓生物與周遭脫離關係：關閉開著的介面、乘客下來、離開載具、拴繩掉落。避免物品複製或連帶消失。 */
    private void detach(LivingEntity target) {
        if (target instanceof Merchant merchant) {
            HumanEntity trader = merchant.getTrader();
            if (trader != null) {
                trader.closeInventory();
            }
        }
        if (target instanceof InventoryHolder holder) {
            for (HumanEntity viewer : new ArrayList<>(holder.getInventory().getViewers())) {
                viewer.closeInventory();
            }
        }
        if (!target.getPassengers().isEmpty()) {
            target.eject();
        }
        if (target.isInsideVehicle()) {
            target.leaveVehicle();
        }
        if (target.isLeashed()) {
            // setLeashHolder(null) 只解開、不掉落（已實測 26.1.2 / 26.3），所以手動掉落一條，拴繩不會憑空消失
            target.setLeashHolder(null);
            target.getWorld().dropItemNaturally(target.getLocation(), new ItemStack(Material.LEAD));
        }
    }

    private ItemStack buildEgg(Material eggType, LivingEntity target, Player catcher, byte[] data) {
        Map<String, Component> vars = new HashMap<>();
        vars.put("entity", entityName(target));
        vars.put("player", Text.plain(catcher.getName()));
        vars.put("time", Text.plain(now()));
        vars.put("health", Text.plain(format(target.getHealth())));
        double max = target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                ? target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()
                : target.getHealth();
        vars.put("max_health", Text.plain(format(max)));

        List<String> templates = new ArrayList<>(messages().rawList("item.lore"));
        int insertAt = Math.min(1, templates.size());
        List<String> extra = new ArrayList<>();
        if (target instanceof Villager villager) {
            vars.put("profession", Component.translatable(villager.getProfession()));
            vars.put("level", Text.plain(Integer.toString(villager.getVillagerLevel())));
            vars.put("trades", Text.plain(Integer.toString(villager.getRecipeCount())));
            extra.addAll(messages().rawList("item.lore-villager"));
        }
        if (target instanceof Tameable pet && pet.isTamed() && pet.getOwnerUniqueId() != null) {
            vars.put("owner", Text.plain(ownerName(pet.getOwnerUniqueId())));
            extra.addAll(messages().rawList("item.lore-tamed"));
        }
        if (target instanceof Ageable ageable && !ageable.isAdult()) {
            extra.addAll(messages().rawList("item.lore-baby"));
        }
        templates.addAll(insertAt, extra);

        List<Component> lore = new ArrayList<>(templates.size());
        for (String line : templates) {
            lore.add(Text.item(line, vars));
        }

        Component customName = target.customName();
        ItemStack egg = new ItemStack(eggType);
        egg.editMeta(meta -> {
            if (customName != null) {
                meta.customName(customName.decorationIfAbsent(
                        net.kyori.adventure.text.format.TextDecoration.ITALIC,
                        net.kyori.adventure.text.format.TextDecoration.State.FALSE));
            } else {
                meta.itemName(Text.parse(messages().raw("item.name"), vars));
            }
            meta.lore(lore);
            meta.getPersistentDataContainer().set(keyData, PersistentDataType.BYTE_ARRAY, data);
            meta.getPersistentDataContainer().set(keyType, PersistentDataType.STRING, target.getType().getKey().toString());
            meta.getPersistentDataContainer().set(keyFormat, PersistentDataType.INTEGER, FORMAT);
        });
        return egg;
    }

    private void giveEgg(Player player, Location at, ItemStack egg) {
        if (settings().dropToInventory() && player.isOnline()) {
            Map<Integer, ItemStack> left = player.getInventory().addItem(egg);
            for (ItemStack rest : left.values()) {
                dropSafe(player.getLocation(), rest);
            }
            return;
        }
        dropSafe(at, egg);
    }

    /** 掉落的捕捉蛋不會被岩漿、火、仙人掌、爆炸摧毀，避免生物連同蛋一起消失。 */
    private void dropSafe(Location at, ItemStack egg) {
        Item item = at.getWorld().dropItem(at, egg);
        item.setInvulnerable(true);
    }

    // ------------------------------------------------------------------ 放出

    public enum ReleaseResult { SUCCESS, CORRUPTED, FAILED, PEACEFUL }

    /**
     * 在指定位置放出捕捉蛋裡的生物。成功才回傳 SUCCESS，呼叫端才可扣除物品。
     * 生物名稱與捕捉蛋名稱同步：蛋有名稱 → 生物用這個名稱；蛋沒有名稱 → 生物沒有名稱。
     */
    public ReleaseResult release(ItemStack egg, Location at, float yaw) {
        byte[] data = egg.getPersistentDataContainer().get(keyData, PersistentDataType.BYTE_ARRAY);
        boolean legacy = data == null;
        Entity entity;
        try {
            if (!legacy) {
                if (data.length == 0 || data.length > MAX_STORED_BYTES || !inflatedWithinLimit(data)) {
                    return ReleaseResult.CORRUPTED;
                }
                entity = Bukkit.getUnsafe().deserializeEntity(data, at.getWorld(), false, false);
            } else {
                entity = decodeEggEmAll(egg, at.getWorld());
                if (entity == null) {
                    return ReleaseResult.CORRUPTED;
                }
            }
        } catch (RuntimeException e) {
            // 和平難度下敵對生物無法建立（原版限制），不是資料損壞
            if (at.getWorld().getDifficulty() == org.bukkit.Difficulty.PEACEFUL && isHostileEgg(egg)) {
                return ReleaseResult.PEACEFUL;
            }
            // 只記一行，避免偽造的物品被拿來洗主控台與日誌
            plugin.getLogger().warning("捕捉蛋資料無法還原：" + e.getClass().getSimpleName());
            return ReleaseResult.CORRUPTED;
        }
        // 完整性檢查：只放出生物，且種類必須和蛋的種類一致（防止被竄改的物品放出其他實體）
        if (!(entity instanceof LivingEntity) || entity instanceof Player
                || Bukkit.getItemFactory().getSpawnEgg(entity.getType()) != egg.getType()) {
            return ReleaseResult.CORRUPTED;
        }
        ItemMeta meta = egg.getItemMeta();
        if (meta != null && meta.hasCustomName()) {
            entity.customName(meta.customName());
        } else if (!legacy) {
            // 本插件的蛋：蛋名永遠與生物名同步，蛋沒有名稱（例如在鐵砧清掉）就代表生物沒有名稱
            entity.customName(null);
        }
        // EggEmAll2 的蛋本來就不帶名稱 → 保留生物原本的名稱
        entity.setFallDistance(0F);
        entity.setFireTicks(0);

        Location spawnAt = at.clone();
        spawnAt.setYaw(yaw);
        spawnAt.setPitch(0F);
        if (!entity.spawnAt(spawnAt, CreatureSpawnEvent.SpawnReason.SPAWNER_EGG)) {
            return ReleaseResult.FAILED;
        }
        if (settings().releaseEffects()) {
            World w = spawnAt.getWorld();
            Location center = spawnAt.clone().add(0, entity.getHeight() / 2D, 0);
            w.spawnParticle(Particle.POOF, center, 16, entity.getWidth() / 2D, entity.getHeight() / 2D, entity.getWidth() / 2D, 0.02D);
            w.playSound(center, Sound.ENTITY_CHICKEN_EGG, 1F, 1F);
        }
        return ReleaseResult.SUCCESS;
    }

    /**
     * 還原 EggEmAll2 的捕捉蛋（SNBT 字串）。只建立實體、尚未生成到世界上。
     * 字串長度同樣受硬上限保護；種類不符等檢查交給呼叫端。
     */
    private Entity decodeEggEmAll(ItemStack egg, World world) {
        if (!settings().eggEmAllCompat()) {
            return null;
        }
        String snbt = egg.getPersistentDataContainer().get(keyLegacyEggEmAll, PersistentDataType.STRING);
        if (snbt == null || snbt.isEmpty() || snbt.length() > MAX_STORED_BYTES) {
            return null;
        }
        int oldVersion = clre20.captureEgg.compatibility.LegacySnbt.legacyDataVersion(snbt);
        if (oldVersion > 0) {
            // 1.21.5 以前存的蛋：轉成 NBT 並標上舊資料版本，交給伺服器的資料升級器（名稱、裝備等舊格式才會正確）
            byte[] data = clre20.captureEgg.compatibility.LegacySnbt.toCompressedNbt(snbt, oldVersion);
            return Bukkit.getUnsafe().deserializeEntity(data, world, false, false);
        }
        org.bukkit.entity.EntitySnapshot snapshot = Bukkit.getEntityFactory().createEntitySnapshot(snbt);
        if (Bukkit.getItemFactory().getSpawnEgg(snapshot.getEntityType()) != egg.getType()) {
            return null;
        }
        return snapshot.createEntity(world);
    }

    /** 由生怪蛋材質判斷是不是敵對生物的蛋。 */
    private static boolean isHostileEgg(ItemStack egg) {
        String eggName = egg.getType().getKey().getKey();
        if (!eggName.endsWith("_spawn_egg")) {
            return false;
        }
        org.bukkit.entity.EntityType type = org.bukkit.Registry.ENTITY_TYPE.get(
                NamespacedKey.minecraft(eggName.substring(0, eggName.length() - "_spawn_egg".length())));
        Class<?> cls = type == null ? null : type.getEntityClass();
        return cls != null && org.bukkit.entity.Enemy.class.isAssignableFrom(cls);
    }

    /** 給訊息用的生物種類名稱（依玩家語言顯示）。 */
    public Component typeName(ItemStack egg) {
        String raw = egg.getPersistentDataContainer().get(keyType, PersistentDataType.STRING);
        if (raw != null) {
            NamespacedKey key = NamespacedKey.fromString(raw);
            if (key != null) {
                org.bukkit.entity.EntityType type = org.bukkit.Registry.ENTITY_TYPE.get(key);
                if (type != null) {
                    return Component.translatable(type.translationKey());
                }
            }
        }
        // 沒有種類標記（例如 EggEmAll2 的蛋）：由生怪蛋材質推回生物種類
        String eggName = egg.getType().getKey().getKey();
        if (eggName.endsWith("_spawn_egg")) {
            org.bukkit.entity.EntityType type = org.bukkit.Registry.ENTITY_TYPE.get(
                    NamespacedKey.minecraft(eggName.substring(0, eggName.length() - "_spawn_egg".length())));
            if (type != null) {
                return Component.translatable(type.translationKey());
            }
        }
        return Component.translatable(egg.getType().translationKey());
    }

    // ------------------------------------------------------------------ 工具

    /** gzip 資料解壓後的大小是否在上限內（只計數、不保留內容）；非 gzip 資料以原始長度為準。 */
    private static boolean inflatedWithinLimit(byte[] data) {
        if (data.length < 2 || data[0] != (byte) 0x1f || data[1] != (byte) 0x8b) {
            return true;
        }
        byte[] buf = new byte[8192];
        long total = 0;
        try (java.util.zip.GZIPInputStream in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(data))) {
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_INFLATED_BYTES) {
                    return false;
                }
            }
        } catch (java.io.IOException e) {
            return false;
        }
        return true;
    }

    private Component entityName(Entity entity) {
        return Component.translatable(entity.getType().translationKey());
    }

    private void deny(Player player, String path, Map<String, Component> vars) {
        long now = System.currentTimeMillis();
        long cooldown = settings().denyCooldownMillis();
        if (cooldown > 0) {
            Long next = denyCooldown.get(player.getUniqueId());
            if (next != null && next > now) {
                return;
            }
            denyCooldown.put(player.getUniqueId(), now + cooldown);
        }
        messages().send(player, path, vars);
    }

    private String ownerName(UUID owner) {
        String name = Bukkit.getOfflinePlayer(owner).getName();
        return name != null ? name : messages().raw("item.unknown-owner");
    }

    private String now() {
        DateTimeFormatter f;
        try {
            f = DateTimeFormatter.ofPattern(messages().raw("item.time-format"), Locale.ROOT);
        } catch (IllegalArgumentException e) {
            f = FALLBACK_TIME;
        }
        return LocalDateTime.now().format(f);
    }

    private static String format(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
