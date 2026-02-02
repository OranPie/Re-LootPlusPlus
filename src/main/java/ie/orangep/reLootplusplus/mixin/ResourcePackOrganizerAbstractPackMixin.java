package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.resourcepack.ResourcePackUiHelper;
import net.minecraft.resource.ResourcePackCompatibility;
import net.minecraft.resource.ResourcePackProfile;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.gui.screen.pack.ResourcePackOrganizer$AbstractPack")
public abstract class ResourcePackOrganizerAbstractPackMixin {
    @Shadow @Final private ResourcePackProfile profile;

    @Inject(method = "getCompatibility", at = @At("HEAD"), cancellable = true)
    private void relootplusplus$forceCompatible(CallbackInfoReturnable<ResourcePackCompatibility> cir) {
        if (ResourcePackUiHelper.isInjectedPack(profile)) {
            cir.setReturnValue(ResourcePackCompatibility.COMPATIBLE);
        }
    }
}
