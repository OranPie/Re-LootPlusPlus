package ie.orangep.reLootplusplus.lucky.worldgen;

import ie.orangep.reLootplusplus.lucky.block.NativeLuckyBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Places one Lucky Block (with optional luck value) at the feature origin during world generation.
 *
 * <p>One instance is registered per addon natural-gen entry. The block and luck level are baked
 * into the feature instance, with {@link DefaultFeatureConfig} used as a no-op config to avoid
 * Codec boilerplate for runtime-only features.
 */
public final class LuckyNaturalGenFeature extends Feature<DefaultFeatureConfig> {

    private final Block block;
    private final int luck;

    public LuckyNaturalGenFeature(Block block, int luck) {
        super(DefaultFeatureConfig.CODEC);
        this.block = block;
        this.luck = luck;
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        BlockPos pos = context.getOrigin();
        StructureWorldAccess world = context.getWorld();

        // Only place if the target spot is air (avoid overwriting existing blocks)
        if (!world.isAir(pos)) return false;

        world.setBlockState(pos, block.getDefaultState(), Block.NOTIFY_LISTENERS);

        if (luck != 0) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof NativeLuckyBlockEntity lbe) {
                lbe.setLuck(luck);
                lbe.markDirty();
            }
        }
        return true;
    }
}
