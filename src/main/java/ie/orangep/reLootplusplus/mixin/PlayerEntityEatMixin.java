package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.runtime.RuleEngine;
import ie.orangep.reLootplusplus.runtime.RuntimeContext;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import ie.orangep.reLootplusplus.runtime.trigger.TriggerType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityEatMixin {
    @Inject(method = "eatFood", at = @At("TAIL"))
    private void relootplusplus$onEatFood(World world, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        if (world == null || world.isClient()) {
            return;
        }
        if (stack == null || stack.isEmpty() || !stack.isFood()) {
            return;
        }
        RuleEngine ruleEngine = RuntimeState.ruleEngine();
        if (ruleEngine == null) {
            return;
        }
        ServerWorld serverWorld = (ServerWorld) world;
        PlayerEntity player = (PlayerEntity) (Object) this;
        RuntimeContext ctx = new RuntimeContext(
            serverWorld.getServer(),
            serverWorld,
            serverWorld.getRandom(),
            RuntimeState.warnReporter()
        );
        ruleEngine.executeForItem(ctx, player, TriggerType.RIGHT_CLICK, stack, player.getBlockPos());
    }
}
