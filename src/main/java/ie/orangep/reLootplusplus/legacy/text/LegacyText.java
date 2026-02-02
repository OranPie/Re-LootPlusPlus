package ie.orangep.reLootplusplus.legacy.text;

import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public final class LegacyText {
    private LegacyText() {
    }

    public static Text fromLegacy(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new LiteralText("");
        }
        MutableText result = new LiteralText("");
        List<Formatting> formats = new ArrayList<>();
        StringBuilder segment = new StringBuilder();

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c == '\u00A7' || c == '$') && i + 1 < raw.length()) {
                appendSegment(result, segment, formats);
                segment.setLength(0);
                char code = raw.charAt(++i);
                Formatting formatting = Formatting.byCode(code);
                if (formatting == null) {
                    if (c == '$') {
                        segment.append(c).append(code);
                    }
                    continue;
                }
                if (formatting == Formatting.RESET) {
                    formats.clear();
                    continue;
                }
                if (formatting.isColor()) {
                    formats.removeIf(Formatting::isColor);
                    formats.add(formatting);
                    continue;
                }
                if (formatting.isModifier() && !formats.contains(formatting)) {
                    formats.add(formatting);
                }
                continue;
            }
            segment.append(c);
        }
        appendSegment(result, segment, formats);
        return result;
    }

    private static void appendSegment(MutableText result, StringBuilder segment, List<Formatting> formats) {
        if (segment.length() == 0) {
            return;
        }
        MutableText text = new LiteralText(segment.toString());
        if (!formats.isEmpty()) {
            text = text.formatted(formats.toArray(new Formatting[0]));
        }
        result.append(text);
    }
}
