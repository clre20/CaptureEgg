package clre20.captureEgg.compatibility;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * 判斷生物是不是 MythicMobs 生物。不需要編譯期依賴：
 * 1. MythicMobs 有啟用時，透過反射呼叫官方 API（BukkitAPIHelper#isMythicMob），只在 init 時解析一次方法。
 * 2. API 無法使用時，改看實體 PDC 是否有 mythicmobs:type 標籤。
 */
public final class MythicMobsHook {

    /** MythicMobs 生物身上記錄生物 ID 的標籤，只有真正的 MythicMobs 生物才有。 */
    private static final NamespacedKey TYPE_KEY = new NamespacedKey("mythicmobs", "type");

    private Object apiHelper;
    private Method isMythicMob;

    /** 啟動與重載時呼叫，重新解析 MythicMobs API。 */
    public void init(Logger logger) {
        apiHelper = null;
        isMythicMob = null;
        Plugin mm = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (mm == null || !mm.isEnabled()) {
            return;
        }
        try {
            Class<?> bukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit", true, mm.getClass().getClassLoader());
            Object inst = bukkit.getMethod("inst").invoke(null);
            Object helper = bukkit.getMethod("getAPIHelper").invoke(inst);
            Method m = helper.getClass().getMethod("isMythicMob", Entity.class);
            apiHelper = helper;
            isMythicMob = m;
            logger.info("已連結 MythicMobs，MythicMobs 生物無法被捕捉。");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            logger.warning("無法連結 MythicMobs API（" + e.getClass().getSimpleName() + "），改用實體標籤判斷 MythicMobs 生物。");
        }
    }

    public void clear() {
        apiHelper = null;
        isMythicMob = null;
    }

    public boolean isMythicMob(Entity entity) {
        Method m = isMythicMob;
        if (m != null) {
            // 有連上 API 就以 API 為準：ScaleVanillaMobs 會在一般原版生物身上留下 mythicmobs 等級標籤，不能只看標籤
            try {
                return Boolean.TRUE.equals(m.invoke(apiHelper, entity));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                // API 呼叫失敗時退回標籤判斷
            }
        }
        return entity.getPersistentDataContainer().getKeys().contains(TYPE_KEY);
    }
}
