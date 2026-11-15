package clre20.captureEgg.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;

/**
 * 文字工具：把 messages.yml 的 &amp; 色碼字串轉成 Component，並替換 {變數}。
 * 變數值一律以 Component 插入、不再解析色碼，玩家可控的名稱無法注入格式。
 */
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private Text() {
    }

    public static Component parse(String template, Map<String, ? extends Component> vars) {
        Component out = LEGACY.deserialize(template == null ? "" : template);
        if (vars == null || vars.isEmpty() || template == null || template.indexOf('{') < 0) {
            return out;
        }
        for (Map.Entry<String, ? extends Component> e : vars.entrySet()) {
            String token = "{" + e.getKey() + "}";
            if (template.contains(token)) {
                out = out.replaceText(TextReplacementConfig.builder()
                        .matchLiteral(token)
                        .replacement(e.getValue())
                        .build());
            }
        }
        return out;
    }

    /** 物品名稱 / Lore 用：預設取消斜體。 */
    public static Component item(String template, Map<String, ? extends Component> vars) {
        return parse(template, vars).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static Component plain(String value) {
        return Component.text(value == null ? "" : value);
    }

    public static String toPlain(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }
}
