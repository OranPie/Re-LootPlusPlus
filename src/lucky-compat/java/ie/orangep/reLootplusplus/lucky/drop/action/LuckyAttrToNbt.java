package ie.orangep.reLootplusplus.lucky.drop.action;

import ie.orangep.reLootplusplus.lucky.attr.LuckyAttr;
import ie.orangep.reLootplusplus.lucky.template.LuckyTemplateVars;
import net.minecraft.nbt.*;

/**
 * Converts a parsed {@link LuckyAttr} tree (DictAttr / ListAttr / StringAttr) into
 * the corresponding Minecraft {@link NbtElement} hierarchy.
 *
 * <p>StringAttr values undergo type inference:
 * <ul>
 *   <li>Numeric suffixes: {@code 1b/1B} → byte, {@code 1s/1S} → short,
 *       {@code 1l/1L} → long, {@code 1f/1F} → float, {@code 1d/1D} → double</li>
 *   <li>Bare integers → {@link NbtInt}</li>
 *   <li>Bare floats → {@link NbtFloat}</li>
 *   <li>{@code true}/{@code false} → {@link NbtByte} 1/0</li>
 *   <li>Anything else → {@link NbtString}</li>
 * </ul>
 */
public final class LuckyAttrToNbt {

    private LuckyAttrToNbt() {}

    /**
     * Converts a {@link LuckyAttr.DictAttr} to a {@link NbtCompound}.
     * Template variables in string values are evaluated with the provided context.
     */
    public static NbtCompound dictToCompound(LuckyAttr.DictAttr dict, LuckyTemplateVars.EvalContext evalCtx) {
        NbtCompound compound = new NbtCompound();
        for (var entry : dict.entries().entrySet()) {
            String key = entry.getKey();
            NbtElement elem = toElement(entry.getValue(), evalCtx);
            if (elem != null) compound.put(key, elem);
        }
        return compound;
    }

    /**
     * Converts any {@link LuckyAttr} to an {@link NbtElement}, or {@code null} if empty/unknown.
     * After template evaluation, string values that look like {@code [...]}, {@code {...}} are
     * re-parsed as NBT lists/compounds rather than kept as strings.
     */
    public static NbtElement toElement(LuckyAttr attr, LuckyTemplateVars.EvalContext evalCtx) {
        if (attr instanceof LuckyAttr.DictAttr d) {
            return dictToCompound(d, evalCtx);
        } else if (attr instanceof LuckyAttr.ListAttr l) {
            return listToNbt(l, evalCtx);
        } else if (attr instanceof LuckyAttr.StringAttr s) {
            String val = evalCtx != null ? LuckyTemplateVars.evaluate(s.value(), evalCtx) : s.value();
            // After template substitution a value like "[0.1d,0.5d,-0.2d]" or "{Fuse:5b}" may appear;
            // try to parse those as NBT before falling back to type inference.
            if (val != null && !val.isEmpty()) {
                char first = val.charAt(0);
                if (first == '[' || first == '{') {
                    NbtElement parsed = ie.orangep.reLootplusplus.legacy.nbt.LenientNbtParser.parseElement(val);
                    if (parsed != null) return parsed;
                }
            }
            return inferType(val);
        }
        return null;
    }

    /**
     * Converts the NBTTag attribute of a drop line to a NbtCompound.
     * Handles both StringAttr (raw SNBT-like text) and DictAttr (structured) forms.
     * Returns null if the attribute is absent or empty.
     */
    public static NbtCompound resolveNbtTag(ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine drop,
                                             LuckyTemplateVars.EvalContext evalCtx) {
        LuckyAttr nbtAttr = drop.attrs().get("NBTTag");
        if (nbtAttr == null) nbtAttr = drop.attrs().get("nbtTag");
        if (nbtAttr == null) {
            for (var e : drop.attrs().entrySet()) {
                if (e.getKey().equalsIgnoreCase("NBTTag")) { nbtAttr = e.getValue(); break; }
            }
        }
        if (nbtAttr == null) return null;

        if (nbtAttr instanceof LuckyAttr.DictAttr d) {
            NbtCompound result = dictToCompound(d, evalCtx);
            return result.isEmpty() ? null : result;
        } else if (nbtAttr instanceof LuckyAttr.StringAttr s) {
            // Legacy: raw SNBT or Lucky-style string
            String raw = evalCtx != null ? LuckyTemplateVars.evaluate(s.value(), evalCtx) : s.value();
            if (raw == null || raw.isBlank()) return null;
            return ie.orangep.reLootplusplus.legacy.nbt.LenientNbtParser.parseOrEmpty(
                raw, null, null, "LuckyNBT"
            );
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private static NbtList listToNbt(LuckyAttr.ListAttr listAttr, LuckyTemplateVars.EvalContext evalCtx) {
        NbtList list = new NbtList();
        for (LuckyAttr item : listAttr.items()) {
            NbtElement elem = toElement(item, evalCtx);
            if (elem != null) {
                try { list.add(elem); } catch (UnsupportedOperationException ignored) {}
            }
        }
        return list;
    }

    /** Infers the most specific NbtElement type from a raw string. */
    private static NbtElement inferType(String val) {
        if (val == null || val.isEmpty()) return NbtString.of("");

        // Boolean
        if ("true".equalsIgnoreCase(val)) return NbtByte.of((byte) 1);
        if ("false".equalsIgnoreCase(val)) return NbtByte.of((byte) 0);

        // Suffixed numeric types
        if (val.length() >= 2) {
            char suffix = val.charAt(val.length() - 1);
            String body = val.substring(0, val.length() - 1);
            switch (Character.toLowerCase(suffix)) {
                case 'b':
                    try { return NbtByte.of(Byte.parseByte(body)); } catch (NumberFormatException ignored) {}
                    break;
                case 's':
                    try { return NbtShort.of(Short.parseShort(body)); } catch (NumberFormatException ignored) {}
                    break;
                case 'l':
                    try { return NbtLong.of(Long.parseLong(body)); } catch (NumberFormatException ignored) {}
                    break;
                case 'f':
                    try { return NbtFloat.of(Float.parseFloat(body)); } catch (NumberFormatException ignored) {}
                    break;
                case 'd':
                    try { return NbtDouble.of(Double.parseDouble(body)); } catch (NumberFormatException ignored) {}
                    break;
            }
        }

        // Bare integer
        try { return NbtInt.of(Integer.parseInt(val)); } catch (NumberFormatException ignored) {}

        // Bare float
        try { return NbtFloat.of(Float.parseFloat(val)); } catch (NumberFormatException ignored) {}

        // Arithmetic expression produced by template evaluation (e.g., "3+16384", "100-5")
        // Only attempt when we see a digit+operator+digit pattern to avoid touching strings
        if (val.matches(".*[0-9][+\\-][0-9].*")) {
            double arith = ie.orangep.reLootplusplus.lucky.template.LuckyTemplateVars.evalArithmetic(val, Double.NaN);
            if (!Double.isNaN(arith)) {
                long asLong = (long) arith;
                if (arith == asLong) {
                    if (asLong >= Integer.MIN_VALUE && asLong <= Integer.MAX_VALUE)
                        return NbtInt.of((int) asLong);
                    return NbtLong.of(asLong);
                }
                return NbtFloat.of((float) arith);
            }
        }

        // Fall back to string
        return NbtString.of(val);
    }
}
