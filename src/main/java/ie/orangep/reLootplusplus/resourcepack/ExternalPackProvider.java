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
            Log.info("ResourcePack", "Dry run: skipping resource pack injection.");
            return;
        }
        if (!config.injectResourcePacks) {
            Log.info("ResourcePack", "Resource pack injection disabled.");
            return;
        }

        // Inject synthetic blockstate/model pack for addon blocks BEFORE addon zips
        ResourcePackProfile syntheticProfile = ResourcePackProfile.of(
            "relootplusplus:addon_blocks_synthetic",
            true,
            () -> new AddonBlockResourcePack(),
            factory,
            ResourcePackProfile.InsertionPosition.TOP,
            ResourcePackSource.PACK_SOURCE_BUILTIN
        );
        if (syntheticProfile != null) {
            profileAdder.accept(syntheticProfile);
        }

        PackDiscovery discovery = new PackDiscovery(config);
        List<AddonPack> packs = discovery.discoverAll();
        List<AddonPack> assetPacks = new java.util.ArrayList<>();
        for (AddonPack pack : packs) {
            if (!hasAssets(pack.zipPath())) {
                continue;
            }
            assetPacks.add(pack);
        }
        if (assetPacks.isEmpty()) {
            return;
        }
        Supplier<ResourcePack> supplier = () -> new CombinedResourcePack("relootplusplus:bundle", assetPacks);
        ResourcePackProfile profile = ResourcePackProfile.of(
            "relootplusplus:bundle",
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

    private boolean hasAssets(Path zipPath) {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            // Match both top-level "assets/" and nested "SomePack/assets/" structures
            return zip.stream().anyMatch(e -> {
                String name = e.getName();
                return name.startsWith(ASSET_PREFIX) || name.contains("/" + ASSET_PREFIX);
            });
        } catch (Exception e) {
            Log.error("ResourcePack", "Failed to scan resource pack {}", zipPath, e);
            return false;
        }
    }
}
