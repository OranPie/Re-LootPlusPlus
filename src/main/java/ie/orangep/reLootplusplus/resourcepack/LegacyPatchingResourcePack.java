package ie.orangep.reLootplusplus.resourcepack;

import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public final class LegacyPatchingResourcePack implements ResourcePack {
    private final ResourcePack delegate;
    private final String name;

    public LegacyPatchingResourcePack(ResourcePack delegate, String name) {
        this.delegate = delegate;
        this.name = name;
    }

    @Override
    public InputStream openRoot(String fileName) throws IOException {
        return delegate.openRoot(fileName);
    }

    @Override
    public InputStream open(ResourceType type, Identifier id) throws IOException {
        InputStream patched = LegacyResourcePackPatcher.tryOpen(delegate, type, id, name);
        if (patched != null) {
            return patched;
        }
        return delegate.open(type, id);
    }

    @Override
    public Collection<Identifier> findResources(ResourceType type, String namespace, String prefix, int maxDepth, java.util.function.Predicate<String> pathFilter) {
        return LegacyResourcePackPatcher.findResources(delegate, type, namespace, prefix, maxDepth, pathFilter);
    }

    @Override
    public boolean contains(ResourceType type, Identifier id) {
        return LegacyResourcePackPatcher.contains(delegate, type, id);
    }

    @Override
    public Set<String> getNamespaces(ResourceType type) {
        if (type != ResourceType.CLIENT_RESOURCES) {
            return Collections.emptySet();
        }
        return delegate.getNamespaces(type);
    }

    @Override
    public <T> T parseMetadata(net.minecraft.resource.metadata.ResourceMetadataReader<T> metaReader) throws IOException {
        return delegate.parseMetadata(metaReader);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void close() {
        delegate.close();
    }
}
