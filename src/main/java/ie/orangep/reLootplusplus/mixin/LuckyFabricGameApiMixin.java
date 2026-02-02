package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.legacy.mapping.LegacyEntityIdFixer;
import ie.orangep.reLootplusplus.legacy.mapping.LegacyItemNbtFixer;
import ie.orangep.reLootplusplus.legacy.mapping.LegacyParticleIdMapper;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import mod.lucky.common.Vec3;
import mod.lucky.common.attribute.DictAttr;
import mod.lucky.fabric.FabricGameAPI;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(value = FabricGameAPI.class, remap = false)
public abstract class LuckyFabricGameApiMixin {
    @Inject(
        method = "spawnEntity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/EntityType;loadEntityWithPassengers(Lnet/minecraft/nbt/NbtCompound;Lnet/minecraft/world/World;Ljava/util/function/Function;)Lnet/minecraft/entity/Entity;"
        ),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void relootplusplus$fixLegacyEntityIds(
        Object world,
        String id,
        Vec3<Double> pos,
        DictAttr nbt,
        DictAttr components,
        double rotation,
        boolean randomizeMob,
        Object player,
        String sourceId,
        CallbackInfo ci,
        DictAttr entityNBT,
        NbtCompound mcEntityNBT,
        ServerWorld serverWorld
    ) {
        LegacyWarnReporter reporter = RuntimeState.warnReporter();
        LegacyEntityIdFixer.fixEntityId(mcEntityNBT, reporter, sourceId);
    }

    @ModifyArg(
        method = "dropItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/Identifier;<init>(Ljava/lang/String;)V"
        ),
        index = 0
    )
    private String relootplusplus$normalizeDropItemId(String id) {
        LegacyWarnReporter reporter = RuntimeState.warnReporter();
        String normalized = LegacyEntityIdFixer.normalizeItemId(id, reporter, null);
        if (normalized == null || normalized.isEmpty()) {
            return "minecraft:air";
        }
        if (net.minecraft.util.Identifier.tryParse(normalized) == null) {
            return "minecraft:air";
        }
        return normalized;
    }

    @ModifyArg(
        method = "dropItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;setNbt(Lnet/minecraft/nbt/NbtCompound;)V"
        ),
        index = 0
    )
    private NbtCompound relootplusplus$fixDropItemNbt(NbtCompound nbt) {
        LegacyWarnReporter reporter = RuntimeState.warnReporter();
        LegacyItemNbtFixer.fixItemStack(nbt, reporter, null);
        return nbt;
    }

    @ModifyArg(
        method = "spawnParticle",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/Identifier;<init>(Ljava/lang/String;)V"
        ),
        index = 0
    )
    private String relootplusplus$normalizeParticleId(String id) {
        LegacyWarnReporter reporter = RuntimeState.warnReporter();
        return LegacyParticleIdMapper.normalizeId(id, reporter);
    }
}
