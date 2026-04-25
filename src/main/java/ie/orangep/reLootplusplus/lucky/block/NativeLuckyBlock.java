package ie.orangep.reLootplusplus.lucky.block;

import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropEngine;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.loader.LuckyAddonData;
import ie.orangep.reLootplusplus.lucky.loader.LuckyAddonLoader;
import ie.orangep.reLootplusplus.lucky.registry.AddonLuckyRegistrar;
import ie.orangep.reLootplusplus.lucky.registry.LuckyRegistrar;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * The native Lucky Block.
 *
 * <p>On break by a player, reads luck + custom drops from the block entity and
 * dispatches to {@link LuckyDropEngine}.
 */
public final class NativeLuckyBlock extends BlockWithEntity {

    public NativeLuckyBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new NativeLuckyBlockEntity(LuckyRegistrar.LUCKY_BLOCK_ENTITY_TYPE, pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return null; // no tick needed
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        // Fire drops on server side only
        if (!world.isClient() && world instanceof ServerWorld serverWorld) {
            // If this is an addon Lucky Block whose pack is currently disabled, fire no drops
            if (AddonLuckyRegistrar.isAddonBlock(state.getBlock())
                    && !AddonLuckyRegistrar.isAddonEnabled(state.getBlock())) {
                super.onBreak(world, pos, state, player);
                return;
            }

            // Respect doDropsOnCreativeMode from the addon's properties.txt
            boolean isCreative = player.isCreative();
            if (isCreative) {
                // Find the relevant addon data to check the property
                boolean dropInCreative = false;
                LuckyAddonData addonData = AddonLuckyRegistrar.getDataForBlock(state.getBlock());
                if (addonData != null) {
                    dropInCreative = addonData.effectiveProperties().doDropsOnCreativeMode();
                } else {
                    // Base lucky block — use the default (false)
                    dropInCreative = false;
                }
                if (!dropInCreative) {
                    super.onBreak(world, pos, state, player);
                    return;
                }
            }
            int luck = 0;
            // Custom drops from block entity — must be parsed at break time (no pre-parsed cache)
            List<String> customDropRaws = null;
            // Pre-parsed drops for normal (non-custom) evaluation
            List<LuckyDropLine> drops = null;

            // Apply defaultLuck from config as baseline
            ie.orangep.reLootplusplus.config.ReLootPlusPlusConfig lbCfg = RuntimeState.config();
            if (lbCfg != null) luck = lbCfg.defaultLuck;

            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof NativeLuckyBlockEntity luckyBe) {
                luck = luckyBe.getLuck() + (lbCfg != null ? lbCfg.defaultLuck : 0);
                customDropRaws = luckyBe.getCustomDrops();
            }

            if (customDropRaws != null && !customDropRaws.isEmpty()) {
                // Custom drops: parse at break time (block entity drops aren't pre-cached)
                // (drops stays null; evaluated below via evaluateRaw)
            } else {
                // Try per-addon pre-parsed drops (addon-specific lucky blocks)
                drops = AddonLuckyRegistrar.getParsedDropsForBlock(state.getBlock());
                if (drops == null || drops.isEmpty()) {
                    // Fall back to merged addon drops (base lucky:lucky_block)
                    drops = LuckyAddonLoader.getMergedDrops();
                }
                if (drops == null || drops.isEmpty()) {
                    // Final fallback: base game drops from config/lucky/
                    drops = LuckyAddonLoader.getBaseDrops();
                }
            }

            Log.debug("LuckyDrop", String.format("[BREAK] pos=%s luck=%d drops=%d blockState=%s",
                pos.toShortString(), luck,
                customDropRaws != null ? customDropRaws.size() : (drops != null ? drops.size() : 0),
                world.getBlockState(pos).getBlock().getTranslationKey()));

            // IMPORTANT: call super FIRST to remove the lucky block from the world
            final List<String> finalCustomDropRaws = customDropRaws;
            final List<LuckyDropLine> finalDrops = drops;
            final int finalLuck = luck;
            super.onBreak(world, pos, state, player);

            Log.debug("LuckyDrop", String.format("[BREAK] block removed, now blockState=%s — evaluating drops...",
                world.getBlockState(pos).getBlock().getTranslationKey()));

            SourceLoc loc = new SourceLoc("lucky", "lucky_block_break", pos.toShortString(), 0, "");
            LuckyDropContext ctx = new LuckyDropContext(
                serverWorld, pos, player, finalLuck,
                RuntimeState.warnReporter(), loc
            );
            if (finalCustomDropRaws != null && !finalCustomDropRaws.isEmpty()) {
                LuckyDropEngine.evaluateRaw(ctx, finalCustomDropRaws);
            } else {
                LuckyDropEngine.evaluate(ctx, finalDrops);
            }
            return; // super already called above
        }

        super.onBreak(world, pos, state, player);
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, net.minecraft.loot.context.LootContext.Builder builder) {
        // Lucky block does not drop itself unless silk touch
        // Check silk touch
        ItemStack tool = builder.getNullable(net.minecraft.loot.context.LootContextParameters.TOOL);
        if (tool != null && net.minecraft.enchantment.EnchantmentHelper.getLevel(
            net.minecraft.enchantment.Enchantments.SILK_TOUCH, tool) > 0) {
            return Collections.singletonList(new ItemStack(LuckyRegistrar.LUCKY_BLOCK_ITEM));
        }
        return Collections.emptyList();
    }

    @Override
    public boolean shouldDropItemsOnExplosion(Explosion explosion) {
        return false; // Lucky block broken by explosion does not evaluate drops
    }
}
