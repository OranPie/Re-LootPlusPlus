package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.client.screen.ReLootPlusPlusMenuScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void relootplusplus$addMenuButton(CallbackInfo ci) {
        GameMenuScreen self = (GameMenuScreen) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        int x = 8;
        int y = 8;
        if ((Object) this instanceof ScreenInvoker invoker) {
            invoker.relootplusplus$addDrawableChild(new ButtonWidget(x, y, 96, 20,
            new LiteralText("✦ Loot++"), button -> {
                client.setScreen(new ReLootPlusPlusMenuScreen(self));
            }));
        }
    }
}
