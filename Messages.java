package clre20.captureEgg.config;

import clre20.captureEgg.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

/** messages.yml 的讀取與發送。 */
public final class Messages {

    private final YamlFile file;

    public Messages(YamlFile file) {
        this.file = file;
    }

    public String raw(String path) {
        return file.getString(path);
    }

    public List<String> rawList(String path) {
        return file.getStringList(path);
    }

    /** 帶前綴發送；訊息為空字串時不發送。 */
    public void send(CommandSender to, String path, Map<String, ? extends Component> vars) {
        String template = file.getString(path);
        if (template.isEmpty()) {
            return;
        }
        to.sendMessage(Text.parse(file.getString("prefix"), null).append(Text.parse(template, vars)));
    }

    public void send(CommandSender to, String path) {
        send(to, path, Map.of());
    }

    /** 不帶前綴發送（說明列表等）。 */
    public void sendLine(CommandSender to, String template, Map<String, ? extends Component> vars) {
        if (template == null || template.isEmpty()) {
            return;
        }
        to.sendMessage(Text.parse(template, vars));
    }

    /** 主控台用的純文字。 */
    public String plain(String path, Map<String, ? extends Component> vars) {
        return Text.toPlain(Text.parse(file.getString(path), vars));
    }
}
