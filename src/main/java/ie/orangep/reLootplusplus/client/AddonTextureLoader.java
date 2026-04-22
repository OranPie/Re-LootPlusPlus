package ie.orangep.reLootplusplus.client;

import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.pack.AddonPack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipFile;

/**
 * Client-side loader that reads PNG bytes from addon pack zip/dir files and
 * registers them as {@link NativeImageBackedTexture} with Minecraft's TextureManager.
 *
 * <p>All public methods must be called from the <b>render thread</b>.
 * Textures are loaded lazily on first request and cached by {@code packId:innerPath}.
 */
public final class AddonTextureLoader {

    /** Sentinel stored in cache when a path has permanently failed to load. */
    private static final Identifier MISSING = new Identifier("re-lootplusplus", "tex/_missing");

    private static final Map<String, Identifier> idCache  = new HashMap<>();
    private static final Map<String, int[]>      dimCache = new HashMap<>();

    private AddonTextureLoader() {}

    /**
     * Returns the registered {@link Identifier} for the given texture, loading it
     * on first call.  Returns {@code null} if loading failed permanently.
     *
     * @param pack      the addon pack containing the texture
     * @param innerPath path inside zip/dir, e.g. {@code assets/lootplusplus/textures/blocks/foo.png}
     */
    @Nullable
    public static Identifier getOrLoad(AddonPack pack, String innerPath) {
        String key = key(pack.id(), innerPath);
        Identifier cached = idCache.get(key);
        if (cached != null) return cached == MISSING ? null : cached;

        try {
            NativeImage img = readImage(pack, innerPath);
            if (img == null) { idCache.put(key, MISSING); return null; }

            Identifier id = buildId(pack.id(), innerPath);
            dimCache.put(key, new int[]{img.getWidth(), img.getHeight()});
            MinecraftClient.getInstance().getTextureManager()
                    .registerTexture(id, new NativeImageBackedTexture(img));
            idCache.put(key, id);
            return id;
        } catch (Exception e) {
            Log.warn("TexLoader", "Failed to load {} from {}: {}", innerPath, pack.id(), e.getMessage());
            idCache.put(key, MISSING);
            return null;
        }
    }

    /**
     * Returns {@code [width, height]} for a previously loaded texture, or {@code null}.
     */
    @Nullable
    public static int[] getDims(AddonPack pack, String innerPath) {
        return dimCache.get(key(pack.id(), innerPath));
    }

    /**
     * Releases all GPU textures belonging to {@code packId} and clears the cache.
     */
    public static void evictPack(String packId) {
        String prefix = packId + ":";
        idCache.entrySet().removeIf(e -> {
            if (!e.getKey().startsWith(prefix)) return false;
            Identifier id = e.getValue();
            if (id != null && id != MISSING) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null) mc.getTextureManager().destroyTexture(id);
            }
            dimCache.remove(e.getKey());
            return true;
        });
    }

    // ── private ──────────────────────────────────────────────────────────────────

    private static String key(String packId, String innerPath) {
        return packId + ":" + innerPath;
    }

    private static @Nullable NativeImage readImage(AddonPack pack, String innerPath) throws Exception {
        Path zipPath = pack.zipPath();
        if (zipPath == null) return null;
        if (Files.isDirectory(zipPath)) {
            Path file = zipPath.resolve(innerPath);
            if (!Files.isRegularFile(file)) return null;
            try (InputStream in = Files.newInputStream(file)) {
                return NativeImage.read(in);
            }
        }
        if (Files.isRegularFile(zipPath)) {
            try (ZipFile zip = new ZipFile(zipPath.toFile())) {
                var entry = zip.getEntry(innerPath);
                if (entry == null) return null;
                try (InputStream in = zip.getInputStream(entry)) {
                    return NativeImage.read(in);
                }
            }
        }
        return null;
    }

    /**
     * Converts a pack-id + inner-path pair into a valid Minecraft {@link Identifier}.
     * Characters outside {@code [a-z0-9._/]} are replaced with {@code _}.
     */
    private static Identifier buildId(String packId, String innerPath) {
        String path = innerPath.startsWith("assets/") ? innerPath.substring(7) : innerPath;
        path = path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._/]", "_");
        String ns = packId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._]", "_");
        String full = "tex/" + ns + "/" + path;
        if (full.length() > 220) {
            full = "tex/" + ns + "/" + Integer.toHexString(innerPath.hashCode()) + ".png";
        }
        return new Identifier("re-lootplusplus", full);
    }
}
