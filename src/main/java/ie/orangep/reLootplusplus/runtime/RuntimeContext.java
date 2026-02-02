package ie.orangep.reLootplusplus.runtime;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.util.Random;

public final class RuntimeContext {
    private final MinecraftServer server;
    private final ServerWorld world;
    private final Random random;
    private final LegacyWarnReporter warnReporter;

    public RuntimeContext(MinecraftServer server, ServerWorld world, Random random, LegacyWarnReporter warnReporter) {
        this.server = server;
        this.world = world;
        this.random = random;
        this.warnReporter = warnReporter;
    }

    public MinecraftServer server() {
        return server;
    }

    public ServerWorld world() {
        return world;
    }

    public Random random() {
        return random;
    }

    public LegacyWarnReporter warnReporter() {
        return warnReporter;
    }
}
