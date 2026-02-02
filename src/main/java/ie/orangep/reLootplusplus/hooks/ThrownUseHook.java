package ie.orangep.reLootplusplus.hooks;

import ie.orangep.reLootplusplus.command.LegacyCommandRunner;
import ie.orangep.reLootplusplus.content.entity.LootThrownItemEntity;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.runtime.RuleEngine;
import ie.orangep.reLootplusplus.runtime.ThrownRegistry;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.registry.Registry;

public final class ThrownUseHook {
    private final RuleEngine ruleEngine;
    private final ThrownRegistry thrownRegistry;
    private final LegacyWarnReporter warnReporter;

    public ThrownUseHook(RuleEngine ruleEngine, ThrownRegistry thrownRegistry, LegacyWarnReporter warnReporter) {
        this.ruleEngine = ruleEngine;
        this.thrownRegistry = thrownRegistry;
        this.warnReporter = warnReporter;
    }

    public void install() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) {
                return TypedActionResult.pass(player.getStackInHand(hand));
            }
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isEmpty()) {
                return TypedActionResult.pass(player.getStackInHand(hand));
            }
            String itemId = Registry.ITEM.getId(stack.getItem()).toString();
            var def = thrownRegistry.get(itemId);
            if (def == null) {
                return TypedActionResult.pass(player.getStackInHand(hand));
            }
            spawnThrown((ServerWorld) world, player, stack, def);
            return TypedActionResult.success(player.getStackInHand(hand));
        });
    }

    private void spawnThrown(ServerWorld world, PlayerEntity player, ItemStack stack, ie.orangep.reLootplusplus.config.model.rule.ThrownDef def) {
        LootThrownItemEntity entity = new LootThrownItemEntity(
            ie.orangep.reLootplusplus.registry.EntityRegistrar.THROWN_ENTITY,
            world,
            player,
            def,
            new LegacyCommandRunner(),
            warnReporter
        );
        entity.setItem(stack.copy());
        entity.setVelocity(player, player.getPitch(), player.getYaw(), 0.0f, def.velocity(), def.inaccuracy());
        boolean spawned = world.spawnEntity(entity);
        if (spawned && !player.getAbilities().creativeMode) {
            stack.decrement(1);
        } else if (!spawned && warnReporter != null) {
            warnReporter.warn("LegacyThrown", "spawn failed for " + def.itemId(), def.sourceLoc());
        }
    }
}
