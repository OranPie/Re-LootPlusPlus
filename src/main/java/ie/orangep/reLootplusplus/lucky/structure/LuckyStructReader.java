package ie.orangep.reLootplusplus.lucky.structure;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import ie.orangep.reLootplusplus.legacy.nbt.LenientNbtParser;
import net.minecraft.nbt.NbtCompound;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Reads .luckystruct text-format structure files. */
public final class LuckyStructReader {

    private LuckyStructReader() {}

    public static ParsedStructure read(InputStream stream, LegacyWarnReporter reporter, String packId) throws IOException {
        List<String> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            lines = reader.lines().collect(Collectors.toList());
        }

        int width = 1, height = 1, length = 1;
        List<StructureBlock> blocks = new ArrayList<>();

        String section = null;
        int lineNum = 0;
        for (String rawLine : lines) {
            lineNum++;
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("//")) continue;
            if (line.startsWith(">")) {
                section = line.substring(1).trim().toLowerCase();
                continue;
            }
            if ("properties".equals(section)) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String key = line.substring(0, eq).trim().toLowerCase();
                    String val = line.substring(eq + 1).trim();
                    switch (key) {
                        case "width" -> { try { width = Integer.parseInt(val); } catch (NumberFormatException ignored) {} }
                        case "height" -> { try { height = Integer.parseInt(val); } catch (NumberFormatException ignored) {} }
                        case "length" -> { try { length = Integer.parseInt(val); } catch (NumberFormatException ignored) {} }
                    }
                }
            } else if ("blocks".equals(section)) {
                SourceLoc loc = new SourceLoc(packId, packId, ".luckystruct", lineNum, rawLine);
                parseBlockLine(line, blocks, reporter, loc);
            }
        }

        return new ParsedStructure(width, height, length, Collections.unmodifiableList(blocks));
    }

    private static void parseBlockLine(String line, List<StructureBlock> blocks,
                                        LegacyWarnReporter reporter, SourceLoc loc) {
        if (line.isEmpty()) return;

        // Split off NBT part: find opening '(' not inside quotes
        String nbtPart = null;
        String mainPart = line;
        int parenIdx = line.indexOf('(');
        if (parenIdx > 0) {
            mainPart = line.substring(0, parenIdx).trim();
            int endParen = line.lastIndexOf(')');
            if (endParen > parenIdx) {
                nbtPart = line.substring(parenIdx + 1, endParen);
            }
        }
        // Remove trailing commas
        mainPart = mainPart.replaceAll(",+$", "").trim();

        String[] parts = mainPart.split(",", -1);
        if (parts.length < 4) return;

        int x, y, z;
        try {
            x = Integer.parseInt(parts[0].trim());
            y = Integer.parseInt(parts[1].trim());
            z = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException e) {
            reporter.warn("LuckyStruct", "invalid coords in line: " + line, loc);
            return;
        }

        String rawBlockId = parts[3].trim();
        if (rawBlockId.isEmpty()) return;

        // Normalize block ID — add minecraft: namespace if not present
        if (!rawBlockId.contains(":")) {
            rawBlockId = "minecraft:" + rawBlockId;
        }

        // Parse NBT if present
        NbtCompound nbt = null;
        if (nbtPart != null && !nbtPart.isBlank()) {
            // Convert Lucky NBT attribute format key=val,key=val to SNBT {key:val,key:val}
            String snbt = luckyAttrToSnbt(nbtPart.trim());
            if (snbt != null) {
                nbt = LenientNbtParser.parseOrNull(snbt, reporter, loc, "LuckyStructNBT");
            }
        }

        blocks.add(new StructureBlock(x, y, z, rawBlockId, Map.of(), nbt));
    }

    /**
     * Converts Lucky attribute format like {@code Luck=75,Color=1} to SNBT like {@code {Luck:75,Color:1}}.
     * Very simple conversion — handles integer values, string values (quoted), and boolean values.
     */
    private static String luckyAttrToSnbt(String luckyAttrs) {
        if (luckyAttrs == null || luckyAttrs.isBlank()) return null;
        // If it already looks like SNBT, return as-is
        if (luckyAttrs.startsWith("{")) return luckyAttrs;

        StringBuilder sb = new StringBuilder("{");
        // Split on commas not inside quotes or parens
        String[] pairs = luckyAttrs.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        boolean first = true;
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String key = pair.substring(0, eq).trim();
            String val = pair.substring(eq + 1).trim();
            if (key.isEmpty()) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append(key).append(':');
            // Try integer
            try {
                Integer.parseInt(val);
                sb.append(val);
                continue;
            } catch (NumberFormatException ignored) {}
            // Try float
            try {
                Float.parseFloat(val);
                sb.append(val).append('f');
                continue;
            } catch (NumberFormatException ignored) {}
            // String value
            if (!val.startsWith("\"")) {
                val = "\"" + val + "\"";
            }
            sb.append(val);
        }
        sb.append('}');
        return sb.toString();
    }
}
