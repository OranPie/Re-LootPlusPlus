package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.diagnostic.Log;
import mod.lucky.java.loader.ReadCraftingKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(value = mod.lucky.java.loader.LoaderKt.class, remap = false)
public abstract class LuckyAddonCraftingSafeMixin {
    @Redirect(
        method = "loadAddonResources",
        at = @At(
            value = "INVOKE",
            target = "Lmod/lucky/java/loader/ReadCraftingKt;readAddonCraftingRecipes(Ljava/util/List;Ljava/lang/String;)Ljava/util/List;"
        )
    )
    private static List<?> relootplusplus$readAddonCraftingRecipesSafe(List<String> lines, String blockId) {
        try {
            return ReadCraftingKt.readAddonCraftingRecipes(lines, blockId);
        } catch (RuntimeException e) {
            List<String> sanitized = sanitizeLines(lines);
            if (sanitized.size() != lines.size()) {
                try {
                    Log.warn("Recipe", "Retry addon recipes for {} after sanitizing lines ({} -> {})", blockId, lines.size(), sanitized.size());
                    return ReadCraftingKt.readAddonCraftingRecipes(sanitized, blockId);
                } catch (RuntimeException retry) {
                    Log.error("Recipe", "Failed to read addon recipes for {} even after sanitize", blockId, retry);
                    return Collections.emptyList();
                }
            }
            Log.error("Recipe", "Failed to read addon recipes for {}", blockId, e);
            return Collections.emptyList();
        }
    }

    private static List<String> sanitizeLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                continue;
            }
            if (!trimmed.contains("=")) {
                continue;
            }
            out.add(line);
        }
        return out;
    }
}
