package ie.orangep.reLootplusplus.resourcepack;

import ie.orangep.reLootplusplus.config.ReLootPlusPlusConfig;
import ie.orangep.reLootplusplus.mixin.ResourcePackProfileAccessor;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import net.minecraft.resource.ResourcePackProfile;

import java.util.Locale;

public final class ResourcePackUiHelper {
    private ResourcePackUiHelper() {
    }

    public static boolean isInjectedPack(ResourcePackProfile profile) {
        if (profile == null) {
            return false;
        }
        String name = profile.getName();
        if (name == null) {
            return false;
        }
        return name.toLowerCase(Locale.ROOT).startsWith("relootplusplus:");
    }

    public static boolean isInjectionEnabled() {
        return ReLootPlusPlusConfig.load().injectResourcePacks;
    }

    public static boolean shouldShowPack(ResourcePackOrganizer.Pack pack) {
        if (pack instanceof ResourcePackProfileAccessor accessor) {
            ResourcePackProfile profile = accessor.relootplusplus$getProfile();
            if (isInjectedPack(profile) && !isInjectionEnabled()) {
                return false;
            }
        }
        return true;
    }
}
