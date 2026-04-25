package ie.orangep.reLootplusplus.client;

import ie.orangep.reLootplusplus.registry.EntityRegistrar;
import ie.orangep.reLootplusplus.registry.CreativeMenuRegistrar;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

public class ReLootPlusPlusClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Run luckyCompat client entrypoints first (registers screen factories etc.)
        FabricLoader.getInstance()
            .getEntrypoints("re-lpp:lucky-client", ClientModInitializer.class)
            .forEach(ClientModInitializer::onInitializeClient);

        EntityRendererRegistry.register(EntityRegistrar.THROWN_ENTITY, FlyingItemEntityRenderer::new);
        new CreativeMenuRegistrar().register(RuntimeState.creativeMenuEntries());
    }
}
