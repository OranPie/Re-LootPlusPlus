package ie.orangep.reLootplusplus.resourcepack;

import ie.orangep.reLootplusplus.config.AddonDisableStore;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.pack.AddonPack;
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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CombinedResourcePack implements ResourcePack {
    private static final int PACK_FORMAT = 8;
    private final String name;
    private final List<AddonPack> packs;
    private final Map<String, ResourcePack> delegates;

    public CombinedResourcePack(String name, List<AddonPack> packs) {
        this.name = name;
        this.packs = List.copyOf(packs);
        Map<String, ResourcePack> map = new LinkedHashMap<>();
        for (AddonPack pack : packs) {
            ResourcePack rp = Files.isDirectory(pack.zipPath())
                ? new ExternalDirResourcePack(pack.zipPath(), pack.id())
                : new ExternalZipResourcePack(pack.zipPath(), pack.id());
            map.put(pack.id(), rp);
        }
        this.delegates = map;
    }

    @Override
    public InputStream openRoot(String fileName) throws IOException {
        if (ResourcePack.PACK_METADATA_NAME.equals(fileName)) {
            return new ByteArrayInputStream(defaultPackMcmeta().getBytes(StandardCharsets.UTF_8));
        }
        for (ResourcePack pack : enabledPacks()) {
            try {
                InputStream stream = pack.openRoot(fileName);
                if (stream != null) {
                    return stream;
                }
            } catch (IOException ignored) {
            }
        }
        throw new IOException("File not found: " + fileName);
    }

    @Override
    public InputStream open(ResourceType type, Identifier id) throws IOException {
        for (ResourcePack pack : enabledPacks()) {
            if (pack.contains(type, id)) {
                return pack.open(type, id);
            }
        }
        throw new IOException("Resource not found: " + id);
    }

    @Override
    public Collection<Identifier> findResources(ResourceType type, String namespace, String prefix, int maxDepth,
                                                java.util.function.Predicate<String> pathFilter) {
        Set<Identifier> out = new LinkedHashSet<>();
        for (ResourcePack pack : enabledPacks()) {
            out.addAll(pack.findResources(type, namespace, prefix, maxDepth, pathFilter));
        }
        return out;
    }

    @Override
    public boolean contains(ResourceType type, Identifier id) {
        for (ResourcePack pack : enabledPacks()) {
            if (pack.contains(type, id)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<String> getNamespaces(ResourceType type) {
        if (type != ResourceType.CLIENT_RESOURCES) {
            return Collections.emptySet();
        }
        Set<String> out = new LinkedHashSet<>();
        for (ResourcePack pack : enabledPacks()) {
            out.addAll(pack.getNamespaces(type));
        }
        return out;
    }

    @Override
    public <T> T parseMetadata(ResourceMetadataReader<T> metaReader) throws IOException {
        if (metaReader == PackResourceMetadata.READER) {
            return (T) new PackResourceMetadata(new LiteralText("Re: Loot++资源合集"), PACK_FORMAT);
        }
        return null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void close() {
        for (ResourcePack pack : delegates.values()) {
            try {
                pack.close();
            } catch (Exception ignored) {
            }
        }
    }

    private List<ResourcePack> enabledPacks() {
        List<ResourcePack> out = new ArrayList<>();
        for (AddonPack pack : packs) {
            if (!AddonDisableStore.isEnabled(pack.id())) {
                continue;
            }
            ResourcePack delegate = delegates.get(pack.id());
            if (delegate != null) {
                out.add(delegate);
            }
        }
        if (out.isEmpty()) {
            Log.info("ResourcePack", "Combined resource pack has no enabled addons.");
        }
        return out;
    }

    private String defaultPackMcmeta() {
        return "{\"pack\":{\"pack_format\":" + PACK_FORMAT + ",\"description\":\"Re: Loot++资源合集\"}}";
    }
}
