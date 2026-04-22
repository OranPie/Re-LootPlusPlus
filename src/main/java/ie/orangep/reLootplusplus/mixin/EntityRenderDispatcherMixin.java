package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    private static final Set<String> MISSING_RENDERERS = ConcurrentHashMap.newKeySet();

    @Shadow
    public abstract EntityRenderer<? super Entity> getRenderer(Entity entity);

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void relootplusplus$skipMissingRenderer(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        EntityRenderer<? super Entity> renderer = getRenderer(entity);
        if (renderer != null) {
            return;
        }
        Identifier id = Registry.ENTITY_TYPE.getId(entity.getType());
        String key = id == null ? "unknown" : id.toString();
        if (MISSING_RENDERERS.add(key)) {
            warnOnce("MissingRenderer", "no renderer for " + key);
        }
        cir.setReturnValue(false);
    }

    private static void warnOnce(String type, String detail) {
        LegacyWarnReporter reporter = RuntimeState.warnReporter();
        if (reporter != null) {
            reporter.warnOnce(type, detail, null);
            return;
        }
        Log.warn("Legacy", "{} {}", type, detail);
    }
}
