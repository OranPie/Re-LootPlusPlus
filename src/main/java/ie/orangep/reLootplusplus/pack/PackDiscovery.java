package ie.orangep.reLootplusplus.pack;

import ie.orangep.reLootplusplus.config.AddonDisableStore;
import ie.orangep.reLootplusplus.config.ReLootPlusPlusConfig;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

public final class PackDiscovery {
    private static final String ENV_ADDON_DIR = "RELOOTPLUSPLUS_ADDONS";
    private static final String PROP_ADDON_DIR = "relootplusplus.addons";

    private final ReLootPlusPlusConfig config;
    private final LegacyWarnReporter warnReporter;

    public PackDiscovery(ReLootPlusPlusConfig config) {
        this(config, null);
    }

    public PackDiscovery(ReLootPlusPlusConfig config, LegacyWarnReporter warnReporter) {
        this.config = config;
        this.warnReporter = warnReporter;
    }

    public List<AddonPack> discover() {
        List<AddonPack> packs = discoverAll();
        if (config != null) {
            packs = filterEnabled(packs);
        }
        Log.info("Pack", "Discovered {} addon packs", packs.size());
        return packs;
    }

    public List<AddonPack> discoverAll() {
        List<AddonPack> packs = new ArrayList<>();
        Set<Path> candidateDirs = new HashSet<>();
        Map<String, Integer> idCounts = new HashMap<>();
        String duplicateStrategy = config == null ? "suffix" : config.normalizedDuplicateStrategy();

        Path gameDir = FabricLoader.getInstance().getGameDir();
        String envPath = System.getenv(ENV_ADDON_DIR);
        String propPath = System.getProperty(PROP_ADDON_DIR);

        addDir(candidateDirs, envPath);
        addDir(candidateDirs, propPath);
        candidateDirs.add(gameDir.resolve("lootplusplus_addons"));
        candidateDirs.add(gameDir.resolve("addons"));
        candidateDirs.add(gameDir.resolve("addons").resolve("lucky"));
        candidateDirs.add(gameDir.resolve("addons").resolve("lucky_block"));
        candidateDirs.add(gameDir.resolve("packs"));
        candidateDirs.add(gameDir.resolve("mods"));
        if (config != null && config.extraAddonDirs != null) {
            for (String extra : config.extraAddonDirs) {
                addDir(candidateDirs, extra);
            }
        }

        for (Path dir : candidateDirs) {
            for (AddonPack pack : scanDir(dir)) {
                addPack(packs, idCounts, pack, duplicateStrategy);
            }
        }
        return packs;
    }

    private static void addDir(Set<Path> dirs, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        dirs.add(Path.of(raw));
    }

    private static List<AddonPack> scanDir(Path dir) {
        List<AddonPack> packs = new ArrayList<>();
        if (!Files.exists(dir)) {
            return packs;
        }
        if (Files.isRegularFile(dir) && dir.toString().endsWith(".zip")) {
            packs.add(new AddonPack(stripZip(dir.getFileName().toString()), dir));
            return packs;
        }
        if (!Files.isDirectory(dir)) {
            return packs;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.zip")) {
            for (Path zip : stream) {
                packs.add(new AddonPack(stripZip(zip.getFileName().toString()), zip));
            }
        } catch (Exception e) {
            Log.error("Pack", "Failed to scan addon dir {}", dir, e);
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                if (!Files.isDirectory(child)) {
                    continue;
                }
                if (Files.exists(child.resolve("config"))) {
                    packs.add(new AddonPack(child.getFileName().toString(), child));
                }
            }
        } catch (Exception e) {
            Log.error("Pack", "Failed to scan addon folders {}", dir, e);
        }
        return packs;
    }

    private void addPack(List<AddonPack> packs, Map<String, Integer> idCounts, AddonPack pack, String duplicateStrategy) {
        String baseId = pack.id();
        int count = idCounts.getOrDefault(baseId, 0) + 1;
        idCounts.put(baseId, count);
        if (count == 1) {
            packs.add(pack);
            return;
        }
        if ("ignore".equals(duplicateStrategy)) {
            warnDuplicate(baseId, pack.zipPath().toString(), "ignored");
            return;
        }
        String suffix = "_" + count;
        String candidate = baseId + suffix;
        while (idCounts.containsKey(candidate)) {
            count++;
            suffix = "_" + count;
            candidate = baseId + suffix;
        }
        idCounts.put(candidate, 1);
        warnDuplicate(baseId, pack.zipPath().toString(), "renamed to " + candidate);
        packs.add(new AddonPack(candidate, pack.zipPath()));
    }

    private void warnDuplicate(String id, String path, String action) {
        String detail = "duplicate pack id " + id + " (" + action + ") from " + path;
        if (warnReporter != null) {
            warnReporter.warnOnce("DuplicatePack", detail, null);
        } else {
            Log.warn("Legacy", "DuplicatePack {}", detail);
        }
    }

    private static String stripZip(String name) {
        if (name.endsWith(".zip")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    private static List<AddonPack> filterEnabled(List<AddonPack> packs) {
        List<AddonPack> out = new ArrayList<>();
        for (AddonPack pack : packs) {
            if (AddonDisableStore.isEnabled(pack.id())) {
                out.add(pack);
            }
        }
        return out;
    }
}
