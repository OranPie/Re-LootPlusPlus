package ie.orangep.reLootplusplus.legacy.selector;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

public final class SelectorContext {
    private final ServerWorld world;
    private final BlockPos origin;
    private final Random random;
    private final SourceLoc sourceLoc;
    private final LegacyWarnReporter warnReporter;

    public SelectorContext(
        ServerWorld world,
        BlockPos origin,
        Random random,
        SourceLoc sourceLoc,
        LegacyWarnReporter warnReporter
    ) {
        this.world = world;
        this.origin = origin;
        this.random = random;
        this.sourceLoc = sourceLoc;
        this.warnReporter = warnReporter;
    }

    public ServerWorld world() {
        return world;
    }

    public BlockPos origin() {
        return origin;
    }

    public Random random() {
        return random;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }

    public LegacyWarnReporter warnReporter() {
        return warnReporter;
    }
}
