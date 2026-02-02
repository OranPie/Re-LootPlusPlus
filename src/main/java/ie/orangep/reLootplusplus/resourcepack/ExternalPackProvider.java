package ie.orangep.reLootplusplus.resourcepack;

import ie.orangep.reLootplusplus.config.ReLootPlusPlusConfig;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.pack.AddonPack;
import ie.orangep.reLootplusplus.pack.PackDiscovery;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackProvider;
import net.minecraft.resource.ResourcePackSource;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ExternalPackProvider implements ResourcePackProvider {
    private static final String ASSET_PREFIX = "assets/";

    @Override
    public void register(Consumer<ResourcePackProfile> profileAdder, ResourcePackProfile.Factory factory) {
        ReLootPlusPlusConfig config = ReLootPlusPlusConfig.load();
        if (config.dryRun) {
            Log.LOGGER.info("Re-LootPlusPlus dry run: skipping resource pack injection.");
            return;
        }
        if (!config.injectResourcePacks) {
            Log.LOGGER.info("Re-LootPlusPlus resource pack injection disabled.");
            return;
        }
        PackDiscovery discovery = new PackDiscovery(config);
        List<AddonPack> packs = discovery.discover();
        for (AddonPack pack : packs) {
            if (!hasAssets(pack.zipPath())) {
                continue;
            }
            Supplier<ResourcePack> supplier = () -> new ExternalZipResourcePack(pack.zipPath(), pack.id());
            String profileName = "relootplusplus:" + pack.id();
            ResourcePackProfile profile = ResourcePackProfile.of(
                profileName,
                true,
                supplier,
                factory,
                ResourcePackProfile.InsertionPosition.TOP,
                ResourcePackSource.PACK_SOURCE_BUILTIN
            );
            if (profile != null) {
                profileAdder.accept(profile);
            }
        }
    }

    private boolean hasAssets(Path zipPath) {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zip.getEntry(ASSET_PREFIX);
            if (entry != null) {
                return true;
            }
            return zip.stream().anyMatch(e -> e.getName().startsWith(ASSET_PREFIX));
        } catch (Exception e) {
            Log.warn("Failed to scan resource pack {}", zipPath, e);
            return false;
        }
    }
}
