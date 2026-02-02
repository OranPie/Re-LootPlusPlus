package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.resourcepack.ResourcePackUiHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.MultilineText;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.LiteralText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(PackListWidget.ResourcePackEntry.class)
public abstract class ResourcePackEntryMixin {
    @Shadow @Final private ResourcePackOrganizer.Pack pack;
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final @Mutable private MultilineText description;
    @Shadow @Final @Mutable private OrderedText incompatibleText;
    @Shadow @Final @Mutable private MultilineText compatibilityNotificationText;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void relootplusplus$decorateEntry(MinecraftClient client, PackListWidget widget, Screen screen,
                                              ResourcePackOrganizer.Pack pack, CallbackInfo ci) {
        ResourcePackProfile profile = null;
        if (pack instanceof ResourcePackProfileAccessor accessor) {
            profile = accessor.relootplusplus$getProfile();
        }
        if (!ResourcePackUiHelper.isInjectedPack(profile)) {
            return;
        }
        incompatibleText = OrderedText.EMPTY;
        compatibilityNotificationText = MultilineText.EMPTY;
        Text label = new LiteralText("[Re:Loot++ Injection]").formatted(Formatting.GRAY);
        List<Text> lines = new ArrayList<>();
        lines.add(label);
        if (profile != null && profile.getDescription() != null) {
            lines.add(profile.getDescription());
        }
        description = MultilineText.createFromTexts(client.textRenderer, lines);
    }
}
