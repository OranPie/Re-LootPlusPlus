package ie.orangep.reLootplusplus.resourcepack;

import ie.orangep.reLootplusplus.config.TextureAdditionStore;
import ie.orangep.reLootplusplus.diagnostic.Log;
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
import java.nio.file.Path;
import java.util.*;

/**
 * A {@link ResourcePack} that reads assets from an unpacked addon directory.
 *
 * <p>Mirrors {@link ExternalZipResourcePack} but uses the {@link Files} API instead
 * of a ZIP archive.  The same {@link LegacyResourcePackPatcher} pipeline is applied.
 *
 * <p>The asset index is built lazily on first use and cached for the lifetime of the
 * pack object.  The index is keyed by <em>lower-cased</em> relative asset path
 * (e.g. {@code assets/lucky/textures/blocks/lucky_block.png}) and maps to the
 * actual {@link Path} on disk.
 */
public final class ExternalDirResourcePack implements ResourcePack {

    private static final int PACK_FORMAT = 8;

    private final Path   dir;
    private final String name;
    private final ResourcePack rawPack;

    /** Lower-cased asset path → actual file on disk. */
    private volatile Map<String, Path>   assetIndex;
    /** namespace/lang/name.json → lower-cased asset key (may point at a .lang file). */
    private volatile Map<String, String> langIndexCache;

    public ExternalDirResourcePack(Path dir, String name) {
        this.dir     = dir;
        this.name    = name;
        this.rawPack = new RawDirResourcePack();
    }

    // ── ResourcePack ─────────────────────────────────────────────────────────

    @Override
    public InputStream openRoot(String fileName) throws IOException {
        if (ResourcePack.PACK_METADATA_NAME.equals(fileName)) {
            return new ByteArrayInputStream(defaultPackMcmeta().getBytes(StandardCharsets.UTF_8));
        }
        // Try actual file first, then nested inside subdirectory
        Path file = dir.resolve(fileName);
        if (Files.isRegularFile(file)) {
            return new ByteArrayInputStream(Files.readAllBytes(file));
        }
        throw new IOException("Root file not found: " + fileName);
    }

    @Override
    public InputStream open(ResourceType type, Identifier id) throws IOException {
        if (type == ResourceType.CLIENT_RESOURCES) {
            InputStream lang = tryOpenLegacyLang(id);
            if (lang != null) return lang;
        }
        InputStream patched = LegacyResourcePackPatcher.tryOpen(rawPack, type, id, name);
        if (patched != null) return patched;
        try {
            return rawPack.open(type, id);
        } catch (IOException e) {
            if (type == ResourceType.CLIENT_RESOURCES) {
                InputStream alt = tryOpenAlternateTexture(id);
                if (alt != null) return alt;
            }
            throw e;
        }
    }

    @Override
    public Collection<Identifier> findResources(ResourceType type, String namespace, String prefix,
                                                int maxDepth,
                                                java.util.function.Predicate<String> pathFilter) {
        if (type == ResourceType.CLIENT_RESOURCES && prefix.startsWith("lang")) {
            return findLegacyLangResources(namespace, prefix, pathFilter);
        }
        return LegacyResourcePackPatcher.findResources(rawPack, type, namespace, prefix, maxDepth, pathFilter);
    }

    @Override
    public boolean contains(ResourceType type, Identifier id) {
        if (type == ResourceType.CLIENT_RESOURCES) {
            String path = id.getPath();
            if (path.startsWith("lang/") && path.endsWith(".json")) {
                if (langIndex().containsKey(buildLangKey(id))) return true;
            }
        }
        return LegacyResourcePackPatcher.contains(rawPack, type, id);
    }

    @Override
    public Set<String> getNamespaces(ResourceType type) {
        if (type != ResourceType.CLIENT_RESOURCES) return Collections.emptySet();
        return rawPack.getNamespaces(type);
    }

    @Override
    public <T> T parseMetadata(ResourceMetadataReader<T> metaReader) throws IOException {
        if (metaReader == PackResourceMetadata.READER) {
            return (T) new PackResourceMetadata(new LiteralText("Loot++ addon pack"), PACK_FORMAT);
        }
        return null;
    }

    @Override
    public String getName() { return name; }

    @Override
    public void close() { /* no file handles to close */ }

    // ── Index building ────────────────────────────────────────────────────────

    private Map<String, Path> assetIndex() {
        Map<String, Path> cached = assetIndex;
        if (cached != null) return cached;
        synchronized (this) {
            if (assetIndex != null) return assetIndex;
            assetIndex = buildAssetIndex();
            return assetIndex;
        }
    }

    private Map<String, Path> buildAssetIndex() {
        Map<String, Path> map = new LinkedHashMap<>();
        try {
            Files.walk(dir).forEach(file -> {
                if (!Files.isRegularFile(file)) return;
                String rel = dir.relativize(file).toString().replace('\\', '/');
                String lower = rel.toLowerCase(Locale.ROOT);
                // Handle nested packs: strip prefix up to "assets/"
                int idx = lower.indexOf("assets/");
                if (idx < 0) return;
                String assetPath = lower.substring(idx);
                map.putIfAbsent(assetPath, file);
            });
        } catch (IOException e) {
            Log.error("ResourcePack", "Failed to scan assets dir {}", dir, e);
        }
        return map;
    }

    private Map<String, String> langIndex() {
        Map<String, String> cached = langIndexCache;
        if (cached != null) return cached;
        synchronized (this) {
            if (langIndexCache != null) return langIndexCache;
            langIndexCache = buildLangIndex();
            return langIndexCache;
        }
    }

    private Map<String, String> buildLangIndex() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String assetPath : assetIndex().keySet()) {
            if (!assetPath.startsWith("assets/")) continue;
            int nsStart = "assets/".length();
            int slash   = assetPath.indexOf('/', nsStart);
            if (slash <= nsStart) continue;
            String namespace = assetPath.substring(nsStart, slash);
            String rel       = assetPath.substring(slash + 1);
            if (!rel.startsWith("lang/")) continue;
            String jsonKey;
            if (rel.endsWith(".lang")) {
                jsonKey = rel.substring(0, rel.length() - ".lang".length()) + ".json";
            } else if (rel.endsWith(".json")) {
                jsonKey = rel;
            } else {
                continue;
            }
            map.putIfAbsent(namespace + "/" + jsonKey, assetPath);
        }
        return map;
    }

    // ── Lang helpers ──────────────────────────────────────────────────────────

    private String buildLangKey(Identifier id) {
        return id.getNamespace().toLowerCase(Locale.ROOT) + "/"
            + id.getPath().toLowerCase(Locale.ROOT);
    }

    private InputStream tryOpenLegacyLang(Identifier id) throws IOException {
        if (!id.getPath().startsWith("lang/") || !id.getPath().endsWith(".json")) return null;
        String assetKey = langIndex().get(buildLangKey(id));
        if (assetKey == null) return null;
        Path file = assetIndex().get(assetKey);
        if (file == null) return null;
        if (assetKey.endsWith(".lang")) {
            try (InputStream raw = Files.newInputStream(file)) {
                String json = LegacyResourcePackPatcher.convertLegacyLang(raw, id.getNamespace());
                return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            }
        }
        return new ByteArrayInputStream(Files.readAllBytes(file));
    }

    private Collection<Identifier> findLegacyLangResources(String namespace, String prefix,
                                                            java.util.function.Predicate<String> pathFilter) {
        List<Identifier> result = new ArrayList<>();
        String nsPrefix    = namespace.toLowerCase(Locale.ROOT) + "/";
        String prefixLower = prefix.toLowerCase(Locale.ROOT);
        for (String key : langIndex().keySet()) {
            if (!key.startsWith(nsPrefix)) continue;
            String path = key.substring(nsPrefix.length());
            if (!path.startsWith(prefixLower)) continue;
            if (!pathFilter.test(path)) continue;
            result.add(new Identifier(namespace, path));
        }
        return result;
    }

    // ── Texture fallback helpers ──────────────────────────────────────────────

    private InputStream tryOpenAlternateTexture(Identifier id) {
        if (!id.getPath().startsWith("textures/")) return null;
        // Custom remap
        String remapped = TextureAdditionStore.load().mapTextureRef(
            id.getNamespace() + ":" + id.getPath().substring(
                "textures/".length(), id.getPath().length() - ".png".length()));
        if (remapped != null) {
            int colon = remapped.indexOf(':');
            if (colon > 0) {
                String ns  = remapped.substring(0, colon);
                String rel = remapped.substring(colon + 1);
                InputStream s = openAssetEntry(
                    "assets/" + ns.toLowerCase(Locale.ROOT) + "/textures/" + rel + ".png");
                if (s != null) return s;
            }
        }
        // Legacy blocks/items path swap
        String key = buildAssetKey(ResourceType.CLIENT_RESOURCES, id);
        String alt = alternateLegacyPath(key);
        if (alt != null) {
            InputStream s = openAssetEntry(alt);
            if (s != null) return s;
        }
        // Filename-based fallback
        return resolveByFileName(key);
    }

    private String buildAssetKey(ResourceType type, Identifier id) {
        return type.getDirectory() + "/"
            + id.getNamespace().toLowerCase(Locale.ROOT) + "/"
            + id.getPath().toLowerCase(Locale.ROOT);
    }

    private String alternateLegacyPath(String assetKey) {
        if (assetKey.contains("textures/item/"))   return assetKey.replace("textures/item/",   "textures/items/");
        if (assetKey.contains("textures/items/"))  return assetKey.replace("textures/items/",  "textures/item/");
        if (assetKey.contains("textures/block/"))  return assetKey.replace("textures/block/",  "textures/blocks/");
        if (assetKey.contains("textures/blocks/")) return assetKey.replace("textures/blocks/", "textures/block/");
        return null;
    }

    private InputStream openAssetEntry(String assetKey) {
        Path file = assetIndex().get(assetKey.toLowerCase(Locale.ROOT));
        if (file == null) return null;
        try {
            return new ByteArrayInputStream(Files.readAllBytes(file));
        } catch (IOException e) {
            return null;
        }
    }

    private InputStream resolveByFileName(String assetKey) {
        int slash = assetKey.lastIndexOf('/');
        if (slash < 0 || slash >= assetKey.length() - 1) return null;
        String fileName = assetKey.substring(slash + 1);
        for (Map.Entry<String, Path> e : assetIndex().entrySet()) {
            String k = e.getKey();
            if (!k.contains("/textures/")) continue;
            if (k.endsWith("/" + fileName)) {
                try {
                    return new ByteArrayInputStream(Files.readAllBytes(e.getValue()));
                } catch (IOException ex) {
                    // try next
                }
            }
        }
        return null;
    }

    private String defaultPackMcmeta() {
        return "{\"pack\":{\"pack_format\":" + PACK_FORMAT + ",\"description\":\"Loot++ addon pack\"}}";
    }

    // ── Inner raw pack (no legacy patching) ───────────────────────────────────

    private final class RawDirResourcePack implements ResourcePack {

        @Override
        public InputStream openRoot(String fileName) throws IOException {
            return ExternalDirResourcePack.this.openRoot(fileName);
        }

        @Override
        public InputStream open(ResourceType type, Identifier id) throws IOException {
            String key  = buildAssetKey(type, id);
            Path   file = assetIndex().get(key);
            if (file == null) throw new IOException("Resource not found: " + id);
            return new ByteArrayInputStream(Files.readAllBytes(file));
        }

        @Override
        public Collection<Identifier> findResources(ResourceType type, String namespace, String prefix,
                                                    int maxDepth,
                                                    java.util.function.Predicate<String> pathFilter) {
            String dirKey      = type.getDirectory().toLowerCase(Locale.ROOT) + "/";
            String ns          = namespace.toLowerCase(Locale.ROOT);
            String base        = dirKey + ns + "/";
            String prefixLower = prefix.toLowerCase(Locale.ROOT);
            List<Identifier> result = new ArrayList<>();
            for (String assetPath : assetIndex().keySet()) {
                if (!assetPath.startsWith(base)) continue;
                String rel = assetPath.substring(base.length());
                if (!rel.startsWith(prefixLower)) continue;
                if (!pathFilter.test(rel)) continue;
                String remainder = rel.substring(prefixLower.length());
                if (remainder.startsWith("/")) remainder = remainder.substring(1);
                if (maxDepth >= 0 && !remainder.isEmpty()) {
                    int depth = 1;
                    for (int i = 0; i < remainder.length(); i++) {
                        if (remainder.charAt(i) == '/') depth++;
                    }
                    if (depth > maxDepth) continue;
                }
                Identifier parsed = Identifier.tryParse(namespace + ":" + rel);
                if (parsed != null) result.add(parsed);
            }
            return result;
        }

        @Override
        public boolean contains(ResourceType type, Identifier id) {
            return assetIndex().containsKey(buildAssetKey(type, id));
        }

        @Override
        public Set<String> getNamespaces(ResourceType type) {
            if (type != ResourceType.CLIENT_RESOURCES) return Collections.emptySet();
            Set<String> namespaces = new LinkedHashSet<>();
            String dirKey = type.getDirectory().toLowerCase(Locale.ROOT) + "/";
            for (String assetPath : assetIndex().keySet()) {
                if (!assetPath.startsWith(dirKey)) continue;
                int nsStart = dirKey.length();
                int nsEnd   = assetPath.indexOf('/', nsStart);
                if (nsEnd <= nsStart) continue;
                namespaces.add(assetPath.substring(nsStart, nsEnd));
            }
            return namespaces;
        }

        @Override
        public <T> T parseMetadata(ResourceMetadataReader<T> reader) throws IOException {
            return null;
        }

        @Override
        public String getName() { return name; }

        @Override
        public void close() { }
    }
}
