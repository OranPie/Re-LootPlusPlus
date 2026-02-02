package ie.orangep.reLootplusplus.command.exec;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

public final class ExecContext {
    private final ServerWorld world;
    private final BlockPos origin;
    private final Entity sender;
    private final Random random;
    private final SourceLoc sourceLoc;
    private final LegacyWarnReporter warnReporter;

    public ExecContext(
        ServerWorld world,
        BlockPos origin,
        Entity sender,
        Random random,
        SourceLoc sourceLoc,
        LegacyWarnReporter warnReporter
    ) {
        this.world = world;
        this.origin = origin;
        this.sender = sender;
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

    public Entity sender() {
        return sender;
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
