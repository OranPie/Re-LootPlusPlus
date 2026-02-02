package ie.orangep.reLootplusplus.hooks;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.runtime.RuleEngine;
import ie.orangep.reLootplusplus.runtime.RuntimeContext;
import ie.orangep.reLootplusplus.runtime.trigger.TriggerType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public final class ServerTickHook {
    private final RuleEngine ruleEngine;
    private final LegacyWarnReporter warnReporter;

    public ServerTickHook(RuleEngine ruleEngine, LegacyWarnReporter warnReporter) {
        this.ruleEngine = ruleEngine;
        this.warnReporter = warnReporter;
    }

    public void install() {
        ServerTickEvents.END_SERVER_TICK.register(this::onTick);
    }

    private void onTick(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            RuntimeContext runtimeContext = new RuntimeContext(server, world, world.getRandom(), warnReporter);
            for (ServerPlayerEntity player : world.getPlayers()) {
                ruleEngine.executeForPlayer(runtimeContext, player, TriggerType.HELD);
                ruleEngine.executeArmour(runtimeContext, player, TriggerType.WEARING_ARMOUR);
                ruleEngine.executeInventory(runtimeContext, player, TriggerType.IN_INVENTORY);
                ruleEngine.executeBlocksInInventory(runtimeContext, player);
                ruleEngine.executeStandingOnBlock(runtimeContext, player);
                ruleEngine.executeInsideBlock(runtimeContext, player);
            }
        }
    }
}
