package ie.orangep.reLootplusplus.mixin;

import net.minecraft.network.ClientConnection;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
    @Inject(method = "onPlayerConnect", at = @At("TAIL"))
    private void relootplusplus$unlockLegacyRecipes(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
        if (player == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        RecipeManager manager = server.getRecipeManager();
        if (manager == null) {
            return;
        }
        player.unlockRecipes(manager.values());
    }
}
