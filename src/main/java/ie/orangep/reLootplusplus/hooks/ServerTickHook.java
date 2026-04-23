package ie.orangep.reLootplusplus.hooks;

import ie.orangep.reLootplusplus.config.ReLootPlusPlusConfig;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.runtime.RuleEngine;
import ie.orangep.reLootplusplus.runtime.RuntimeContext;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import ie.orangep.reLootplusplus.runtime.trigger.TriggerType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.List;

public final class ServerTickHook {
    private final RuleEngine ruleEngine;
    private final LegacyWarnReporter warnReporter;
    private int tickCount = 0;

    public ServerTickHook(RuleEngine ruleEngine, LegacyWarnReporter warnReporter) {
        this.ruleEngine = ruleEngine;
        this.warnReporter = warnReporter;
    }

    public void install() {
        ServerTickEvents.END_SERVER_TICK.register(this::onTick);
    }

    private void onTick(MinecraftServer server) {
        ReLootPlusPlusConfig cfg = RuntimeState.config();
        int interval = cfg != null ? cfg.tickIntervalTicks : 1;
        if (++tickCount % interval != 0) return;

        List<String> enabledTypes = (cfg != null && cfg.enabledTriggerTypes != null)
            ? cfg.enabledTriggerTypes : List.of();
        boolean allEnabled = enabledTypes.isEmpty();

        for (ServerWorld world : server.getWorlds()) {
            RuntimeContext runtimeContext = new RuntimeContext(server, world, world.getRandom(), warnReporter);
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (allEnabled || enabledTypes.contains("held"))
                    ruleEngine.executeForPlayer(runtimeContext, player, TriggerType.HELD);
                if (allEnabled || enabledTypes.contains("wearing_armour"))
                    ruleEngine.executeArmour(runtimeContext, player, TriggerType.WEARING_ARMOUR);
                if (allEnabled || enabledTypes.contains("in_inventory"))
                    ruleEngine.executeInventory(runtimeContext, player, TriggerType.IN_INVENTORY);
                if (allEnabled || enabledTypes.contains("standing_on_block") || enabledTypes.contains("in_inventory"))
                    ruleEngine.executeBlocksInInventory(runtimeContext, player);
                if (allEnabled || enabledTypes.contains("standing_on_block"))
                    ruleEngine.executeStandingOnBlock(runtimeContext, player);
                if (allEnabled || enabledTypes.contains("inside_block"))
                    ruleEngine.executeInsideBlock(runtimeContext, player);
            }
        }
    }
}
