package ie.orangep.reLootplusplus.resourcepack;

import ie.orangep.reLootplusplus.lucky.registry.AddonLuckyRegistrar;
import ie.orangep.reLootplusplus.lucky.loader.LuckyAddonData;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.metadata.PackResourceMetadata;
import net.minecraft.resource.metadata.ResourceMetadataReader;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A synthetic {@link ResourcePack} that provides blockstate and model JSON for
 * addon-registered Lucky Block variants that do not supply their own assets.
 *
 * <p>The pack is injected on the client BEFORE addon zip packs, so if an addon zip
 * already provides its own blockstate/model the zip pack takes priority via MC's layering.
 */
public final class AddonBlockResourcePack implements ResourcePack {

    private static final int PACK_FORMAT = 8;
    private static final String NAME = "relootplusplus:addon_blocks_synthetic";

    @Override
    public InputStream openRoot(String fileName) throws IOException {
        if (ResourcePack.PACK_METADATA_NAME.equals(fileName)) {
            String mcmeta = "{\"pack\":{\"pack_format\":" + PACK_FORMAT
                + ",\"description\":\"Re-LootPlusPlus synthetic addon block assets\"}}";
            return new ByteArrayInputStream(mcmeta.getBytes(StandardCharsets.UTF_8));
        }
        throw new IOException("Not found: " + fileName);
    }

    @Override
    public InputStream open(ResourceType type, Identifier id) throws IOException {
        if (type != ResourceType.CLIENT_RESOURCES) throw new IOException("Not a client resource: " + id);
        String json = generateJson(id);
        if (json == null) throw new IOException("Not found: " + id);
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Collection<Identifier> findResources(ResourceType type, String namespace, String prefix,
                                                int maxDepth, java.util.function.Predicate<String> pathFilter) {
        if (type != ResourceType.CLIENT_RESOURCES || !"lucky".equals(namespace)) {
            return Collections.emptyList();
        }
        Set<Identifier> out = new LinkedHashSet<>();
        for (String blockId : getAddonBlockIds()) {
            List<Identifier> candidates = List.of(
                new Identifier("lucky", "blockstates/" + blockId + ".json"),
                new Identifier("lucky", "models/block/" + blockId + ".json"),
                new Identifier("lucky", "models/item/" + blockId + ".json")
            );
            for (Identifier id : candidates) {
                if (id.getPath().startsWith(prefix) && pathFilter.test(id.getPath())) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    @Override
    public boolean contains(ResourceType type, Identifier id) {
        if (type != ResourceType.CLIENT_RESOURCES) return false;
        return generateJson(id) != null;
    }

    @Override
    public Set<String> getNamespaces(ResourceType type) {
        if (type != ResourceType.CLIENT_RESOURCES) return Collections.emptySet();
        if (getAddonBlockIds().isEmpty()) return Collections.emptySet();
        return Set.of("lucky");
    }

    @Override
    public <T> T parseMetadata(ResourceMetadataReader<T> metaReader) throws IOException {
        if (metaReader == PackResourceMetadata.READER) {
            return (T) new PackResourceMetadata(
                new LiteralText("Re-LootPlusPlus addon blocks"), PACK_FORMAT);
        }
        return null;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void close() {}

    // -------------------------------------------------------------------------
    // JSON generation
    // -------------------------------------------------------------------------

    private String generateJson(Identifier id) {
        if (!"lucky".equals(id.getNamespace())) return null;
        String path = id.getPath();

        if (path.startsWith("blockstates/") && path.endsWith(".json")) {
            String blockId = path.substring("blockstates/".length(), path.length() - ".json".length());
            if (isAddonBlock(blockId)) {
                return "{\"variants\":{\"\":{\"model\":\"lucky:block/" + blockId + "\"}}}";
            }
        } else if (path.startsWith("models/block/") && path.endsWith(".json")) {
            String blockId = path.substring("models/block/".length(), path.length() - ".json".length());
            if (isAddonBlock(blockId)) {
                String texture = hasCustomTexture(blockId) ? "lucky:block/" + blockId : "lucky:block/lucky_block";
                return "{\"parent\":\"block/cube_all\",\"textures\":{\"all\":\"" + texture + "\"}}";
            }
        } else if (path.startsWith("models/item/") && path.endsWith(".json")) {
            String blockId = path.substring("models/item/".length(), path.length() - ".json".length());
            if (isAddonBlock(blockId)) {
                return "{\"parent\":\"lucky:block/" + blockId + "\"}";
            }
        }
        return null;
    }

    private boolean isAddonBlock(String blockId) {
        return AddonLuckyRegistrar.getBlockIdToAddonMap().containsKey(blockId);
    }

    private Set<String> getAddonBlockIds() {
        return AddonLuckyRegistrar.getBlockIdToAddonMap().keySet();
    }

    /**
     * Checks if the addon zip provides a texture at the modern or legacy path.
     */
    private boolean hasCustomTexture(String blockId) {
        LuckyAddonData data = AddonLuckyRegistrar.getBlockIdToAddonMap().get(blockId);
        if (data == null || data.pack() == null || data.pack().zipPath() == null) return false;
        java.nio.file.Path zipPath = data.pack().zipPath();
        if (!java.nio.file.Files.isRegularFile(zipPath)) return false;
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            ZipEntry modern = zip.getEntry("assets/lucky/textures/block/" + blockId + ".png");
            ZipEntry legacy = zip.getEntry("assets/lucky/textures/blocks/" + blockId + ".png");
            return modern != null || legacy != null;
        } catch (Exception e) {
            return false;
        }
    }
}
