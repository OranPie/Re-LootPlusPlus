package ie.orangep.reLootplusplus.mixin;

import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ArmorItem;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin {
    @Inject(method = "getArmorTexture", at = @At("HEAD"), cancellable = true)
    private void relootplusplus$fixArmorTexture(ArmorItem item, boolean legs, String overlay, CallbackInfoReturnable<Identifier> cir) {
        String name = item.getMaterial().getName();
        if (name == null) {
            return;
        }
        String raw = name.replace('\\', '/');
        String prefix = "textures/models/armor/";
        String namespace = null;
        String base = null;
        int colon = raw.indexOf(':');
        if (raw.contains(prefix)) {
            int prefixIdx = raw.indexOf(prefix);
            if (colon > 0 && colon < prefixIdx) {
                namespace = raw.substring(0, colon);
                base = raw.substring(prefixIdx + prefix.length());
            } else {
                String material = raw.substring(prefixIdx + prefix.length());
                int colon2 = material.indexOf(':');
                if (colon2 > 0) {
                    namespace = material.substring(0, colon2);
                    base = material.substring(colon2 + 1);
                } else {
                    namespace = "minecraft";
                    base = material;
                }
            }
        } else if (colon > 0) {
            namespace = raw.substring(0, colon);
            base = raw.substring(colon + 1);
        }
        if (namespace == null || base == null) {
            return;
        }
        if (namespace.indexOf('/') >= 0) {
            namespace = namespace.substring(namespace.lastIndexOf('/') + 1);
        }
        if (namespace.isEmpty()) {
            namespace = "minecraft";
        }
        namespace = namespace.toLowerCase(Locale.ROOT);
        if (base.endsWith(".png")) {
            base = base.substring(0, base.length() - ".png".length());
        }
        if (base.endsWith("_layer_1")) {
            base = base.substring(0, base.length() - "_layer_1".length());
        } else if (base.endsWith("_layer_2")) {
            base = base.substring(0, base.length() - "_layer_2".length());
        }
        base = sanitizePath(base.toLowerCase(Locale.ROOT));
        String overlaySuffix = overlay == null ? "" : "_" + overlay;
        int layer = legs ? 2 : 1;
        String texturePath = "textures/models/armor/" + base + "_layer_" + layer + overlaySuffix + ".png";
        Identifier id = Identifier.tryParse(namespace + ":" + texturePath);
        if (id != null && hasResource(id)) {
            cir.setReturnValue(id);
            return;
        }
        String fallbackPath = "textures/models/armor/" + base + overlaySuffix + ".png";
        Identifier fallback = Identifier.tryParse(namespace + ":" + fallbackPath);
        if (fallback != null && hasResource(fallback)) {
            cir.setReturnValue(fallback);
            return;
        }
        if (id != null) {
            cir.setReturnValue(id);
        }
    }

    private static boolean hasResource(Identifier id) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getResourceManager() == null) {
            return false;
        }
        return client.getResourceManager().containsResource(id);
    }

    private static String sanitizePath(String path) {
        StringBuilder out = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '/' || c == '.' || c == '_' || c == '-') {
                out.append(c);
            } else {
                out.append('_');
            }
        }
        return out.toString();
    }
}
