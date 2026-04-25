package ie.orangep.reLootplusplus.bootstrap;

import ie.orangep.reLootplusplus.client.screen.ReLootPlusPlusDropLinesScreen;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.loader.LuckyAddonLoader;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.Screen;

import java.util.Collections;
import java.util.List;

/**
 * Client-side initializer for the LuckyBlock compat module.
 * Registers the DropLines screen factory so the core UI can open it without
 * importing any lucky-package types.
 *
 * Registered via the {@code re-lpp:lucky-client} Fabric entrypoint.
 */
public final class LuckyCompatClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        LuckyCompat.setDropLinesScreenFactory((parent, packId) -> {
            String id = (String) packId;
            var data = LuckyAddonLoader.getAddonDataList().stream()
                .filter(d -> d.packId().equals(id)).findFirst().orElse(null);
            List<LuckyDropLine> drops = data != null ? data.parsedDrops() : Collections.emptyList();
            return new ReLootPlusPlusDropLinesScreen((Screen) parent, id, drops);
        });
    }
}
