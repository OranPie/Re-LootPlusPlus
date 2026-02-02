package ie.orangep.reLootplusplus.hooks;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.runtime.RuleEngine;
import ie.orangep.reLootplusplus.runtime.RuntimeContext;
import ie.orangep.reLootplusplus.runtime.trigger.TriggerType;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;

public final class AttackHook {
    private final RuleEngine ruleEngine;
    private final LegacyWarnReporter warnReporter;

    public AttackHook(RuleEngine ruleEngine, LegacyWarnReporter warnReporter) {
        this.ruleEngine = ruleEngine;
        this.warnReporter = warnReporter;
    }

    public void install() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            }
            RuntimeContext ctx = new RuntimeContext(world.getServer(), (ServerWorld) world, world.getRandom(), warnReporter);
            ruleEngine.executeForPlayer(ctx, player, TriggerType.HITTING_ENTITY_TO_ENTITY, entity.getBlockPos());
            ruleEngine.executeForPlayer(ctx, player, TriggerType.HITTING_ENTITY_TO_YOURSELF, player.getBlockPos());
            return ActionResult.PASS;
        });
    }
}
