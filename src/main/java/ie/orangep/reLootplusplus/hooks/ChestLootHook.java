package ie.orangep.reLootplusplus.hooks;

import ie.orangep.reLootplusplus.config.model.rule.ChestAmountRule;
import ie.orangep.reLootplusplus.config.model.rule.ChestLootRule;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.legacy.mapping.LegacyChestTypeMapper;
import ie.orangep.reLootplusplus.legacy.mapping.LegacyEntityIdFixer;
import ie.orangep.reLootplusplus.legacy.nbt.LenientNbtParser;
import ie.orangep.reLootplusplus.runtime.ChestLootRegistry;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.item.Item;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetCountLootFunction;
import net.minecraft.loot.function.SetNbtLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.List;
import java.util.Map;

public final class ChestLootHook {
    private final ChestLootRegistry registry;
    private final LegacyChestTypeMapper mapper;
    private final LegacyWarnReporter warnReporter;

    public ChestLootHook(ChestLootRegistry registry, LegacyChestTypeMapper mapper, LegacyWarnReporter warnReporter) {
        this.registry = registry;
        this.mapper = mapper;
        this.warnReporter = warnReporter;
    }

    public void install() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            for (Map.Entry<String, List<ChestLootRule>> entry : registry.allLoots().entrySet()) {
                String chestType = entry.getKey();
                if (!appliesTo(id, chestType, entry.getValue().isEmpty() ? null : entry.getValue().get(0))) {
                    continue;
                }
                LootPool.Builder pool = buildPool(chestType, entry.getValue(), id);
                if (pool != null) {
                    tableBuilder.pool(pool);
                }
            }
        });
    }

    private boolean appliesTo(Identifier tableId, String chestType, ChestLootRule firstRule) {
        Identifier target = mapper.resolve(chestType, firstRule == null ? null : firstRule.sourceLoc());
        if (target == null) {
            return false;
        }
        return target.equals(tableId);
    }

    private LootPool.Builder buildPool(String chestType, List<ChestLootRule> rules, Identifier tableId) {
        ChestAmountRule amount = registry.amountFor(chestType);
        int minRolls = amount != null ? amount.minItems() : 3;
        int maxRolls = amount != null ? amount.maxItems() : 9;

        LootPool.Builder pool = LootPool.builder()
            .rolls(UniformLootNumberProvider.create(minRolls, maxRolls));

        int added = 0;
        for (ChestLootRule rule : rules) {
            if (rule.weight() <= 0) {
                continue;
            }
            String normalizedId = LegacyEntityIdFixer.normalizeItemId(
                rule.itemId(),
                warnReporter,
                rule.sourceLoc() == null ? null : rule.sourceLoc().formatShort()
            );
            normalizedId = mapWoolMeta(normalizedId, rule.meta());
            Identifier itemId = Identifier.tryParse(normalizedId);
            if (itemId == null || !Registry.ITEM.containsId(itemId)) {
                warnReporter.warn("LegacyItemId", "missing item " + normalizedId, rule.sourceLoc());
                continue;
            }
            Item item = Registry.ITEM.get(itemId);
            ItemEntry.Builder<?> entry = ItemEntry.builder(item)
                .weight(rule.weight());

            if (rule.minCount() != rule.maxCount()) {
                entry.apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(rule.minCount(), rule.maxCount())));
            } else {
                entry.apply(SetCountLootFunction.builder(ConstantLootNumberProvider.create(rule.minCount())));
            }

            NbtCompound tag = null;
            if (rule.nbtRaw() != null && !rule.nbtRaw().isEmpty() && !"{}".equals(rule.nbtRaw())) {
                tag = LenientNbtParser.parseOrEmpty(rule.nbtRaw(), warnReporter, rule.sourceLoc(), "LegacyNBT");
            }
            if (rule.meta() > 0 && tag == null) {
                tag = new NbtCompound();
                tag.putInt("Damage", rule.meta());
                warnReporter.warnOnce("LegacyMeta", "chest meta used " + rule.meta(), rule.sourceLoc());
            } else if (rule.meta() > 0 && tag != null && !tag.contains("Damage")) {
                tag.putInt("Damage", rule.meta());
                warnReporter.warnOnce("LegacyMeta", "chest meta used " + rule.meta(), rule.sourceLoc());
            }
            if (tag != null) {
                entry.apply(SetNbtLootFunction.builder(tag));
            }

            pool.with(entry);
            added++;
        }
        if (added == 0) {
            Log.warn("[LootPP-Legacy] ChestLoot empty pool for {} -> {}", chestType, tableId);
        } else {
            Log.LOGGER.info("[LootPP-Legacy] ChestLoot {} -> {} (rules {}, rolls {}-{})", chestType, tableId, added, minRolls, maxRolls);
        }
        return pool;
    }

    private static String mapWoolMeta(String id, int meta) {
        if (id == null || meta < 0 || meta > 15) {
            return id;
        }
        if (!id.endsWith(":wool")) {
            return id;
        }
        String[] dye = new String[] {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
        };
        return "minecraft:" + dye[meta] + "_wool";
    }
}
