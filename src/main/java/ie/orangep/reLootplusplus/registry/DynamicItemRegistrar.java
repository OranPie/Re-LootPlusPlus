package ie.orangep.reLootplusplus.registry;

import ie.orangep.reLootplusplus.config.model.item.ArmorDef;
import ie.orangep.reLootplusplus.config.model.item.ArmorSlotType;
import ie.orangep.reLootplusplus.config.model.item.BowDef;
import ie.orangep.reLootplusplus.config.model.item.FoodDef;
import ie.orangep.reLootplusplus.config.model.item.FoodEffectSpec;
import ie.orangep.reLootplusplus.config.model.item.GenericItemDef;
import ie.orangep.reLootplusplus.config.model.item.GunDef;
import ie.orangep.reLootplusplus.config.model.item.ItemAdditions;
import ie.orangep.reLootplusplus.config.model.item.MaterialDef;
import ie.orangep.reLootplusplus.config.model.item.MultitoolDef;
import ie.orangep.reLootplusplus.config.model.item.SwordDef;
import ie.orangep.reLootplusplus.config.model.item.ToolDef;
import ie.orangep.reLootplusplus.config.model.item.ToolType;
import ie.orangep.reLootplusplus.content.item.LegacyNamedArmorItem;
import ie.orangep.reLootplusplus.content.item.LegacyNamedAxeItem;
import ie.orangep.reLootplusplus.content.item.LegacyNamedBowItem;
import ie.orangep.reLootplusplus.content.item.LegacyNamedHoeItem;
import ie.orangep.reLootplusplus.content.item.LegacyNamedItem;
import ie.orangep.reLootplusplus.content.item.LegacyNamedPickaxeItem;
import ie.orangep.reLootplusplus.content.item.LegacyNamedShovelItem;
import ie.orangep.reLootplusplus.content.item.LegacyNamedSwordItem;
import ie.orangep.reLootplusplus.content.item.LootThrownItem;
import ie.orangep.reLootplusplus.content.material.LegacyArmorMaterial;
import ie.orangep.reLootplusplus.content.material.LegacyToolMaterial;
import ie.orangep.reLootplusplus.config.model.rule.ThrownDef;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.legacy.mapping.LegacyEffectIdMapper;
import net.minecraft.item.Item;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import net.minecraft.entity.EquipmentSlot;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class DynamicItemRegistrar {
    private final LegacyWarnReporter warnReporter;
    private final String duplicateStrategy;

    public DynamicItemRegistrar(LegacyWarnReporter warnReporter, String duplicateStrategy) {
        this.warnReporter = warnReporter;
        this.duplicateStrategy = duplicateStrategy == null ? "suffix" : duplicateStrategy;
    }

    public void registerThrownItems(List<ThrownDef> defs) {
        int added = 0;
        for (ThrownDef def : defs) {
            Identifier id = Identifier.tryParse(def.itemId());
            if (id == null) {
                warnReporter.warn("LegacyItemId", "bad item id " + def.itemId(), def.sourceLoc());
                continue;
            }
            id = resolveDuplicateItemId(id, def.sourceLoc());
            if (id == null) {
                continue;
            }
            Item item = new LootThrownItem(new Item.Settings(), def.shines(), def.displayName());
            Registry.register(Registry.ITEM, id, item);
            added++;
            if (!Registry.ITEM.containsId(id)) {
                Log.warn("[LootPP-Legacy] Thrown item not registered {}", id);
            }
        }
        Log.LOGGER.info("Registered {} thrown items", added);
    }

    public void registerItemAdditions(ItemAdditions additions) {
        registerGenericItems(additions.genericItems());
        registerFoods(additions.foods());
        registerBows(additions.bows());
        registerGuns(additions.guns());
        registerMultitools(additions.multitools());
        registerSwords(additions, additions.swords());
        registerTools(additions, additions.tools());
        registerArmor(additions, additions.armors());
    }

    private void registerGenericItems(List<GenericItemDef> defs) {
        for (GenericItemDef def : defs) {
            registerItem(def.itemId(), () -> new LegacyNamedItem(new Item.Settings(), def.shiny(), def.displayName()), def.sourceLoc());
        }
    }

    private void registerFoods(List<FoodDef> defs) {
        for (FoodDef def : defs) {
            Item.Settings settings = new Item.Settings();
            var builder = new net.minecraft.item.FoodComponent.Builder()
                .hunger(def.hunger())
                .saturationModifier(def.saturation());
            if (def.wolvesEat()) {
                builder.meat();
            }
            if (def.alwaysEdible()) {
                builder.alwaysEdible();
            }
            if (def.timeToEat() > 0 && def.timeToEat() < 32) {
                builder.snack();
            } else if (def.timeToEat() != 32) {
                warnReporter.warnOnce("LegacyFoodTime", "time_to_eat=" + def.timeToEat(), def.sourceLoc());
            }
            for (FoodEffectSpec effect : def.effects()) {
                var effectId = LegacyEffectIdMapper.resolve(effect.effectId(), warnReporter, def.sourceLoc());
                if (effectId == null) {
                    continue;
                }
                var statusEffect = Registry.STATUS_EFFECT.get(effectId);
                if (statusEffect == null) {
                    warnReporter.warn("LegacyEffect", "unknown effect " + effectId, def.sourceLoc());
                    continue;
                }
                boolean showParticles = !"none".equalsIgnoreCase(effect.particleType());
                boolean ambient = "faded".equalsIgnoreCase(effect.particleType());
                var instance = new net.minecraft.entity.effect.StatusEffectInstance(
                    statusEffect,
                    effect.durationTicks(),
                    effect.amplifier(),
                    ambient,
                    showParticles,
                    showParticles
                );
                builder.statusEffect(instance, effect.probability());
            }
            settings.food(builder.build());
            registerItem(def.itemId(), () -> new LegacyNamedItem(settings, def.shiny(), def.displayName()), def.sourceLoc());
        }
    }

    private void registerBows(List<BowDef> defs) {
        for (BowDef def : defs) {
            Item.Settings settings = new Item.Settings();
            if (def.durability() > 0) {
                settings.maxDamage(def.durability());
            }
            registerItem(def.itemId(), () -> new LegacyNamedBowItem(settings, def.displayName()), def.sourceLoc());
            if (def.rawParts().size() > 3) {
                warnReporter.warnOnce("LegacyItemAdditions", "bow extra fields ignored for " + def.itemId(), def.sourceLoc());
            }
        }
    }

    private void registerGuns(List<GunDef> defs) {
        for (GunDef def : defs) {
            Item.Settings settings = new Item.Settings();
            if (def.durability() > 0) {
                settings.maxDamage(def.durability());
            }
            registerItem(def.itemId(), () -> new LegacyNamedItem(settings, false, def.displayName()), def.sourceLoc());
            warnReporter.warnOnce("LegacyItemAdditions", "guns behavior not implemented for " + def.itemId(), def.sourceLoc());
        }
    }

    private void registerMultitools(List<MultitoolDef> defs) {
        for (MultitoolDef def : defs) {
            registerItem(def.itemId(), () -> new LegacyNamedItem(new Item.Settings(), false, def.displayName()), def.sourceLoc());
            warnReporter.warnOnce("LegacyItemAdditions", "multitool behavior not implemented for " + def.itemId(), def.sourceLoc());
        }
    }

    private void registerSwords(ItemAdditions additions, List<SwordDef> defs) {
        for (SwordDef def : defs) {
            ToolMaterial material = resolveToolMaterial(additions, def.materialItemId(), def.materialMeta(), def.sourceLoc());
            int extraDamage = Math.round(def.extraDamage());
            if (Math.abs(def.extraDamage() - extraDamage) > 0.001f) {
                warnReporter.warnOnce("LegacyToolDamage", "fractional sword damage " + def.extraDamage(), def.sourceLoc());
            }
            registerItem(def.itemId(),
                () -> new LegacyNamedSwordItem(material, extraDamage, -2.4f, new Item.Settings(), def.displayName()),
                def.sourceLoc());
        }
    }

    private void registerTools(ItemAdditions additions, List<ToolDef> defs) {
        for (ToolDef def : defs) {
            ToolMaterial material = resolveToolMaterial(additions, def.materialItemId(), def.materialMeta(), def.sourceLoc());
            registerItem(def.itemId(), () -> switch (def.type()) {
                case PICKAXE -> new LegacyNamedPickaxeItem(material, 0, -2.8f, new Item.Settings(), def.displayName());
                case AXE -> new LegacyNamedAxeItem(material, material.getAttackDamage(), -3.1f, new Item.Settings(), def.displayName());
                case SHOVEL -> new LegacyNamedShovelItem(material, material.getAttackDamage(), -3.0f, new Item.Settings(), def.displayName());
                case HOE -> new LegacyNamedHoeItem(material, 0, -1.0f, new Item.Settings(), def.displayName());
                default -> new LegacyNamedItem(new Item.Settings(), false, def.displayName());
            }, def.sourceLoc());
        }
    }

    private void registerArmor(ItemAdditions additions, List<ArmorDef> defs) {
        for (ArmorDef def : defs) {
            MaterialDef materialDef = additions.findMaterial(def.materialItemId(), def.materialMeta());
            if (materialDef == null) {
                warnReporter.warn("LegacyMaterial", "missing material " + def.materialItemId(), def.sourceLoc());
                registerItem(def.itemId(), () -> new LegacyNamedItem(new Item.Settings(), false, def.displayName()), def.sourceLoc());
                continue;
            }
            LegacyArmorMaterial material = createArmorMaterial(materialDef, def.textureBase(), def.sourceLoc());
            EquipmentSlot slot = switch (def.slotType()) {
                case HELMET -> EquipmentSlot.HEAD;
                case CHESTPLATE -> EquipmentSlot.CHEST;
                case LEGGINGS -> EquipmentSlot.LEGS;
                case BOOTS -> EquipmentSlot.FEET;
            };
            registerItem(def.itemId(), () -> new LegacyNamedArmorItem(material, slot, new Item.Settings(), def.displayName()), def.sourceLoc());
        }
    }

    private ToolMaterial resolveToolMaterial(ItemAdditions additions, String materialId, int meta, ie.orangep.reLootplusplus.diagnostic.SourceLoc loc) {
        MaterialDef materialDef = additions.findMaterial(materialId, meta);
        if (materialDef == null) {
            warnReporter.warn("LegacyMaterial", "missing material " + materialId, loc);
            return new LegacyToolMaterial(1, 250, 4.0f, 1.0f, 1, Ingredient.EMPTY);
        }
        Ingredient repair = resolveIngredient(materialDef.itemId(), materialDef.meta(), materialDef.sourceLoc());
        return new LegacyToolMaterial(
            materialDef.harvestLevel(),
            materialDef.durability(),
            materialDef.efficiency(),
            materialDef.damage(),
            materialDef.enchantability(),
            repair
        );
    }

    private LegacyArmorMaterial createArmorMaterial(MaterialDef materialDef, String textureBase, ie.orangep.reLootplusplus.diagnostic.SourceLoc loc) {
        String name = normalizeArmorName(textureBase, loc);
        Ingredient repair = resolveIngredient(materialDef.itemId(), materialDef.meta(), materialDef.sourceLoc());
        return new LegacyArmorMaterial(
            name,
            materialDef.armorDurabilityFactor(),
            materialDef.armorProtection(),
            materialDef.enchantability(),
            repair,
            0.0f,
            0.0f
        );
    }

    private Ingredient resolveIngredient(String itemId, int meta, ie.orangep.reLootplusplus.diagnostic.SourceLoc loc) {
        if (itemId == null) {
            return Ingredient.EMPTY;
        }
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !Registry.ITEM.containsId(id)) {
            warnReporter.warn("LegacyItemId", "missing repair item " + itemId, loc);
            return Ingredient.EMPTY;
        }
        if (meta != 0 && meta != 32767) {
            warnReporter.warnOnce("LegacyMeta", "material meta ignored " + meta, loc);
        }
        return Ingredient.ofItems(Registry.ITEM.get(id));
    }

    private String normalizeArmorName(String textureBase, ie.orangep.reLootplusplus.diagnostic.SourceLoc loc) {
        if (textureBase == null || textureBase.isEmpty()) {
            warnReporter.warnOnce("LegacyArmor", "missing texture base", loc);
            return "lootplusplus:unknown";
        }
        String value = textureBase.trim().replace('\\', '/');
        if (value.endsWith("_layer_1")) {
            value = value.substring(0, value.length() - "_layer_1".length());
        } else if (value.endsWith("_layer_2")) {
            value = value.substring(0, value.length() - "_layer_2".length());
        }
        if (value.endsWith(".png")) {
            value = value.substring(0, value.length() - ".png".length());
        }
        String prefix = "textures/models/armor/";
        if (value.contains(prefix)) {
            value = value.substring(value.lastIndexOf(prefix) + prefix.length());
        }
        String namespace = null;
        String path = value;
        int colon = value.indexOf(':');
        if (colon > 0) {
            namespace = value.substring(0, colon);
            path = value.substring(colon + 1);
        }
        if (path.contains(prefix)) {
            path = path.substring(path.lastIndexOf(prefix) + prefix.length());
        }
        if (namespace == null || namespace.isBlank()) {
            warnReporter.warnOnce("LegacyItemId", "missing namespace for armor texture " + value, loc);
            namespace = "lootplusplus";
        }
        if (namespace.contains("/")) {
            namespace = namespace.substring(namespace.lastIndexOf('/') + 1);
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return (namespace + ":" + path).toLowerCase(Locale.ROOT);
    }

    private void registerItem(String rawId, Supplier<Item> supplier, ie.orangep.reLootplusplus.diagnostic.SourceLoc loc) {
        Identifier id = Identifier.tryParse(rawId);
        if (id == null) {
            warnReporter.warn("LegacyItemId", "bad item id " + rawId, loc);
            return;
        }
        id = resolveDuplicateItemId(id, loc);
        if (id == null) {
            return;
        }
        Item item = supplier.get();
        if (item == null) {
            warnReporter.warnOnce("LegacyItemId", "null item for " + id, loc);
            return;
        }
        Registry.register(Registry.ITEM, id, item);
    }

    private Identifier resolveDuplicateItemId(Identifier id, ie.orangep.reLootplusplus.diagnostic.SourceLoc loc) {
        if (!Registry.ITEM.containsId(id)) {
            return id;
        }
        if ("ignore".equalsIgnoreCase(duplicateStrategy)) {
            warnReporter.warnOnce("DuplicateItem", "skipping duplicate " + id, loc);
            return null;
        }
        String basePath = id.getPath();
        String namespace = id.getNamespace();
        int counter = 2;
        Identifier candidate = new Identifier(namespace, basePath + "_" + counter);
        while (Registry.ITEM.containsId(candidate)) {
            counter++;
            candidate = new Identifier(namespace, basePath + "_" + counter);
        }
        warnReporter.warnOnce("DuplicateItem", "renamed " + id + " -> " + candidate, loc);
        return candidate;
    }
}
