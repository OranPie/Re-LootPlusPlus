package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.resourcepack.ExternalPackProvider;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.Arrays;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @ModifyArgs(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/resource/ResourcePackManager;<init>(Lnet/minecraft/resource/ResourcePackProfile$Factory;[Lnet/minecraft/resource/ResourcePackProvider;)V"
        )
    )
    private void relootplusplus$injectResourcePackProvider(Args args) {
        ResourcePackProvider[] providers = args.get(1);
        ResourcePackProvider[] updated = Arrays.copyOf(providers, providers.length + 1);
        updated[updated.length - 1] = new ExternalPackProvider();
        args.set(1, updated);
    }
}
