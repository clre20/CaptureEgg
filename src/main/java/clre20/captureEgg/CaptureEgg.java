package clre20.captureEgg;

import clre20.captureEgg.capture.CaptureService;
import clre20.captureEgg.command.CaptureEggCommand;
import clre20.captureEgg.config.Messages;
import clre20.captureEgg.config.Settings;
import clre20.captureEgg.config.YamlFile;
import clre20.captureEgg.listener.CaptureListener;
import clre20.captureEgg.listener.PlayerListener;
import clre20.captureEgg.listener.ReleaseListener;
import clre20.captureEgg.update.UpdateChecker;
import net.kyori.adventure.text.Component;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public final class CaptureEgg extends JavaPlugin {

    private YamlFile configFile;
    private YamlFile messagesFile;
    private volatile Settings settings;
    private Messages messages;
    private CaptureService captureService;
    private UpdateChecker updateChecker;
    private PlayerListener playerListener;

    @Override
    public void onEnable() {
        configFile = new YamlFile(this, "config.yml");
        messagesFile = new YamlFile(this, "messages.yml");
        messages = new Messages(messagesFile);
        reloadAll();

        captureService = new CaptureService(this);
        updateChecker = new UpdateChecker(this);
        playerListener = new PlayerListener(this);

        getServer().getPluginManager().registerEvents(new CaptureListener(captureService), this);
        getServer().getPluginManager().registerEvents(new ReleaseListener(this, captureService), this);
        getServer().getPluginManager().registerEvents(playerListener, this);

        PluginCommand cmd = getCommand("captureegg");
        if (cmd != null) {
            CaptureEggCommand executor = new CaptureEggCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        if (settings.checkOnStartup()) {
            updateChecker.check(result -> {
                String path = switch (result.status()) {
                    case NEW_VERSION -> "update.console-new-version";
                    case UP_TO_DATE -> "update.console-up-to-date";
                    case DEV_BUILD -> "update.console-dev-build";
                    case NO_RELEASE -> "update.console-no-release";
                    case FAILED -> "update.console-failed";
                };
                String line = messages.plain(path, updateVars(result));
                if (result.status() == UpdateChecker.Status.NEW_VERSION || result.status() == UpdateChecker.Status.FAILED) {
                    getLogger().warning(line);
                } else {
                    getLogger().info(line);
                }
            });
        }
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        if (playerListener != null) {
            playerListener.clear();
        }
        if (captureService != null) {
            captureService.clear();
        }
        if (updateChecker != null) {
            updateChecker.shutdown();
        }
    }

    /** 重新讀取並補齊 config.yml 與 messages.yml。任一檔案格式錯誤時回傳 false（該檔改用預設值）。 */
    public boolean reloadAll() {
        boolean okConfig = configFile.reload();
        boolean okMessages = messagesFile.reload();
        settings = Settings.load(configFile, getLogger());
        return okConfig && okMessages;
    }

    public Map<String, Component> updateVars(UpdateChecker.Result r) {
        Map<String, Component> vars = new HashMap<>();
        vars.put("current", Component.text(r.current() == null ? "?" : r.current()));
        vars.put("latest", Component.text(r.latest() == null ? "?" : r.latest()));
        vars.put("url", Component.text(r.url() == null ? "" : r.url()));
        vars.put("error", Component.text(r.error() == null ? "" : r.error()));
        return vars;
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public CaptureService captureService() {
        return captureService;
    }

    public UpdateChecker updateChecker() {
        return updateChecker;
    }
}
