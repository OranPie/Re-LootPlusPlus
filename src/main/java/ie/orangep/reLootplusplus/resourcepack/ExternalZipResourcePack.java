package ie.orangep.reLootplusplus.resourcepack;

import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.ZipResourcePack;
import net.minecraft.resource.metadata.PackResourceMetadata;
import net.minecraft.resource.metadata.ResourceMetadataReader;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import ie.orangep.reLootplusplus.diagnostic.Log;

public final class ExternalZipResourcePack implements ResourcePack {
    private static final int PACK_FORMAT = 8;
    private final String name;
    private final Path zipPath;
    private final ZipResourcePack delegate;
    private volatile Map<String, String> langEntryMap;

    public ExternalZipResourcePack(Path zipPath, String name) {
        this.name = name;
        this.zipPath = zipPath;
        this.delegate = new ZipResourcePack(zipPath.toFile());
    }

    @Override
    public InputStream openRoot(String fileName) throws IOException {
        if (ResourcePack.PACK_METADATA_NAME.equals(fileName)) {
            return new ByteArrayInputStream(defaultPackMcmeta().getBytes(StandardCharsets.UTF_8));
        }
        return delegate.openRoot(fileName);
    }

    @Override
    public InputStream open(ResourceType type, Identifier id) throws IOException {
        if (type == ResourceType.CLIENT_RESOURCES) {
            InputStream lang = tryOpenLegacyLang(id);
            if (lang != null) {
                return lang;
            }
        }
        InputStream patched = LegacyResourcePackPatcher.tryOpen(delegate, type, id, name);
        if (patched != null) {
            return patched;
        }
        return delegate.open(type, id);
    }

    @Override
    public Collection<Identifier> findResources(ResourceType type, String namespace, String prefix, int maxDepth, java.util.function.Predicate<String> pathFilter) {
        if (type == ResourceType.CLIENT_RESOURCES && prefix.startsWith("lang")) {
            return findLegacyLangResources(namespace, prefix, pathFilter);
        }
        return LegacyResourcePackPatcher.findResources(delegate, type, namespace, prefix, maxDepth, pathFilter);
    }

    @Override
    public boolean contains(ResourceType type, Identifier id) {
        if (type == ResourceType.CLIENT_RESOURCES && id.getPath().startsWith("lang/") && id.getPath().endsWith(".json")) {
            String key = buildLangKey(id);
            if (langEntries().containsKey(key)) {
                return true;
            }
        }
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
    public <T> T parseMetadata(ResourceMetadataReader<T> metaReader) throws IOException {
        if (metaReader == PackResourceMetadata.READER) {
            PackResourceMetadata metadata = delegate.parseMetadata(PackResourceMetadata.READER);
            if (metadata != null) {
                return (T) metadata;
            }
            return (T) new PackResourceMetadata(new LiteralText("Loot++ addon pack"), PACK_FORMAT);
        }
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

    private String defaultPackMcmeta() {
        return "{\"pack\":{\"pack_format\":" + PACK_FORMAT + ",\"description\":\"Loot++ addon pack\"}}";
    }

    private Collection<Identifier> findLegacyLangResources(String namespace, String prefix, java.util.function.Predicate<String> pathFilter) {
        Map<String, String> map = langEntries();
        if (map.isEmpty()) {
            return Collections.emptyList();
        }
        String prefixLower = prefix.toLowerCase(Locale.ROOT);
        Collection<Identifier> result = new java.util.ArrayList<>();
        String nsPrefix = namespace.toLowerCase(Locale.ROOT) + "/";
        for (String key : map.keySet()) {
            if (!key.startsWith(nsPrefix)) {
                continue;
            }
            String path = key.substring(nsPrefix.length());
            if (!path.startsWith(prefixLower)) {
                continue;
            }
            if (!pathFilter.test(path)) {
                continue;
            }
            result.add(new Identifier(namespace, path));
        }
        return result;
    }

    private InputStream tryOpenLegacyLang(Identifier id) throws IOException {
        String path = id.getPath();
        if (!path.startsWith("lang/") || !path.endsWith(".json")) {
            return null;
        }
        String key = buildLangKey(id);
        String entryName = langEntries().get(key);
        if (entryName == null) {
            return null;
        }
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                return null;
            }
            try (InputStream raw = zip.getInputStream(entry)) {
                if (entryName.toLowerCase(Locale.ROOT).endsWith(".lang")) {
                    String json = LegacyResourcePackPatcher.convertLegacyLang(raw);
                    return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
                }
                byte[] data = raw.readAllBytes();
                return new ByteArrayInputStream(data);
            }
        }
    }

    private String buildLangKey(Identifier id) {
        String path = id.getPath().toLowerCase(Locale.ROOT);
        return id.getNamespace().toLowerCase(Locale.ROOT) + "/" + path;
    }

    private Map<String, String> langEntries() {
        Map<String, String> cached = langEntryMap;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (langEntryMap != null) {
                return langEntryMap;
            }
            langEntryMap = scanLangEntries();
            return langEntryMap;
        }
    }

    private Map<String, String> scanLangEntries() {
        Map<String, String> map = new HashMap<>();
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            zip.stream().forEach(entry -> {
                String name = entry.getName();
                if (!name.startsWith("assets/")) {
                    return;
                }
                int nsStart = "assets/".length();
                int slash = name.indexOf('/', nsStart);
                if (slash <= nsStart) {
                    return;
                }
                String namespace = name.substring(nsStart, slash);
                String rel = name.substring(slash + 1);
                String lowerRel = rel.toLowerCase(Locale.ROOT);
                if (!lowerRel.startsWith("lang/")) {
                    return;
                }
                String jsonPath;
                if (lowerRel.endsWith(".lang")) {
                    jsonPath = lowerRel.substring(0, lowerRel.length() - ".lang".length()) + ".json";
                } else if (lowerRel.endsWith(".json")) {
                    jsonPath = lowerRel;
                } else {
                    return;
                }
                String key = namespace.toLowerCase(Locale.ROOT) + "/" + jsonPath;
                map.putIfAbsent(key, name);
            });
        } catch (Exception e) {
            Log.warn("Failed to scan legacy lang entries in {}", zipPath, e);
        }
        return map;
    }
}
