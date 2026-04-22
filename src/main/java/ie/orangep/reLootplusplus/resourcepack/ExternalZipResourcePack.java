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
    private final ResourcePack rawPack;
    private volatile Map<String, String> langEntryMap;
    private volatile Map<String, String> assetEntryMap;
    private volatile Map<String, java.util.List<String>> assetFileIndex;
    private volatile Map<String, java.util.List<String>> assetFileIndexNormalized;

    public ExternalZipResourcePack(Path zipPath, String name) {
        this.name = name;
        this.zipPath = zipPath;
        this.delegate = new ZipResourcePack(zipPath.toFile());
        this.rawPack = new IndexedZipResourcePack();
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
        InputStream patched = LegacyResourcePackPatcher.tryOpen(rawPack, type, id, name);
        if (patched != null) {
            return patched;
        }
        try {
            return rawPack.open(type, id);
        } catch (IOException e) {
            if (type == ResourceType.CLIENT_RESOURCES) {
                InputStream alt = tryOpenAlternateTexture(id);
                if (alt != null) {
                    Log.warn("ResourcePack", "Resolved texture {} using zip path in {}", id, zipPath);
                    return alt;
                }
                logMissingResource(type, id);
            }
            throw e;
        }
    }

    @Override
    public Collection<Identifier> findResources(ResourceType type, String namespace, String prefix, int maxDepth, java.util.function.Predicate<String> pathFilter) {
        if (type == ResourceType.CLIENT_RESOURCES && prefix.startsWith("lang")) {
            return findLegacyLangResources(namespace, prefix, pathFilter);
        }
        return LegacyResourcePackPatcher.findResources(rawPack, type, namespace, prefix, maxDepth, pathFilter);
    }

    @Override
    public boolean contains(ResourceType type, Identifier id) {
        if (type == ResourceType.CLIENT_RESOURCES && id.getPath().startsWith("lang/") && id.getPath().endsWith(".json")) {
            String key = buildLangKey(id);
            if (langEntries().containsKey(key)) {
                return true;
            }
        }
        return LegacyResourcePackPatcher.contains(rawPack, type, id);
    }

    @Override
    public Set<String> getNamespaces(ResourceType type) {
        if (type != ResourceType.CLIENT_RESOURCES) {
            return Collections.emptySet();
        }
        return rawPack.getNamespaces(type);
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
                    String json = LegacyResourcePackPatcher.convertLegacyLang(raw, id.getNamespace());
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
        for (Map.Entry<String, String> entry : assetEntries().entrySet()) {
            String assetPath = entry.getKey();
            if (!assetPath.startsWith("assets/")) {
                continue;
            }
            int nsStart = "assets/".length();
            int slash = assetPath.indexOf('/', nsStart);
            if (slash <= nsStart) {
                continue;
            }
            String namespace = assetPath.substring(nsStart, slash);
            String rel = assetPath.substring(slash + 1);
            if (!rel.startsWith("lang/")) {
                continue;
            }
            String jsonPath;
            if (rel.endsWith(".lang")) {
                jsonPath = rel.substring(0, rel.length() - ".lang".length()) + ".json";
            } else if (rel.endsWith(".json")) {
                jsonPath = rel;
            } else {
                continue;
            }
            String key = namespace + "/" + jsonPath;
            map.putIfAbsent(key, entry.getValue());
        }
        return map;
    }

    private Map<String, String> assetEntries() {
        Map<String, String> cached = assetEntryMap;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (assetEntryMap != null) {
                return assetEntryMap;
            }
            assetEntryMap = scanAssetEntries();
            return assetEntryMap;
        }
    }

    private Map<String, String> scanAssetEntries() {
        Map<String, String> map = new HashMap<>();
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            zip.stream().forEach(entry -> {
                if (entry.isDirectory()) {
                    return;
                }
                String name = entry.getName();
                String lower = name.toLowerCase(Locale.ROOT);
                int idx = lower.indexOf("assets/");
                if (idx < 0) {
                    return;
                }
                String assetPath = name.substring(idx);
                map.putIfAbsent(assetPath.toLowerCase(Locale.ROOT), name);
            });
        } catch (Exception e) {
            Log.error("ResourcePack", "Failed to scan assets in {}", zipPath, e);
        }
        return map;
    }

    private Map<String, java.util.List<String>> assetFileIndex() {
        Map<String, java.util.List<String>> cached = assetFileIndex;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (assetFileIndex != null) {
                return assetFileIndex;
            }
            Map<String, java.util.List<String>> built = new HashMap<>();
            Map<String, java.util.List<String>> builtNormalized = new HashMap<>();
            for (String assetPath : assetEntries().keySet()) {
                int slash = assetPath.lastIndexOf('/');
                if (slash < 0 || slash >= assetPath.length() - 1) {
                    continue;
                }
                String file = assetPath.substring(slash + 1);
                built.computeIfAbsent(file, k -> new java.util.ArrayList<>()).add(assetPath);
                String normalized = normalizeFileName(file);
                builtNormalized.computeIfAbsent(normalized, k -> new java.util.ArrayList<>()).add(assetPath);
            }
            assetFileIndex = built;
            assetFileIndexNormalized = builtNormalized;
            return assetFileIndex;
        }
    }

    private String buildAssetKey(ResourceType type, Identifier id) {
        String dir = type.getDirectory();
        String namespace = id.getNamespace().toLowerCase(Locale.ROOT);
        String path = id.getPath().toLowerCase(Locale.ROOT);
        return dir + "/" + namespace + "/" + path;
    }

    private void logMissingResource(ResourceType type, Identifier id) {
        String assetKey = buildAssetKey(type, id);
        Map<String, String> entries = assetEntries();
        if (entries.containsKey(assetKey)) {
            return;
        }
        java.util.List<String> hints = new java.util.ArrayList<>();
        String alt = alternateLegacyPath(assetKey);
        if (alt != null) {
            String entry = entries.get(alt);
            if (entry != null) {
                hints.add("found-alt=" + entry);
            }
        }
        String samePath = findSamePathOtherNamespace(type, id);
        if (samePath != null) {
            hints.add("found-namespace=" + samePath);
        }
        java.util.List<String> byName = findByFileName(assetKey);
        if (!byName.isEmpty()) {
            hints.add("found-name=" + String.join(", ", byName));
        }
        if (hints.isEmpty()) {
            Log.error("ResourcePack", "Missing resource {} in pack {} (zip={})", id, name, zipPath);
        } else {
            Log.error("ResourcePack", "Missing resource {} in pack {} (zip={}) {}", id, name, zipPath, String.join(" ", hints));
        }
    }

    private InputStream tryOpenAlternateTexture(Identifier id) {
        if (id == null) {
            return null;
        }
        String path = id.getPath();
        if (path == null || !path.startsWith("textures/")) {
            return null;
        }
        String remapped = ie.orangep.reLootplusplus.config.TextureAdditionStore.load().mapTextureRef(
            id.getNamespace() + ":" + path.substring("textures/".length(), path.length() - ".png".length())
        );
        if (remapped != null) {
            String remappedKey = remapped.replace('\\', '/');
            int idx = remappedKey.indexOf(':');
            if (idx > 0 && idx < remappedKey.length() - 1) {
                String ns = remappedKey.substring(0, idx);
                String rel = remappedKey.substring(idx + 1);
                String targetKey = "assets/" + ns.toLowerCase(java.util.Locale.ROOT) + "/textures/" + rel + ".png";
                String entry = assetEntries().get(targetKey.toLowerCase(java.util.Locale.ROOT));
                if (entry != null) {
                    return openAssetEntry(entry);
                }
            }
        }
        String key = buildAssetKey(ResourceType.CLIENT_RESOURCES, id);
        String alt = alternateLegacyPath(key);
        if (alt != null) {
            String entry = assetEntries().get(alt);
            if (entry != null) {
                return openAssetEntry(entry);
            }
        }
        String entry = resolveByFileName(key);
        if (entry != null) {
            return openAssetEntry(entry);
        }
        return null;
    }

    private String resolveByFileName(String assetKey) {
        int slash = assetKey.lastIndexOf('/');
        if (slash < 0 || slash >= assetKey.length() - 1) {
            return null;
        }
        String file = assetKey.substring(slash + 1);
        java.util.List<String> hits = assetFileIndex().get(file);
        if (hits == null || hits.isEmpty()) {
            String normalized = normalizeFileName(file);
            hits = assetFileIndexNormalized.get(normalized);
        }
        if (hits == null || hits.isEmpty()) {
            return null;
        }
        for (String candidate : hits) {
            if (candidate.contains("/textures/")) {
                return assetEntries().get(candidate);
            }
        }
        return assetEntries().get(hits.get(0));
    }

    private InputStream openAssetEntry(String entry) {
        if (entry == null) {
            return null;
        }
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            ZipEntry zipEntry = zip.getEntry(entry);
            if (zipEntry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(zipEntry)) {
                byte[] data = in.readAllBytes();
                return new ByteArrayInputStream(data);
            }
        } catch (IOException e) {
            return null;
        }
    }

    private String alternateLegacyPath(String assetKey) {
        if (assetKey.contains("textures/item/")) {
            return assetKey.replace("textures/item/", "textures/items/");
        }
        if (assetKey.contains("textures/items/")) {
            return assetKey.replace("textures/items/", "textures/item/");
        }
        if (assetKey.contains("textures/block/")) {
            return assetKey.replace("textures/block/", "textures/blocks/");
        }
        if (assetKey.contains("textures/blocks/")) {
            return assetKey.replace("textures/blocks/", "textures/block/");
        }
        return null;
    }

    private java.util.List<String> findByFileName(String assetKey) {
        int slash = assetKey.lastIndexOf('/');
        if (slash < 0 || slash >= assetKey.length() - 1) {
            return java.util.Collections.emptyList();
        }
        String file = assetKey.substring(slash + 1);
        java.util.List<String> hits = assetFileIndex().get(file);
        if (hits == null || hits.isEmpty()) {
            String normalized = normalizeFileName(file);
            hits = assetFileIndexNormalized.get(normalized);
            if (hits == null || hits.isEmpty()) {
                return java.util.Collections.emptyList();
            }
        }
        java.util.List<String> trimmed = new java.util.ArrayList<>();
        int limit = Math.min(3, hits.size());
        for (int i = 0; i < limit; i++) {
            trimmed.add(hits.get(i));
        }
        return trimmed;
    }

    private String normalizeFileName(String file) {
        String lower = file.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == '_' || c == '-' || c == '.') {
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private String findSamePathOtherNamespace(ResourceType type, Identifier id) {
        String path = id.getPath().toLowerCase(Locale.ROOT);
        String dir = type.getDirectory() + "/";
        String suffix = "/" + path;
        for (String assetPath : assetEntries().keySet()) {
            if (!assetPath.startsWith(dir) || !assetPath.endsWith(suffix)) {
                continue;
            }
            int nsStart = dir.length();
            int nsEnd = assetPath.indexOf('/', nsStart);
            if (nsEnd <= nsStart) {
                continue;
            }
            String namespace = assetPath.substring(nsStart, nsEnd);
            if (!namespace.equals(id.getNamespace().toLowerCase(Locale.ROOT))) {
                return assetPath;
            }
        }
        return null;
    }

    private InputStream openAsset(ResourceType type, Identifier id) throws IOException {
        String key = buildAssetKey(type, id);
        String entry = assetEntries().get(key);
        if (entry == null) {
            throw new IOException("Resource not found: " + id);
        }
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            ZipEntry zipEntry = zip.getEntry(entry);
            if (zipEntry == null) {
                throw new IOException("Resource not found: " + id);
            }
            try (InputStream in = zip.getInputStream(zipEntry)) {
                byte[] data = in.readAllBytes();
                return new ByteArrayInputStream(data);
            }
        }
    }

    private Collection<Identifier> findResourcesRaw(ResourceType type, String namespace, String prefix, int maxDepth,
                                                    java.util.function.Predicate<String> pathFilter) {
        String dir = type.getDirectory().toLowerCase(Locale.ROOT) + "/";
        String ns = namespace.toLowerCase(Locale.ROOT);
        String base = dir + ns + "/";
        String prefixLower = prefix.toLowerCase(Locale.ROOT);
        Collection<Identifier> result = new java.util.ArrayList<>();
        for (String assetPath : assetEntries().keySet()) {
            if (!assetPath.startsWith(base)) {
                continue;
            }
            String rel = assetPath.substring(base.length());
            if (!rel.startsWith(prefixLower)) {
                continue;
            }
            if (!pathFilter.test(rel)) {
                continue;
            }
            String remainder = rel.substring(prefixLower.length());
            if (remainder.startsWith("/")) {
                remainder = remainder.substring(1);
            }
            if (maxDepth >= 0 && !remainder.isEmpty()) {
                int depth = 1;
                for (int i = 0; i < remainder.length(); i++) {
                    if (remainder.charAt(i) == '/') {
                        depth++;
                    }
                }
                if (depth > maxDepth) {
                    continue;
                }
            }
            Identifier id = Identifier.tryParse(namespace + ":" + rel);
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    private Set<String> getNamespacesRaw(ResourceType type) {
        if (type != ResourceType.CLIENT_RESOURCES) {
            return Collections.emptySet();
        }
        Set<String> namespaces = new java.util.LinkedHashSet<>();
        String dir = type.getDirectory().toLowerCase(Locale.ROOT) + "/";
        for (String assetPath : assetEntries().keySet()) {
            if (!assetPath.startsWith(dir)) {
                continue;
            }
            int nsStart = dir.length();
            int nsEnd = assetPath.indexOf('/', nsStart);
            if (nsEnd <= nsStart) {
                continue;
            }
            namespaces.add(assetPath.substring(nsStart, nsEnd));
        }
        return namespaces;
    }

    private final class IndexedZipResourcePack implements ResourcePack {
        @Override
        public InputStream openRoot(String fileName) throws IOException {
            return delegate.openRoot(fileName);
        }

        @Override
        public InputStream open(ResourceType type, Identifier id) throws IOException {
            return openAsset(type, id);
        }

        @Override
        public Collection<Identifier> findResources(ResourceType type, String namespace, String prefix, int maxDepth,
                                                    java.util.function.Predicate<String> pathFilter) {
            return findResourcesRaw(type, namespace, prefix, maxDepth, pathFilter);
        }

        @Override
        public boolean contains(ResourceType type, Identifier id) {
            String key = buildAssetKey(type, id);
            return assetEntries().containsKey(key);
        }

        @Override
        public Set<String> getNamespaces(ResourceType type) {
            return getNamespacesRaw(type);
        }

        @Override
        public <T> T parseMetadata(ResourceMetadataReader<T> metaReader) throws IOException {
            return delegate.parseMetadata(metaReader);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void close() {
        }
    }
}
