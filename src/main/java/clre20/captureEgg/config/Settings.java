package clre20.captureEgg.config;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/** config.yml 解析後的快取，事件中只讀這裡，不碰檔案。 */
public record Settings(
        boolean checkProtection,
        boolean allowOthersPets,
        boolean preventBaby,
        boolean preventNamed,
        boolean dropToInventory,
        long denyCooldownMillis,
        int maxDataBytes,
        Set<EntityType> blacklist,
        boolean worldWhitelist,
        Set<String> worlds,
        boolean dispenserRelease,
        boolean captureEffects,
        boolean releaseEffects,
        boolean eggEmAllCompat,
        boolean checkOnStartup,
        boolean notifyOnJoin
) {

    public static Settings load(YamlFile file, Logger logger) {
        Set<EntityType> blacklist = EnumSet.noneOf(EntityType.class);
        for (String raw : file.getStringList("capture.entity-blacklist")) {
            EntityType type = parseEntity(raw);
            if (type == null) {
                logger.warning("config.yml 的 entity-blacklist 有無法辨識的生物：" + raw);
            } else {
                blacklist.add(type);
            }
        }
        Set<String> worlds = new HashSet<>();
        for (String w : file.getStringList("worlds.list")) {
            if (w != null && !w.isBlank()) {
                worlds.add(w.trim().toLowerCase(Locale.ROOT));
            }
        }
        int maxKb = (int) Math.max(4D, Math.min(512D, file.getDouble("capture.max-data-kb")));
        double cooldown = Math.max(0D, Math.min(60D, file.getDouble("capture.deny-message-cooldown")));
        return new Settings(
                file.getBoolean("capture.check-protection"),
                file.getBoolean("capture.allow-others-pets"),
                file.getBoolean("capture.prevent-baby"),
                file.getBoolean("capture.prevent-named"),
                "inventory".equalsIgnoreCase(file.getString("capture.drop-mode").trim()),
                (long) (cooldown * 1000D),
                maxKb * 1024,
                Collections.unmodifiableSet(blacklist),
                "whitelist".equalsIgnoreCase(file.getString("worlds.mode").trim()),
                Collections.unmodifiableSet(worlds),
                file.getBoolean("release.dispenser"),
                file.getBoolean("effects.capture"),
                file.getBoolean("effects.release"),
                file.getBoolean("compatibility.eggemall2"),
                file.getBoolean("update-checker.check-on-startup"),
                file.getBoolean("update-checker.notify-on-join")
        );
    }

    private static EntityType parseEntity(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(raw.trim().toLowerCase(Locale.ROOT));
        return key == null ? null : Registry.ENTITY_TYPE.get(key);
    }

    /** 這個世界是否允許捕捉。 */
    public boolean worldAllowed(String worldName) {
        boolean listed = worlds.contains(worldName.toLowerCase(Locale.ROOT));
        return worldWhitelist == listed;
    }
}
