package ie.orangep.reLootplusplus.legacy.mapping;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.legacy.text.LegacyText;
import ie.orangep.reLootplusplus.legacy.mapping.LegacyItemNbtFixer;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import ie.orangep.reLootplusplus.config.CustomRemapStore;
import ie.orangep.reLootplusplus.config.ReLootPlusPlusConfig;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class LegacyEntityIdFixer {
    private static final Map<String, String> LEGACY_MAP = new HashMap<>();
    private static final String[] DYE_COLORS = new String[] {
        "black", "red", "green", "brown", "blue", "purple", "cyan", "light_gray",
        "gray", "pink", "lime", "yellow", "light_blue", "magenta", "orange", "white"
    };

    static {
        register("Item", "minecraft:item");
        register("FallingSand", "minecraft:falling_block");
        register("PrimedTnt", "minecraft:tnt");
        register("FireworksRocketEntity", "minecraft:firework_rocket");
        register("fireworks_rocket", "minecraft:firework_rocket");
        register("minecraft:fireworks_rocket", "minecraft:firework_rocket");
        register("WitherBoss", "minecraft:wither");
        register("SnowMan", "minecraft:snow_golem");
        register("VillagerGolem", "minecraft:iron_golem");
        register("PigZombie", "minecraft:zombified_piglin");
        register("LavaSlime", "minecraft:magma_cube");
        register("EnderCrystal", "minecraft:end_crystal");
        register("XPOrb", "minecraft:experience_orb");
        register("ThrownExpBottle", "minecraft:experience_bottle");
        register("ThrownPotion", "minecraft:potion");
        register("ThrownEgg", "minecraft:egg");
        register("ThrownSnowball", "minecraft:snowball");
        register("LightningBolt", "minecraft:lightning_bolt");
        register("ArmorStand", "minecraft:armor_stand");
        register("EnderDragon", "minecraft:ender_dragon");
        register("CaveSpider", "minecraft:cave_spider");
        register("MushroomCow", "minecraft:mooshroom");
        register("Ozelot", "minecraft:ocelot");
        register("lootplusplus.ThrownItem", "re-lootplusplus:loot_thrown");
        register("lootplusplus:thrown_item", "re-lootplusplus:loot_thrown");
        register("potioncore.CustomPotion", "potioncore:custom_potion");
        register("LuckyProjectile", "lucky:lucky_projectile");
        register("luckyprojectile", "lucky:lucky_projectile");
        register("lucky.LuckyProjectile", "lucky:lucky_projectile");
    }

    private LegacyEntityIdFixer() {
    }

    public static String normalizeEntityId(String raw, LegacyWarnReporter reporter, String context) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        String mapped = mapLegacyEntityId(raw, new NbtCompound(), reporter, context);
        return mapped == null ? raw : mapped;
    }

    public static boolean fixEntityId(NbtCompound nbt, LegacyWarnReporter reporter, String context) {
        if (nbt == null) {
            return false;
        }
        String raw = nbt.getString("id");
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        String mapped = mapLegacyEntityId(raw, nbt, reporter, context);
        if (mapped == null) {
            nbt.remove("id");
            return true;
        }
        if (!mapped.equals(raw)) {
            nbt.putString("id", mapped);
            warn(reporter, "LegacyEntityId", "mapped '" + raw + "' -> '" + mapped + "'" + formatContext(context));
        }
        fixEntityCustomName(nbt, reporter, context);
        fixEntityAttributes(nbt, reporter, context);
        fixEntityEquipment(nbt, reporter, context);
        fixEntityRiding(nbt, reporter, context);
        if ("minecraft:falling_block".equals(mapped)) {
            fixFallingBlockNbt(nbt, reporter, context);
        }
        if ("minecraft:item".equals(mapped)) {
            fixItemEntityStack(nbt, reporter, context);
        }
        return !mapped.equals(raw);
    }

    private static String mapLegacyEntityId(String raw, NbtCompound nbt, LegacyWarnReporter reporter, String context) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if ("entityhorse".equals(lower) || "minecraft:entityhorse".equals(lower)) {
            return mapHorseType(nbt, reporter, context);
        }
        String direct = LEGACY_MAP.get(lower);
        if (direct != null) {
            return applyNamespaceRemap(direct, reporter, context);
        }
        String namespace = null;
        String path = trimmed;
        if (trimmed.indexOf(':') >= 0) {
            int idx = trimmed.indexOf(':');
            namespace = trimmed.substring(0, idx);
            path = trimmed.substring(idx + 1);
        } else if (trimmed.indexOf('.') >= 0) {
            int idx = trimmed.indexOf('.');
            namespace = trimmed.substring(0, idx);
            path = trimmed.substring(idx + 1);
        } else {
            namespace = "minecraft";
        }
        String keyFull = (namespace + ":" + path).toLowerCase(Locale.ROOT);
        String keyPath = path.toLowerCase(Locale.ROOT);
        String mapped = LEGACY_MAP.get(keyFull);
        if (mapped != null) {
            return applyNamespaceRemap(mapped, reporter, context);
        }
        if ("minecraft".equalsIgnoreCase(namespace)) {
            mapped = LEGACY_MAP.get(keyPath);
            if (mapped != null) {
                return applyNamespaceRemap(mapped, reporter, context);
            }
        }
        String normalized = normalizeEntityPath(path);
        String normalizedId = namespace.toLowerCase(Locale.ROOT) + ":" + normalized;
        Identifier parsed = Identifier.tryParse(normalizedId);
        if (parsed != null) {
            return applyNamespaceRemap(normalizedId, reporter, context);
        }
        String sanitized = sanitizePath(normalized);
        String sanitizedId = namespace.toLowerCase(Locale.ROOT) + ":" + sanitized;
        Identifier retry = Identifier.tryParse(sanitizedId);
        if (retry != null) {
            warn(reporter, "LegacyEntityId", "sanitized '" + raw + "' -> '" + sanitizedId + "'" + formatContext(context));
            return applyNamespaceRemap(sanitizedId, reporter, context);
        }
        warn(reporter, "LegacyEntityId", "invalid id '" + raw + "' (skipped)" + formatContext(context));
        return null;
    }

    private static String mapHorseType(NbtCompound nbt, LegacyWarnReporter reporter, String context) {
        int type = -1;
        if (nbt.contains("Type", NbtElement.NUMBER_TYPE)) {
            type = nbt.getInt("Type");
        }
        String mapped;
        switch (type) {
            case 1 -> mapped = "minecraft:donkey";
            case 2 -> mapped = "minecraft:mule";
            case 3 -> mapped = "minecraft:zombie_horse";
            case 4 -> mapped = "minecraft:skeleton_horse";
            case 0 -> mapped = "minecraft:horse";
            default -> mapped = "minecraft:horse";
        }
        warn(reporter, "LegacyEntityId", "mapped 'EntityHorse' type=" + type + " -> '" + mapped + "'" + formatContext(context));
        return CustomRemapStore.map(mapped, reporter, null, "id");
    }

    private static void fixItemEntityStack(NbtCompound nbt, LegacyWarnReporter reporter, String context) {
        if (!nbt.contains("Item", NbtElement.COMPOUND_TYPE)) {
            return;
        }
        NbtCompound item = nbt.getCompound("Item");
        fixItemStack(item, reporter, context);
    }

    private static void fixItemStack(NbtCompound item, LegacyWarnReporter reporter, String context) {
        if (item == null) {
            return;
        }
        LegacyItemNbtFixer.fixItemStack(item, reporter, context);
        if (!item.contains("id", NbtElement.STRING_TYPE)) {
            return;
        }
        String rawId = item.getString("id");
        if (rawId == null || rawId.isEmpty()) {
            return;
        }
        String normalized = rawId;
        if (!rawId.contains(":")) {
            normalized = "minecraft:" + rawId;
            warn(reporter, "LegacyItemId", "assumed namespace for '" + rawId + "' -> '" + normalized + "'" + formatContext(context));
        }
        normalized = sanitizeItemId(normalized, reporter, context);
        normalized = applyNamespaceRemap(normalized, reporter, context);
        normalized = mapLegacyItemId(normalized, item, reporter, context);
        normalized = resolveDuplicateItemId(normalized, reporter, context);
        if (!normalized.equals(rawId)) {
            item.putString("id", normalized);
        }
        Identifier parsed = Identifier.tryParse(normalized);
        if (parsed == null || !Registry.ITEM.containsId(parsed)) {
            warn(reporter, "LegacyItemId", "missing item " + normalized + formatContext(context));
        }
    }

    private static String mapLegacyItemId(String id, NbtCompound item, LegacyWarnReporter reporter, String context) {
        if (id == null) {
            return null;
        }
        if ("minecraft:banner".equals(id)) {
            int meta = item.contains("Damage", NbtElement.NUMBER_TYPE) ? item.getInt("Damage") : 15;
            if (meta < 0 || meta >= DYE_COLORS.length) {
                warn(reporter, "LegacyItemId", "invalid banner meta " + meta + formatContext(context));
                return "minecraft:white_banner";
            }
            String mapped = "minecraft:" + DYE_COLORS[meta] + "_banner";
            item.remove("Damage");
            warn(reporter, "LegacyItemId", "mapped banner meta " + meta + " -> " + mapped + formatContext(context));
            return mapped;
        }
        if (id.endsWith(":loot_chest")) {
            String mapped = "minecraft:chest";
            warn(reporter, "LegacyItemId", "mapped loot_chest -> " + mapped + formatContext(context));
            return mapped;
        }
        if (id.endsWith(":red_flower")) {
            String mapped = "minecraft:poppy";
            warn(reporter, "LegacyItemId", "mapped red_flower -> " + mapped + formatContext(context));
            return mapped;
        }
        if (id.endsWith(":yellow_flower")) {
            String mapped = "minecraft:dandelion";
            warn(reporter, "LegacyItemId", "mapped yellow_flower -> " + mapped + formatContext(context));
            return mapped;
        }
        return id;
    }

    public static String normalizeItemId(String rawId, LegacyWarnReporter reporter, String context) {
        if (rawId == null || rawId.isEmpty()) {
            return rawId;
        }
        String normalized = rawId.trim();
        if (normalized.isEmpty()) {
            return rawId;
        }
        normalized = mapGoldToGolden(normalized);
        if (!normalized.contains(":")) {
            String assumed = "minecraft:" + normalized;
            warn(reporter, "LegacyItemId", "assumed namespace for '" + normalized + "' -> '" + assumed + "'" + formatContext(context));
            normalized = assumed;
        }
        normalized = sanitizeItemId(normalized, reporter, context);
        normalized = applyNamespaceRemap(normalized, reporter, context);
        normalized = mapLegacyItemAlias(normalized, reporter, context);
        if (normalized.endsWith(":loot_chest")) {
            String mapped = "minecraft:chest";
            warn(reporter, "LegacyItemId", "mapped loot_chest -> " + mapped + formatContext(context));
            normalized = mapped;
        }
        if (normalized.endsWith(":red_flower")) {
            String mapped = "minecraft:poppy";
            warn(reporter, "LegacyItemId", "mapped red_flower -> " + mapped + formatContext(context));
            normalized = mapped;
        }
        if (normalized.endsWith(":yellow_flower")) {
            String mapped = "minecraft:dandelion";
            warn(reporter, "LegacyItemId", "mapped yellow_flower -> " + mapped + formatContext(context));
            normalized = mapped;
        }
        normalized = resolveDuplicateItemId(normalized, reporter, context);
        return normalized;
    }


    private static String resolveDuplicateItemId(String id, LegacyWarnReporter reporter, String context) {
        if (id == null) {
            return null;
        }
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) {
            return id;
        }
        if (Registry.ITEM.containsId(parsed)) {
            return id;
        }
        String namespace = parsed.getNamespace();
        String path = parsed.getPath();
        for (int i = 2; i <= 16; i++) {
            Identifier candidate = new Identifier(namespace, path + "_" + i);
            if (Registry.ITEM.containsId(candidate)) {
                String mapped = candidate.toString();
                warn(reporter, "DuplicateItem", "mapped missing item " + id + " -> " + mapped + formatContext(context));
                return mapped;
            }
        }
        return id;
    }
    private static void fixEntityCustomName(NbtCompound nbt, LegacyWarnReporter reporter, String context) {
        if (!nbt.contains("CustomName", NbtElement.STRING_TYPE)) {
            return;
        }
        String raw = nbt.getString("CustomName");
        if (raw == null || raw.isEmpty()) {
            return;
        }
        if (raw.trim().startsWith("{")) {
            return;
        }
        String json = Text.Serializer.toJson(LegacyText.fromLegacy(raw));
        nbt.putString("CustomName", json);
        warn(reporter, "LegacyText", "converted entity custom name '" + raw + "' to json" + formatContext(context));
    }

    private static void fixEntityAttributes(NbtCompound nbt, LegacyWarnReporter reporter, String context) {
        if (!nbt.contains("Attributes", NbtElement.LIST_TYPE)) {
            return;
        }
        var list = nbt.getList("Attributes", NbtElement.COMPOUND_TYPE);
        if (list == null || list.isEmpty()) {
            return;
        }
        NbtList fixed = new NbtList();
        boolean changed = false;
        for (int i = 0; i < list.size(); i++) {
            NbtElement element = list.get(i);
            if (!(element instanceof NbtCompound attr)) {
                changed = true;
                continue;
            }
            if (attr.contains("Name", NbtElement.STRING_TYPE)) {
                String raw = attr.getString("Name");
                String normalized = LegacyItemNbtFixer.normalizeAttributeId(raw, reporter, context);
                if (normalized != null && !normalized.equals(raw)) {
                    attr.putString("Name", normalized);
                    warn(reporter, "LegacyAttribute", "mapped '" + raw + "' -> '" + normalized + "'" + formatContext(context));
                }
            }
            fixed.add(attr);
        }
        if (changed) {
            if (fixed.isEmpty()) {
                nbt.remove("Attributes");
            } else {
                nbt.put("Attributes", fixed);
            }
        }
    }

    private static void fixEntityRiding(NbtCompound nbt, LegacyWarnReporter reporter, String context) {
        if (!nbt.contains("Riding", NbtElement.COMPOUND_TYPE)) {
            return;
        }
        NbtCompound riding = nbt.getCompound("Riding");
        if (riding == null || riding.isEmpty()) {
            nbt.remove("Riding");
            return;
        }
        fixEntityId(riding, reporter, context);
        NbtList passengers = new NbtList();
        passengers.add(riding);
        nbt.put("Passengers", passengers);
        nbt.remove("Riding");
        warn(reporter, "LegacyRiding", "converted Riding -> Passengers" + formatContext(context));
    }

    private static void fixEntityEquipment(NbtCompound nbt, LegacyWarnReporter reporter, String context) {
        if (!nbt.contains("Equipment", NbtElement.LIST_TYPE)) {
            return;
        }
        var equipment = nbt.getList("Equipment", NbtElement.COMPOUND_TYPE);
        if (equipment == null || equipment.isEmpty()) {
            return;
        }
        NbtList handItems = new NbtList();
        NbtList armorItems = new NbtList();
        NbtCompound mainHand = equipment.size() > 0 ? equipment.getCompound(0).copy() : new NbtCompound();
        NbtCompound boots = equipment.size() > 1 ? equipment.getCompound(1).copy() : new NbtCompound();
        NbtCompound leggings = equipment.size() > 2 ? equipment.getCompound(2).copy() : new NbtCompound();
        NbtCompound chest = equipment.size() > 3 ? equipment.getCompound(3).copy() : new NbtCompound();
        NbtCompound head = equipment.size() > 4 ? equipment.getCompound(4).copy() : new NbtCompound();
        fixItemStack(mainHand, reporter, context);
        fixItemStack(boots, reporter, context);
        fixItemStack(leggings, reporter, context);
        fixItemStack(chest, reporter, context);
        fixItemStack(head, reporter, context);
        handItems.add(mainHand);
        handItems.add(new NbtCompound());
        armorItems.add(boots);
        armorItems.add(leggings);
        armorItems.add(chest);
        armorItems.add(head);
        nbt.put("HandItems", handItems);
        nbt.put("ArmorItems", armorItems);
        nbt.remove("Equipment");

        if (nbt.contains("DropChances", NbtElement.LIST_TYPE)) {
            var chances = nbt.getList("DropChances", NbtElement.FLOAT_TYPE);
            if (chances != null && chances.size() >= 5) {
                NbtList handChances = new NbtList();
                NbtList armorChances = new NbtList();
                handChances.add(chances.get(0).copy());
                handChances.add(net.minecraft.nbt.NbtFloat.of(0.0f));
                armorChances.add(chances.get(1).copy());
                armorChances.add(chances.get(2).copy());
                armorChances.add(chances.get(3).copy());
                armorChances.add(chances.get(4).copy());
                nbt.put("HandDropChances", handChances);
                nbt.put("ArmorDropChances", armorChances);
                nbt.remove("DropChances");
            }
        }
        warn(reporter, "LegacyEquipment", "converted Equipment -> HandItems/ArmorItems" + formatContext(context));
    }

    private static void fixFallingBlockNbt(NbtCompound nbt, LegacyWarnReporter reporter, String context) {
        if (nbt == null) {
            return;
        }
        if (nbt.contains("BlockState", NbtElement.COMPOUND_TYPE)) {
            NbtCompound state = nbt.getCompound("BlockState");
            if (state.contains("Name", NbtElement.STRING_TYPE)) {
                String name = state.getString("Name");
                if (name != null && !name.isEmpty() && !name.contains(":")) {
                    String fixed = "minecraft:" + name.toLowerCase(Locale.ROOT);
                    state.putString("Name", fixed);
                    warn(reporter, "LegacyBlockId", "assumed namespace for '" + name + "' -> '" + fixed + "'" + formatContext(context));
                }
            }
            return;
        }
        if (nbt.contains("Block", NbtElement.STRING_TYPE)) {
            String raw = nbt.getString("Block");
            if (raw == null || raw.isEmpty()) {
                return;
            }
            String normalized = raw;
            if (!raw.contains(":")) {
                normalized = "minecraft:" + raw.toLowerCase(Locale.ROOT);
                warn(reporter, "LegacyBlockId", "assumed namespace for '" + raw + "' -> '" + normalized + "'" + formatContext(context));
            } else {
                normalized = raw.toLowerCase(Locale.ROOT);
            }
            NbtCompound state = new NbtCompound();
            state.putString("Name", normalized);
            nbt.put("BlockState", state);
            nbt.remove("Block");
            if (nbt.contains("Data", NbtElement.NUMBER_TYPE)) {
                nbt.remove("Data");
            }
            warn(reporter, "LegacyFallingBlock", "converted Block -> BlockState" + formatContext(context));
        }
    }

    private static String applyNamespaceRemap(String id, LegacyWarnReporter reporter, String context) {
        if (id == null) {
            return null;
        }
        int idx = id.indexOf(':');
        if (idx <= 0) {
            return id;
        }
        String ns = id.substring(0, idx);
        if (!"potioncore".equalsIgnoreCase(ns)) {
            return id;
        }
        ReLootPlusPlusConfig config = RuntimeState.config();
        if (config == null || config.potioncoreNamespace == null || config.potioncoreNamespace.isBlank()) {
            return id;
        }
        String target = config.potioncoreNamespace.trim();
        if (target.equalsIgnoreCase("potioncore")) {
            return id;
        }
        String mapped = target.toLowerCase(Locale.ROOT) + ":" + id.substring(idx + 1);
        warn(reporter, "LegacyNamespace", "mapped potioncore -> " + target + " for '" + id + "' -> '" + mapped + "'" + formatContext(context));
        return mapped;
    }

    private static String normalizeEntityPath(String rawPath) {
        String path = rawPath;
        if (path.startsWith("Entity") && path.length() > "Entity".length()) {
            path = path.substring("Entity".length());
        }
        return toSnakeCase(path);
    }

    private static String sanitizeItemId(String id, LegacyWarnReporter reporter, String context) {
        if (id == null || id.isEmpty()) {
            return id;
        }
        String trimmed = id.trim();
        int idx = trimmed.indexOf(':');
        if (idx <= 0) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        String ns = trimmed.substring(0, idx).toLowerCase(Locale.ROOT);
        String path = mapGoldToGolden(trimmed.substring(idx + 1));
        String sanitized = sanitizePath(path);
        String rebuilt = ns + ":" + sanitized;
        if (!rebuilt.equals(trimmed)) {
            warn(reporter, "LegacyItemId", "sanitized '" + trimmed + "' -> '" + rebuilt + "'" + formatContext(context));
        }
        return rebuilt;
    }

    private static String mapGoldToGolden(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        String path = raw;
        int idx = raw.indexOf(':');
        if (idx >= 0) {
            path = raw.substring(idx + 1);
        }
        String lowered = path.toLowerCase(Locale.ROOT);
        if (!lowered.startsWith("gold_")) {
            return raw;
        }
        String suffix = lowered.substring("gold_".length());
        switch (suffix) {
            case "shovel":
            case "pickaxe":
            case "axe":
            case "sword":
            case "hoe":
            case "helmet":
            case "chestplate":
            case "leggings":
            case "boots":
            case "horse_armor":
            case "apple":
            case "carrot":
                if (idx >= 0) {
                    return raw.substring(0, idx + 1) + "golden_" + suffix;
                }
                return "golden_" + suffix;
            default:
                return raw;
        }
    }

    private static String sanitizePath(String path) {
        String cleaned = path;
        while (cleaned.startsWith("/") || cleaned.startsWith(".") || cleaned.startsWith("\\")) {
            cleaned = cleaned.substring(1);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (Identifier.isPathCharacterValid(c)) {
                out.append(c);
            } else if (Character.isUpperCase(c)) {
                out.append(Character.toLowerCase(c));
            } else {
                out.append('_');
            }
        }
        return out.toString();
    }

    private static String mapLegacyItemAlias(String id, LegacyWarnReporter reporter, String context) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) {
            return id;
        }
        String namespace = parsed.getNamespace();
        String path = parsed.getPath();
        if ("lucky".equalsIgnoreCase(namespace) && path.startsWith("lucky_block_")) {
            Identifier baseLuckyBlock = new Identifier("lucky", "lucky_block");
            if (Registry.ITEM.containsId(baseLuckyBlock)) {
                String mapped = baseLuckyBlock.toString();
                warn(reporter, "LegacyItemId", "mapped legacy lucky item " + id + " -> " + mapped + formatContext(context));
                return mapped;
            }
        }
        if (!"minecraft".equalsIgnoreCase(namespace)) {
            return id;
        }
        if (path.startsWith("record_") && path.length() > "record_".length()) {
            String mapped = "minecraft:music_disc_" + path.substring("record_".length());
            warn(reporter, "LegacyItemId", "mapped legacy item " + id + " -> " + mapped + formatContext(context));
            return mapped;
        }
        String mappedPath = switch (path) {
            case "noteblock" -> "note_block";
            case "wooden_pressure_plate" -> "oak_pressure_plate";
            case "wooden_button" -> "oak_button";
            case "wooden_door" -> "oak_door";
            case "trapdoor" -> "oak_trapdoor";
            case "fence_gate" -> "oak_fence_gate";
            case "magic_book" -> "enchanted_book";
            case "web" -> "cobweb";
            case "dye" -> "white_dye";
            case "skull" -> "skeleton_skull";
            case "fish" -> "cod";
            case "cooked_fish" -> "cooked_cod";
            case "slime" -> "slime_ball";
            case "speckled_melon" -> "glistering_melon_slice";
            case "lit_pumpkin" -> "jack_o_lantern";
            case "waterlilly" -> "lily_pad";
            case "quartz_ore" -> "quartz";
            case "zombie" -> "zombie_spawn_egg";
            case "skeleton" -> "skeleton_spawn_egg";
            default -> null;
        };
        if (mappedPath == null || mappedPath.equals(path)) {
            return id;
        }
        String mapped = "minecraft:" + mappedPath;
        warn(reporter, "LegacyItemId", "mapped legacy item " + id + " -> " + mapped + formatContext(context));
        return mapped;
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
            } else if (c == '.') {
                out.append('_');
            } else {
                out.append(Character.toLowerCase(c));
            }
            prev = c;
        }
        return out.toString();
    }

    private static void register(String legacy, String modern) {
        LEGACY_MAP.put(legacy.toLowerCase(Locale.ROOT), modern);
    }

    private static void warn(LegacyWarnReporter reporter, String type, String detail) {
        if (reporter != null) {
            reporter.warnOnce(type, detail, null);
            return;
        }
        Log.warn("Legacy", "{} {}", type, detail);
    }

    private static String formatContext(String context) {
        if (context == null || context.isEmpty()) {
            return "";
        }
        return " (" + context + ")";
    }
}
