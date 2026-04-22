package ie.orangep.reLootplusplus.legacy.nbt;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;

public final class LenientNbtParser {
    private LenientNbtParser() {
    }

    /**
     * Parses any NBT element (list, compound, or scalar) from a raw SNBT string.
     * Returns null on parse failure. Does not require the string to be a compound.
     * Useful for template-evaluated strings like {@code [0.1d,0.5d,-0.2d]}.
     */
    public static NbtElement parseElement(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            return StringNbtReader.parse(raw);
        } catch (CommandSyntaxException e) {
            // If full SNBT fails, try wrapping as a value inside a dummy compound
            // e.g. "[0.1d,0.2d,0.3d]" is a valid SNBT list but StringNbtReader.parse() expects compound
            try {
                NbtCompound dummy = (NbtCompound) StringNbtReader.parse("{v:" + raw + "}");
                return dummy.get("v");
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    public static NbtCompound parseOrNull(String raw, LegacyWarnReporter warnReporter, SourceLoc loc, String warnType) {
        if (raw == null || raw.isEmpty() || "{}".equals(raw)) {
            return null;
        }
        raw = sanitize(raw, warnReporter, loc, warnType);
        try {
            NbtElement element = StringNbtReader.parse(raw);
            if (element instanceof NbtCompound compound) {
                return sanitizeCompound(compound);
            }
            if (warnReporter != null) {
                warnReporter.warn(warnType, "nbt not a compound", loc);
            }
            return null;
        } catch (CommandSyntaxException e) {
            if (warnReporter != null) {
                warnReporter.warn(warnType, "nbt parse failed", loc);
            }
            return null;
        }
    }

    public static NbtCompound parseOrEmpty(String raw, LegacyWarnReporter warnReporter, SourceLoc loc, String warnType) {
        if (raw == null || raw.isEmpty() || "{}".equals(raw)) {
            return null;
        }
        raw = sanitize(raw, warnReporter, loc, warnType);
        try {
            NbtElement element = StringNbtReader.parse(raw);
            if (element instanceof NbtCompound compound) {
                return sanitizeCompound(compound);
            }
        } catch (CommandSyntaxException e) {
            if (warnReporter != null) {
                warnReporter.warn(warnType, "nbt parse failed", loc);
            }
            return new NbtCompound();
        }
        if (warnReporter != null) {
            warnReporter.warn(warnType, "nbt not a compound", loc);
        }
        return new NbtCompound();
    }

    private static NbtCompound sanitizeCompound(NbtCompound compound) {
        if (compound == null) {
            return null;
        }
        for (String key : compound.getKeys()) {
            NbtElement element = compound.get(key);
            if (element instanceof net.minecraft.nbt.NbtList list) {
                compound.put(key, sanitizeList(list));
            } else if (element instanceof NbtCompound child) {
                compound.put(key, sanitizeCompound(child));
            }
        }
        return compound;
    }

    private static net.minecraft.nbt.NbtList sanitizeList(net.minecraft.nbt.NbtList list) {
        net.minecraft.nbt.NbtList cleaned = new net.minecraft.nbt.NbtList();
        if (list == null) {
            return cleaned;
        }
        for (int i = 0; i < list.size(); i++) {
            NbtElement element = list.get(i);
            if (element == null) {
                continue;
            }
            if (element instanceof NbtCompound child) {
                cleaned.add(sanitizeCompound(child));
            } else if (element instanceof net.minecraft.nbt.NbtList childList) {
                cleaned.add(sanitizeList(childList));
            } else {
                cleaned.add(element);
            }
        }
        return cleaned;
    }

    private static String sanitize(String raw, LegacyWarnReporter warnReporter, SourceLoc loc, String warnType) {
        boolean changed = false;
        StringBuilder sb = null;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (isBadChar(c)) {
                changed = true;
                if (sb == null) {
                    sb = new StringBuilder(raw.length());
                    if (i > 0) {
                        sb.append(raw, 0, i);
                    }
                }
                continue;
            }
            if (sb != null) {
                sb.append(c);
            }
        }
        if (!changed) {
            return raw;
        }
        if (warnReporter != null) {
            warnReporter.warnOnce(warnType, "stripped invalid chars", loc);
        }
        return sb == null ? "" : sb.toString();
    }

    private static boolean isBadChar(char c) {
        int type = Character.getType(c);
        if (type == Character.FORMAT) {
            return true;
        }
        return c == '\uFEFF';
    }
}
