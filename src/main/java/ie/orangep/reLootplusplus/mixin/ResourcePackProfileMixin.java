package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.resourcepack.LegacyPatchingResourcePack;
import ie.orangep.reLootplusplus.resourcepack.LegacyResourcePackPatcher;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResourcePackProfile.class)
public abstract class ResourcePackProfileMixin {
    @Shadow
    public abstract String getName();

    @Inject(method = "createResourcePack", at = @At("RETURN"), cancellable = true)
    private void relootplusplus$wrapLuckyPack(CallbackInfoReturnable<ResourcePack> cir) {
        ResourcePack pack = cir.getReturnValue();
        if (pack == null || pack instanceof LegacyPatchingResourcePack) {
            return;
        }
        String name = getName();
        if (!LegacyResourcePackPatcher.shouldWrapPackName(name)) {
            return;
        }
        cir.setReturnValue(new LegacyPatchingResourcePack(pack, name));
    }
}
