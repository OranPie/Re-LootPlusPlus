package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.config.ReLootPlusPlusConfig;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PackScreen.class)
public abstract class PackScreenMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void relootplusplus$addInjectionToggle(CallbackInfo ci) {
        PackScreen self = (PackScreen) (Object) this;
        ReLootPlusPlusConfig config = ReLootPlusPlusConfig.load();
        int x = self.width - 190;
        int y = 8;
        Text label = injectionLabel(config.injectResourcePacks);
        ButtonWidget button = new ButtonWidget(x, y, 180, 20, label, btn -> {
            ReLootPlusPlusConfig updated = ReLootPlusPlusConfig.load();
            updated.injectResourcePacks = !updated.injectResourcePacks;
            updated.save();
            btn.setMessage(injectionLabel(updated.injectResourcePacks));
            if ((Object) this instanceof PackScreenInvoker invoker) {
                invoker.relootplusplus$refresh();
            }
        });
        if ((Object) this instanceof ScreenInvoker invoker) {
            invoker.relootplusplus$addDrawableChild(button);
        }
    }

    private Text injectionLabel(boolean enabled) {
        return new LiteralText("[Re:Loot++ Injection] ").append(enabled ? "ON" : "OFF");
    }
}
