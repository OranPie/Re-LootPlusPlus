package ie.orangep.reLootplusplus.legacy.mapping;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.legacy.text.LegacyText;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.Locale;

public final class LegacyItemNbtFixer {
    private LegacyItemNbtFixer() {
    }

    public static void fixItemStack(NbtCompound item, LegacyWarnReporter reporter, String context) {
        if (item == null) {
            return;
        }
        Log.trace("Legacy", "ItemNbt: fix stack context={}", context);
        NbtCompound tag = item;
        if (item.contains("tag", NbtElement.COMPOUND_TYPE)) {
            tag = item.getCompound("tag");
        }
        fixDisplay(tag, reporter, context);
        fixEnchantments(tag, reporter, context);
        fixAttributeModifiers(tag, reporter, context);
        fixCustomPotionEffects(tag, reporter, context);
    }

    public static String normalizeAttributeId(String raw, LegacyWarnReporter reporter, String context) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return raw;
        }
        String namespace;
        String path;
        int idx = trimmed.indexOf(':');
        if (idx > 0) {
            namespace = trimmed.substring(0, idx);
            path = trimmed.substring(idx + 1);
        } else if (trimmed.startsWith("generic.")) {
            namespace = "minecraft";
            path = "generic." + toSnakeCase(trimmed.substring("generic.".length()));
        } else if (trimmed.startsWith("potioncore.")) {
            namespace = resolvePotioncoreNamespace();
            path = toSnakeCase(trimmed.substring("potioncore.".length()));
            warn(reporter, "LegacyNamespace", "mapped potioncore -> " + namespace + " for '" + trimmed + "' -> '" + namespace + ":" + path + "'" + formatContext(context));
        } else {
            namespace = "minecraft";
            path = toSnakeCase(trimmed);
        }
        if ("minecraft".equalsIgnoreCase(namespace) && path.startsWith("generic.")) {
            String suffix = path.substring("generic.".length());
            path = "generic." + toSnakeCase(suffix);
        }
        if ("potioncore".equalsIgnoreCase(namespace)) {
            namespace = resolvePotioncoreNamespace();
        }
        String normalized = namespace.toLowerCase(Locale.ROOT) + ":" + sanitizePath(path);
        if (Identifier.tryParse(normalized) == null) {
            return trimmed;
        }
        if (!normalized.equals(trimmed)) {
            warn(reporter, "LegacyAttribute", "mapped '" + trimmed + "' -> '" + normalized + "'" + formatContext(context));
            Log.trace("Legacy", "AttributeId: {} → {}", trimmed, normalized);
        }
        return normalized;
    }

    private static void fixDisplay(NbtCompound tag, LegacyWarnReporter reporter, String context) {
        if (tag == null || !tag.contains("display", NbtElement.COMPOUND_TYPE)) {
            return;
        }
        NbtCompound display = tag.getCompound("display");
        if (display.contains("Name", NbtElement.STRING_TYPE)) {
            String raw = display.getString("Name");
            if (raw != null && !raw.isEmpty() && !raw.trim().startsWith("{")) {
                String json = Text.Serializer.toJson(LegacyText.fromLegacy(raw));
                display.putString("Name", json);
                warn(reporter, "LegacyText", "converted item display name '" + raw + "' to json" + formatContext(context));
            }
        }
        if (display.contains("Lore", NbtElement.LIST_TYPE)) {
            NbtList lore = display.getList("Lore", NbtElement.STRING_TYPE);
            int converted = 0;
            for (int i = 0; i < lore.size(); i++) {
                String raw = lore.getString(i);
                if (raw == null || raw.isEmpty() || raw.trim().startsWith("{")) {
                    continue;
                }
                String json = Text.Serializer.toJson(LegacyText.fromLegacy(raw));
                lore.set(i, NbtString.of(json));
                converted++;
            }
            if (converted > 0) {
                warn(reporter, "LegacyText", "converted item lore (" + converted + ") to json" + formatContext(context));
            }
        }
    }

    private static void fixEnchantments(NbtCompound tag, LegacyWarnReporter reporter, String context) {
        if (tag == null) {
            return;
        }
        if (tag.contains("ench", NbtElement.LIST_TYPE)) {
            NbtList legacy = tag.getList("ench", NbtElement.COMPOUND_TYPE);
            NbtList modern = convertEnchants(legacy, reporter, context);
            if (!modern.isEmpty()) {
                if (tag.contains("Enchantments", NbtElement.LIST_TYPE)) {
                    NbtList merged = tag.getList("Enchantments", NbtElement.COMPOUND_TYPE);
                    merged.addAll(modern);
                    tag.put("Enchantments", merged);
                } else {
                    tag.put("Enchantments", modern);
                }
                warn(reporter, "LegacyEnchant", "converted ench -> Enchantments" + formatContext(context));
            }
            tag.remove("ench");
        } else if (tag.contains("Enchantments", NbtElement.LIST_TYPE)) {
            NbtList list = tag.getList("Enchantments", NbtElement.COMPOUND_TYPE);
            NbtList fixed = convertEnchants(list, reporter, context);
            if (!fixed.equals(list)) {
                tag.put("Enchantments", fixed);
            }
        }
        if (tag.contains("StoredEnchantments", NbtElement.LIST_TYPE)) {
            NbtList list = tag.getList("StoredEnchantments", NbtElement.COMPOUND_TYPE);
            NbtList fixed = convertEnchants(list, reporter, context);
            if (!fixed.equals(list)) {
                tag.put("StoredEnchantments", fixed);
            }
        }
    }

    private static NbtList convertEnchants(NbtList legacy, LegacyWarnReporter reporter, String context) {
        NbtList modern = new NbtList();
        if (legacy == null) {
            return modern;
        }
        for (int i = 0; i < legacy.size(); i++) {
            NbtCompound entry = legacy.getCompound(i);
            if (entry == null) {
                continue;
            }
            String id = null;
            if (entry.contains("id", NbtElement.STRING_TYPE)) {
                id = entry.getString("id");
            } else if (entry.contains("id", NbtElement.NUMBER_TYPE)) {
                id = Integer.toString(entry.getInt("id"));
            }
            if (id == null || id.isEmpty()) {
                continue;
            }
            String mapped = resolveEnchantmentId(id, reporter, context);
            if (mapped == null || mapped.isEmpty()) {
                continue;
            }
            int lvl = 1;
            if (entry.contains("lvl", NbtElement.NUMBER_TYPE)) {
                lvl = entry.getInt("lvl");
            } else if (entry.contains("lvl", NbtElement.STRING_TYPE)) {
                try {
                    lvl = Integer.parseInt(entry.getString("lvl"));
                } catch (NumberFormatException ignored) {
                    lvl = 1;
                }
            }
            NbtCompound out = new NbtCompound();
            out.putString("id", mapped);
            out.putShort("lvl", (short) lvl);
            modern.add(out);
        }
        return modern;
    }

    private static String resolveEnchantmentId(String id, LegacyWarnReporter reporter, String context) {
        try {
            return LegacyEnchantmentIdMapper.resolve(id, reporter, null);
        } catch (NoClassDefFoundError e) {
            String mapped = fallbackEnchantmentId(id);
            if (mapped == null) {
                warn(reporter, "LegacyEnchant", "unknown numeric id " + id + formatContext(context));
                return null;
            }
            warn(reporter, "LegacyEnchant",
                "numeric id " + id + " -> " + mapped + " (inline fallback)" + formatContext(context));
            return mapped;
        }
    }

    private static String fallbackEnchantmentId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        if (!isNumeric(trimmed)) {
            return trimmed.contains(":") ? trimmed.toLowerCase(Locale.ROOT) : "minecraft:" + trimmed.toLowerCase(Locale.ROOT);
        }
        return switch (Integer.parseInt(trimmed)) {
            case 0 -> "minecraft:protection";
            case 1 -> "minecraft:fire_protection";
            case 2 -> "minecraft:feather_falling";
            case 3 -> "minecraft:blast_protection";
            case 4 -> "minecraft:projectile_protection";
            case 5 -> "minecraft:respiration";
            case 6 -> "minecraft:aqua_affinity";
            case 7 -> "minecraft:thorns";
            case 8 -> "minecraft:depth_strider";
            case 9 -> "minecraft:frost_walker";
            case 10 -> "minecraft:binding_curse";
            case 16 -> "minecraft:sharpness";
            case 17 -> "minecraft:smite";
            case 18 -> "minecraft:bane_of_arthropods";
            case 19 -> "minecraft:knockback";
            case 20 -> "minecraft:fire_aspect";
            case 21 -> "minecraft:looting";
            case 32 -> "minecraft:efficiency";
            case 33 -> "minecraft:silk_touch";
            case 34 -> "minecraft:unbreaking";
            case 35 -> "minecraft:fortune";
            case 48 -> "minecraft:power";
            case 49 -> "minecraft:punch";
            case 50 -> "minecraft:flame";
            case 51 -> "minecraft:infinity";
            case 61 -> "minecraft:luck_of_the_sea";
            case 62 -> "minecraft:lure";
            case 70 -> "minecraft:mending";
            case 71 -> "minecraft:vanishing_curse";
            default -> null;
        };
    }

    private static boolean isNumeric(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private static void fixAttributeModifiers(NbtCompound tag, LegacyWarnReporter reporter, String context) {
        if (tag == null || !tag.contains("AttributeModifiers", NbtElement.LIST_TYPE)) {
            return;
        }
        NbtElement rawList = tag.get("AttributeModifiers");
        if (!(rawList instanceof NbtList list)) {
            return;
        }
        if (list.getHeldType() != NbtElement.COMPOUND_TYPE) {
            tag.remove("AttributeModifiers");
            warn(reporter, "LegacyNBT", "removed AttributeModifiers with invalid list type" + formatContext(context));
            return;
        }
        if (list.isEmpty()) {
            return;
        }
        NbtList fixed = new NbtList();
        boolean changed = false;
        for (int i = 0; i < list.size(); i++) {
            NbtElement element = list.get(i);
            if (element == null) {
                changed = true;
                continue;
            }
            if (!(element instanceof NbtCompound entry)) {
                changed = true;
                continue;
            }
            if (!entry.contains("UUID", NbtElement.INT_ARRAY_TYPE)) {
                if (entry.contains("UUIDLeast", NbtElement.NUMBER_TYPE) && entry.contains("UUIDMost", NbtElement.NUMBER_TYPE)) {
                    long least = entry.getLong("UUIDLeast");
                    long most = entry.getLong("UUIDMost");
                    entry.putIntArray("UUID", toIntArray(most, least));
                    changed = true;
                }
            }
            if (entry.contains("AttributeName", NbtElement.STRING_TYPE)) {
                String raw = entry.getString("AttributeName");
                String normalized = normalizeAttributeId(raw, reporter, context);
                if (normalized != null && !normalized.equals(raw)) {
                    entry.putString("AttributeName", normalized);
                }
            }
            fixed.add(entry);
        }
        if (changed) {
            if (fixed.isEmpty()) {
                tag.remove("AttributeModifiers");
            } else {
                tag.put("AttributeModifiers", fixed);
            }
        }
    }

    private static void fixCustomPotionEffects(NbtCompound tag, LegacyWarnReporter reporter, String context) {
        if (tag == null || !tag.contains("CustomPotionEffects", NbtElement.LIST_TYPE)) {
            return;
        }
        NbtList effects = tag.getList("CustomPotionEffects", NbtElement.COMPOUND_TYPE);
        int converted = 0;
        for (int i = 0; i < effects.size(); i++) {
            NbtCompound effect = effects.getCompound(i);
            if (!effect.contains("Id", NbtElement.STRING_TYPE)) {
                continue;
            }
            String raw = effect.getString("Id");
            Identifier mapped = LegacyEffectIdMapper.resolve(raw, reporter, null);
            if (mapped == null || !Registry.STATUS_EFFECT.containsId(mapped)) {
                warn(reporter, "LegacyPotionEffect",
                    "unknown custom potion effect '" + raw + "'" + formatContext(context));
                continue;
            }
            int rawId = Registry.STATUS_EFFECT.getRawId(Registry.STATUS_EFFECT.get(mapped));
            if (rawId < 0) {
                continue;
            }
            effect.putByte("Id", (byte) rawId);
            converted++;
        }
        if (converted > 0) {
            warn(reporter, "LegacyPotionEffect",
                "converted CustomPotionEffects string ids (" + converted + ")" + formatContext(context));
        }
    }

    private static int[] toIntArray(long most, long least) {
        return new int[] {
            (int) (most >> 32),
            (int) most,
            (int) (least >> 32),
            (int) least
        };
    }

    private static String resolvePotioncoreNamespace() {
        var config = RuntimeState.config();
        if (config == null || config.potioncoreNamespace == null || config.potioncoreNamespace.isBlank()) {
            return "potioncore";
        }
        return config.potioncoreNamespace.trim().toLowerCase(Locale.ROOT);
    }

    private static String sanitizePath(String path) {
        StringBuilder out = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '/' || c == '.' || c == '_' || c == '-') {
                out.append(c);
            } else if (Character.isUpperCase(c)) {
                out.append(Character.toLowerCase(c));
            } else {
                out.append('_');
            }
        }
        return out.toString();
    }

    private static String toSnakeCase(String input) {
        StringBuilder out = new StringBuilder();
        char prev = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0 && (Character.isLowerCase(prev) || Character.isDigit(prev))) {
                    out.append('_');
                }
                out.append(Character.toLowerCase(c));
            } else if (c == ' ' || c == '-') {
                out.append('_');
            } else {
                out.append(Character.toLowerCase(c));
            }
            prev = c;
        }
        return out.toString();
    }

    private static void warn(LegacyWarnReporter reporter, String type, String detail) {
        if (reporter != null) {
            reporter.warnOnce(type, detail, null);
        }
    }

    private static String formatContext(String context) {
        return context == null || context.isEmpty() ? "" : " (" + context + ")";
    }
}
