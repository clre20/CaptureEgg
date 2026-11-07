package clre20.captureEgg.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;

/**
 * 外掛資料夾中的一個 YAML 檔（config.yml / messages.yml）。
 * 載入時與 jar 內的預設檔比對，缺少的項目自動補上並寫回，保留使用者原本的值。
 * 讀取一律有預設值防呆。
 */
public final class YamlFile {

    private final JavaPlugin plugin;
    private final String name;
    private final YamlConfiguration defaults;
    private YamlConfiguration user = new YamlConfiguration();
    private boolean broken;

    public YamlFile(JavaPlugin plugin, String name) {
        this.plugin = plugin;
        this.name = name;
        this.defaults = loadDefaults();
    }

    private YamlConfiguration loadDefaults() {
        YamlConfiguration def = new YamlConfiguration();
        try (InputStream in = plugin.getResource(name)) {
            if (in != null) {
                def.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "無法讀取內建的 " + name, e);
        }
        return def;
    }

    /** 重新讀取檔案並補齊缺少的項目；檔案格式錯誤時回傳 false（改用預設值，不覆蓋原檔）。 */
    public boolean reload() {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe(name + " 格式錯誤，暫時改用預設值（原檔不會被覆蓋）：" + e.getMessage());
            this.user = new YamlConfiguration();
            this.broken = true;
            return false;
        }
        this.broken = false;
        this.user = loaded;
        if (fillMissing()) {
            try {
                user.save(file);
                plugin.getLogger().info("已自動補上 " + name + " 缺少的項目。");
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "無法寫回 " + name, e);
            }
        }
        return true;
    }

    private boolean fillMissing() {
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (user.contains(key, true)) {
                continue;
            }
            if (defaults.isConfigurationSection(key)) {
                user.createSection(key);
            } else {
                user.set(key, defaults.get(key));
            }
            user.setComments(key, defaults.getComments(key));
            user.setInlineComments(key, defaults.getInlineComments(key));
            changed = true;
        }
        return changed;
    }

    public boolean isBroken() {
        return broken;
    }

    public String getString(String path) {
        String fallback = defaults.getString(path, "");
        return user.isString(path) ? user.getString(path, fallback) : fallback;
    }

    public List<String> getStringList(String path) {
        if (user.isList(path)) {
            return user.getStringList(path);
        }
        return defaults.getStringList(path);
    }

    public boolean getBoolean(String path) {
        boolean fallback = defaults.getBoolean(path, false);
        return user.isBoolean(path) ? user.getBoolean(path) : fallback;
    }

    public double getDouble(String path) {
        double fallback = defaults.getDouble(path, 0D);
        return (user.isDouble(path) || user.isInt(path) || user.isLong(path)) ? user.getDouble(path) : fallback;
    }
}
