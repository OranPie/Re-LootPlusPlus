package ie.orangep.reLootplusplus.lucky.registry;

import ie.orangep.reLootplusplus.config.AddonDisableStore;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.block.NativeLuckyBlock;
import ie.orangep.reLootplusplus.lucky.block.NativeLuckyBlockEntity;
import ie.orangep.reLootplusplus.lucky.block.NativeLuckyBlockItem;
import ie.orangep.reLootplusplus.lucky.item.NativeLuckyBow;
import ie.orangep.reLootplusplus.lucky.item.NativeLuckyPotion;
import ie.orangep.reLootplusplus.lucky.item.NativeLuckySword;
import ie.orangep.reLootplusplus.lucky.loader.LuckyAddonData;
import ie.orangep.reLootplusplus.lucky.loader.LuckyPluginInit;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ToolMaterials;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registers all addon-declared Lucky Block blocks and items under the {@code lucky:} namespace.
 *
 * <p>Called from {@code Bootstrap.java} in Phase 4, after {@link LuckyRegistrar#register()}.
 * Must not be called more than once (duplicate registry writes are prevented by try/catch).
 */
public final class AddonLuckyRegistrar {

    private static final Map<Block, String> BLOCK_TO_PACK = new LinkedHashMap<>();
    private static final Map<String, LuckyAddonData> BLOCK_ID_TO_ADDON = new LinkedHashMap<>();
    // Direct Block→data map for fast lookup in onBreak
    private static final Map<Block, LuckyAddonData> BLOCK_TO_DATA = new LinkedHashMap<>();

    private AddonLuckyRegistrar() {}

    /**
     * Registers all addon blocks/items. Called exactly once during bootstrap Phase 4.
     */
    public static synchronized void register(List<LuckyAddonData> addonDataList) {
        for (LuckyAddonData data : addonDataList) {
            if (data.pluginInit() == null) continue;
            LuckyPluginInit pi = data.pluginInit();
            String rawBlockId = pi.blockId();
            if (rawBlockId == null || rawBlockId.isBlank()) continue;

            String blockId = rawBlockId.toLowerCase(Locale.ROOT).replace('.', '_');
            Identifier blockReg = new Identifier("lucky", blockId);

            // Skip if this id is already registered (e.g. the base lucky:lucky_block)
            if (Registry.BLOCK.containsId(blockReg)) {
                Log.warn("AddonLucky", "Block {} already registered, skipping addon {}", blockReg, data.packId());
                continue;
            }

            try {
                NativeLuckyBlock block = Registry.register(
                    Registry.BLOCK, blockReg,
                    new NativeLuckyBlock(AbstractBlock.Settings.of(Material.WOOD, MapColor.YELLOW)
                        .strength(0.5f, 10.0f).sounds(BlockSoundGroup.WOOD))
                );

                Registry.register(
                    Registry.ITEM, blockReg,
                    new NativeLuckyBlockItem(block, new Item.Settings().group(ItemGroup.DECORATIONS))
                );

                if (pi.swordId() != null && !pi.swordId().isBlank()) {
                    String sId = pi.swordId().toLowerCase(Locale.ROOT).replace('.', '_');
                    Identifier sReg = new Identifier("lucky", sId);
                    if (!Registry.ITEM.containsId(sReg)) {
                        Registry.register(Registry.ITEM, sReg,
                            new NativeLuckySword(ToolMaterials.GOLD, 3, -2.4f,
                                new Item.Settings().group(ItemGroup.COMBAT)));
                    }
                }
                if (pi.bowId() != null && !pi.bowId().isBlank()) {
                    String bId = pi.bowId().toLowerCase(Locale.ROOT).replace('.', '_');
                    Identifier bReg = new Identifier("lucky", bId);
                    if (!Registry.ITEM.containsId(bReg)) {
                        Registry.register(Registry.ITEM, bReg,
                            new NativeLuckyBow(new Item.Settings().maxDamage(384).group(ItemGroup.COMBAT)));
                    }
                }
                if (pi.potionId() != null && !pi.potionId().isBlank()) {
                    String pId = pi.potionId().toLowerCase(Locale.ROOT).replace('.', '_');
                    Identifier pReg = new Identifier("lucky", pId);
                    if (!Registry.ITEM.containsId(pReg)) {
                        Registry.register(Registry.ITEM, pReg,
                            new NativeLuckyPotion(new Item.Settings().maxCount(16).group(ItemGroup.BREWING)));
                    }
                }

                BLOCK_TO_PACK.put(block, data.packId());
                BLOCK_ID_TO_ADDON.put(blockId, data);
                BLOCK_TO_DATA.put(block, data);
                Log.info("AddonLucky", "Registered addon block lucky:{} from pack {}", blockId, data.packId());
            } catch (Exception e) {
                Log.error("AddonLucky", "Failed to register addon block {}: {}", blockId, e.getMessage());
            }
        }
    }

    /**
     * Returns true when the given block is an addon Lucky Block whose pack is currently enabled.
     * Returns true for non-addon blocks (unknown blocks are not gated here).
     */
    public static boolean isAddonEnabled(Block block) {
        LuckyAddonData data = BLOCK_TO_DATA.get(block);
        if (data == null) return true; // not an addon block — don't suppress
        return AddonDisableStore.isEnabled(data.packId());
    }

    /**
     * Returns true if the block is any known addon Lucky Block (enabled or disabled).
     */
    public static boolean isAddonBlock(Block block) {
        return BLOCK_TO_DATA.containsKey(block);
    }

    /**
     * Returns the drop lines for an addon block, or {@code null} if the block is not an addon block.
     * Returns an empty list (not null) when the owning pack is currently disabled.
     */
    public static List<String> getDropsForBlock(Block block) {
        LuckyAddonData data = BLOCK_TO_DATA.get(block);
        if (data == null) return null;
        if (!AddonDisableStore.isEnabled(data.packId())) return Collections.emptyList();
        return data.dropLines();
    }

    /**
     * Returns the pre-parsed drop lines for an addon block, or {@code null} if not found.
     * Returns an empty list (not null) when the owning pack is currently disabled.
     * Prefer this over {@link #getDropsForBlock} at block-break time.
     */
    public static List<LuckyDropLine> getParsedDropsForBlock(Block block) {
        LuckyAddonData data = BLOCK_TO_DATA.get(block);
        if (data == null) return null;
        if (!AddonDisableStore.isEnabled(data.packId())) return Collections.emptyList();
        return data.parsedDrops();
    }

    /**
     * Returns the full {@link LuckyAddonData} for an addon block, or {@code null} if unknown.
     */
    public static LuckyAddonData getDataForBlock(Block block) {
        return BLOCK_TO_DATA.get(block);
    }

    public static Map<Block, String> getBlockToPackMap() {
        return Collections.unmodifiableMap(BLOCK_TO_PACK);
    }

    public static Map<String, LuckyAddonData> getBlockIdToAddonMap() {
        return Collections.unmodifiableMap(BLOCK_ID_TO_ADDON);
    }
}
