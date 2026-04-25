package ie.orangep.reLootplusplus.lucky.drop;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Runtime context passed to the Lucky drop evaluation pipeline.
 */
public record LuckyDropContext(
    ServerWorld world,
    BlockPos pos,
    PlayerEntity player,
    int luck,
    LegacyWarnReporter warnReporter,
    SourceLoc sourceLoc
) {
    public LuckyDropContext withLuck(int newLuck) {
        return new LuckyDropContext(world, pos, player, newLuck, warnReporter, sourceLoc);
    }
}
