package ie.orangep.reLootplusplus.mixin;

import net.minecraft.resource.ResourcePackProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.screen.pack.ResourcePackOrganizer$AbstractPack")
public interface ResourcePackProfileAccessor {
    @Accessor("profile")
    ResourcePackProfile relootplusplus$getProfile();
}
