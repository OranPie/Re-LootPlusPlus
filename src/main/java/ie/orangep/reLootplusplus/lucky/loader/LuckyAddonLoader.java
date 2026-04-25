package ie.orangep.reLootplusplus.lucky.loader;

import ie.orangep.reLootplusplus.config.AddonDisableStore;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import ie.orangep.reLootplusplus.legacy.LegacyDropSanitizer;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropParser;
import ie.orangep.reLootplusplus.pack.AddonPack;
import ie.orangep.reLootplusplus.pack.PackIndex;
import org.jetbrains.annotations.Nullable;
import ie.orangep.reLootplusplus.pack.io.PackFileReader;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Loads Lucky Block addon drops from scanned {@link AddonPack} instances.
 *
 * <p>Called during bootstrap Phase 3 (parse rules), after pack discovery.
 * Drop lines are parsed <em>once</em> here so that {@code LuckyDropEngine} can
 * roll and execute without re-parsing the entire drops.txt on every block break.
 */
public final class LuckyAddonLoader {

    // Raw merged drop lists (kept for UI / diagnostics)
    private static volatile List<String> mergedDropLines = null;
    private static volatile List<String> mergedBowDropLines = null;
    private static volatile List<String> mergedSwordDropLines = null;
    private static volatile List<String> mergedPotionDropLines = null;

    // Pre-parsed merged drop lists — used by LuckyDropEngine at runtime
    private static volatile List<LuckyDropLine> mergedDrops = null;
    private static volatile List<LuckyDropLine> mergedBowDrops = null;
    private static volatile List<LuckyDropLine> mergedSwordDrops = null;
    private static volatile List<LuckyDropLine> mergedPotionDrops = null;
    private static volatile List<LuckyDropLine> baseDrops = null;

    // Per-addon data (populated by load())
    private static volatile List<LuckyAddonData> addonDataList = null;

    // Base drops from the game's config/lucky directory (ultimate fallback)
    private static volatile List<String> baseDropLines = null;

    // Merged structure registry: structureId (lower) -> entry (first seen wins)
    private static volatile Map<String, LuckyStructureEntry> structureRegistry = null;

    // Merged structure-to-pack registry: structureId (lower) -> AddonPack (first seen wins)
    private static volatile Map<String, AddonPack> structurePackRegistry = null;

    private LuckyAddonLoader() {}

    /**
     * Returns the merged drop lines from all loaded addons, or an empty list if not yet loaded.
     */
    public static List<String> getMergedDropLines() {
        List<String> lines = mergedDropLines;
        return lines != null ? lines : Collections.emptyList();
    }

    /**
     * Returns the merged bow_drops lines from all loaded addons, or an empty list if not yet loaded.
     */
    public static List<String> getMergedBowDropLines() {
        List<String> lines = mergedBowDropLines;
        return lines != null ? lines : Collections.emptyList();
    }

    /**
     * Returns the merged sword_drops lines from all loaded addons, or an empty list if not yet loaded.
     * Falls back to bow_drops if sword_drops is empty.
     */
    public static List<String> getMergedSwordDropLines() {
        List<String> lines = mergedSwordDropLines;
        if (lines != null && !lines.isEmpty()) return lines;
        return getMergedBowDropLines(); // sword falls back to bow drops by convention
    }

    /**
     * Returns the merged potion_drops lines from all loaded addons, or an empty list if not yet loaded.
     * Falls back to bow_drops if potion_drops is empty.
     */
    public static List<String> getMergedPotionDropLines() {
        List<String> lines = mergedPotionDropLines;
        if (lines != null && !lines.isEmpty()) return lines;
        return getMergedBowDropLines(); // potion falls back to bow drops by convention
    }

    /**
     * Looks up a structure entry by ID from the merged structure registry (case-insensitive).
     * Returns {@code null} if not found.
     */
    public static LuckyStructureEntry getStructureById(String structureId) {
        Map<String, LuckyStructureEntry> reg = structureRegistry;
        if (reg == null || structureId == null) return null;
        return reg.get(structureId.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Looks up the {@link AddonPack} that owns the given structure ID (case-insensitive).
     * Returns {@code null} if not found.
     */
    public static @Nullable AddonPack getPackForStructure(String structureId) {
        Map<String, AddonPack> reg = structurePackRegistry;
        if (reg == null || structureId == null) return null;
        return reg.get(structureId.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Returns per-addon data list (populated by {@link #load}), or empty list if not yet loaded.
     */
    public static List<LuckyAddonData> getAddonDataList() {
        List<LuckyAddonData> list = addonDataList;
        return list != null ? list : Collections.emptyList();
    }

    /**
     * Returns the drop lines for the addon whose plugin_init blockId matches, or empty if not found
     * or the owning pack is disabled.
     */
    public static List<String> getDropsForBlockId(String blockId) {
        List<LuckyAddonData> list = addonDataList;
        if (list == null || blockId == null) return Collections.emptyList();
        String normalized = blockId.toLowerCase(java.util.Locale.ROOT).replace('.', '_');
        for (LuckyAddonData data : list) {
            if (data.pluginInit() != null) {
                String dataId = data.pluginInit().blockId()
                    .toLowerCase(java.util.Locale.ROOT).replace('.', '_');
                if (dataId.equals(normalized)) {
                    if (!AddonDisableStore.isEnabled(data.packId())) return Collections.emptyList();
                    return data.dropLines();
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * Returns the base game drops loaded from the config/lucky directory, or empty list if not found.
     */
    public static List<String> getBaseDropLines() {
        List<String> lines = baseDropLines;
        return lines != null ? lines : Collections.emptyList();
    }

    // ---- Pre-parsed getters (no re-parsing at block-break time) ----

    /**
     * Pre-parsed merged drops filtered to only enabled packs.
     * Called on every block-break — O(n packs), no re-parsing.
     */
    public static List<LuckyDropLine> getMergedDrops() {
        List<LuckyAddonData> list = addonDataList;
        if (list == null || list.isEmpty()) {
            // Fallback to static merged list during early bootstrap
            List<LuckyDropLine> d = mergedDrops;
            return d != null ? d : Collections.emptyList();
        }
        List<LuckyDropLine> out = new ArrayList<>();
        for (LuckyAddonData data : list) {
            if (AddonDisableStore.isEnabled(data.packId())) {
                out.addAll(data.parsedDrops());
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** Pre-parsed merged bow drops filtered to enabled packs. */
    public static List<LuckyDropLine> getMergedBowDrops() {
        List<LuckyAddonData> list = addonDataList;
        if (list == null || list.isEmpty()) {
            List<LuckyDropLine> d = mergedBowDrops;
            return d != null ? d : Collections.emptyList();
        }
        List<LuckyDropLine> out = new ArrayList<>();
        for (LuckyAddonData data : list) {
            if (AddonDisableStore.isEnabled(data.packId())) {
                out.addAll(data.parsedBowDrops());
            }
        }
        return out.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(out);
    }

    /** Pre-parsed merged sword drops filtered to enabled packs (falls back to bow drops). */
    public static List<LuckyDropLine> getMergedSwordDrops() {
        List<LuckyAddonData> list = addonDataList;
        if (list == null || list.isEmpty()) {
            List<LuckyDropLine> d = mergedSwordDrops;
            if (d != null && !d.isEmpty()) return d;
            return getMergedBowDrops();
        }
        List<LuckyDropLine> out = new ArrayList<>();
        for (LuckyAddonData data : list) {
            if (AddonDisableStore.isEnabled(data.packId())) {
                out.addAll(data.parsedSwordDrops());
            }
        }
        return out.isEmpty() ? getMergedBowDrops() : Collections.unmodifiableList(out);
    }

    /** Pre-parsed merged potion drops filtered to enabled packs (falls back to bow drops). */
    public static List<LuckyDropLine> getMergedPotionDrops() {
        List<LuckyAddonData> list = addonDataList;
        if (list == null || list.isEmpty()) {
            List<LuckyDropLine> d = mergedPotionDrops;
            if (d != null && !d.isEmpty()) return d;
            return getMergedBowDrops();
        }
        List<LuckyDropLine> out = new ArrayList<>();
        for (LuckyAddonData data : list) {
            if (AddonDisableStore.isEnabled(data.packId())) {
                out.addAll(data.parsedPotionDrops());
            }
        }
        return out.isEmpty() ? getMergedBowDrops() : Collections.unmodifiableList(out);
    }

    /** Pre-parsed base game drops. */
    public static List<LuckyDropLine> getBaseDrops() {
        List<LuckyDropLine> d = baseDrops;
        return d != null ? d : Collections.emptyList();
    }

    /**
     * Returns the pre-parsed drop lines for the addon whose plugin_init blockId matches,
     * or empty if not found or the owning pack is disabled.
     */
    public static List<LuckyDropLine> getParsedDropsForBlockId(String blockId) {
        List<LuckyAddonData> list = addonDataList;
        if (list == null || blockId == null) return Collections.emptyList();
        String normalized = blockId.toLowerCase(java.util.Locale.ROOT).replace('.', '_');
        for (LuckyAddonData data : list) {
            if (data.pluginInit() != null) {
                String dataId = data.pluginInit().blockId()
                    .toLowerCase(java.util.Locale.ROOT).replace('.', '_');
                if (dataId.equals(normalized)) {
                    if (!AddonDisableStore.isEnabled(data.packId())) return Collections.emptyList();
                    return data.parsedDrops();
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * Loads Lucky drops from all packs. May be called multiple times (e.g. on /reload).
     * Re-load replaces the in-memory drop lists atomically.
     */
    public static void load(List<AddonPack> packs, LegacyWarnReporter warnReporter) {
        List<String> drops = new ArrayList<>();
        List<String> bowDrops = new ArrayList<>();
        List<String> swordDrops = new ArrayList<>();
        List<String> potionDrops = new ArrayList<>();
        List<LuckyAddonData> dataList = new ArrayList<>();
        Map<String, LuckyStructureEntry> structReg = new LinkedHashMap<>();
        Map<String, AddonPack> structPackReg = new LinkedHashMap<>();

        // Deduplicate addon IDs: first seen wins
        Map<String, AddonPack> seen = new LinkedHashMap<>();
        for (AddonPack pack : packs) {
            String id = pack.id();
            if (seen.containsKey(id)) {
                warnReporter.warnOnce("LuckyAddonDuplicate",
                    "duplicate addon id '" + id + "' — keeping first", null);
                continue;
            }
            seen.put(id, pack);
        }

        for (AddonPack pack : seen.values()) {
            SourceLoc loc = new SourceLoc(pack.id(), pack.zipPath() != null ? pack.zipPath().toString() : pack.id(), "", 0, "");
            LuckyAddonData data = loadPack(pack, drops, bowDrops, swordDrops, potionDrops, warnReporter, loc);
            dataList.add(data);
            // Merge structure registry (first seen wins)
            for (LuckyStructureEntry entry : data.structureEntries()) {
                structReg.putIfAbsent(entry.id().toLowerCase(java.util.Locale.ROOT), entry);
                structPackReg.putIfAbsent(entry.id().toLowerCase(java.util.Locale.ROOT), pack);
            }
        }

        // Try loading base drops from config/lucky/<version>/drops.txt
        List<String> baseDrps = loadBaseDrops(warnReporter);

        // Parse all raw drop lists once here — no re-parsing at block-break time
        SourceLoc mergedLoc = new SourceLoc("merged", "merged", "drops.txt", 0, "");
        List<LuckyDropLine> parsedDrops      = parseLines(drops,       warnReporter, mergedLoc);
        List<LuckyDropLine> parsedBowDrops   = parseLines(bowDrops,    warnReporter, mergedLoc);
        List<LuckyDropLine> parsedSwordDrops = parseLines(swordDrops,  warnReporter, mergedLoc);
        List<LuckyDropLine> parsedPotionDrops= parseLines(potionDrops, warnReporter, mergedLoc);
        List<LuckyDropLine> parsedBaseDrops  = parseLines(baseDrps,    warnReporter, mergedLoc);

        // Atomic replacement
        mergedDropLines     = Collections.unmodifiableList(drops);
        mergedBowDropLines  = Collections.unmodifiableList(bowDrops);
        mergedSwordDropLines= Collections.unmodifiableList(swordDrops);
        mergedPotionDropLines=Collections.unmodifiableList(potionDrops);
        mergedDrops         = Collections.unmodifiableList(parsedDrops);
        mergedBowDrops      = Collections.unmodifiableList(parsedBowDrops);
        mergedSwordDrops    = Collections.unmodifiableList(parsedSwordDrops);
        mergedPotionDrops   = Collections.unmodifiableList(parsedPotionDrops);
        baseDrops           = Collections.unmodifiableList(parsedBaseDrops);
        addonDataList       = Collections.unmodifiableList(dataList);
        baseDropLines       = Collections.unmodifiableList(baseDrps);
        structureRegistry   = Collections.unmodifiableMap(structReg);
        structurePackRegistry = Collections.unmodifiableMap(structPackReg);
    }

    /** Parses raw lines into LuckyDropLine using a shared LuckyDropParser instance. */
    private static List<LuckyDropLine> parseLines(List<String> rawLines, LegacyWarnReporter reporter, SourceLoc loc) {
        if (rawLines == null || rawLines.isEmpty()) return Collections.emptyList();
        return new LuckyDropParser(reporter, loc).parseLines(rawLines);
    }

    // -------------------------------------------------------------------------
    // Pack reading
    // -------------------------------------------------------------------------

    private static LuckyAddonData loadPack(AddonPack pack, List<String> drops, List<String> bowDrops,
                                           List<String> swordDrops, List<String> potionDrops,
                                           LegacyWarnReporter warnReporter, SourceLoc loc) {
        Path zipPath = pack.zipPath();
        if (zipPath == null) {
            return emptyData(pack);
        }

        if (Files.isDirectory(zipPath)) {
            return loadFromDirectory(pack, zipPath, drops, bowDrops, swordDrops, potionDrops, warnReporter, loc);
        } else if (Files.isRegularFile(zipPath)) {
            return loadFromZip(pack, zipPath, drops, bowDrops, swordDrops, potionDrops, warnReporter, loc);
        }
        return emptyData(pack);
    }

    private static LuckyAddonData emptyData(AddonPack pack) {
        return LuckyAddonData.withoutDrops(pack.id(), pack, null, null, List.of(), List.of());
    }

    private static LuckyAddonData loadFromZip(AddonPack pack, Path zipPath,
                                              List<String> drops, List<String> bowDrops,
                                              List<String> swordDrops, List<String> potionDrops,
                                              LegacyWarnReporter warnReporter, SourceLoc loc) {
        List<String> addonDropLines = List.of();
        List<String> addonBowDropLines = List.of();
        List<String> addonSwordDropLines = List.of();
        List<String> addonPotionDropLines = List.of();
        LuckyPluginInit pluginInit = null;
        LuckyAddonProperties properties = null;
        List<LuckyStructureEntry> structureEntries = List.of();
        List<LuckyNaturalGenEntry> naturalGenEntries = List.of();

        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            String prefix = PackIndex.findZipContentPrefix(zip);

            // plugin_init.txt
            List<String> piLines = readZipEntry(zip, prefix + "plugin_init.txt", pack, warnReporter);
            if (piLines == null) piLines = readZipEntry(zip, prefix + "config/plugin_init.txt", pack, warnReporter);
            if (piLines != null) {
                pluginInit = LuckyPluginInit.parse(String.join("\n", piLines));
            }

            // drops.txt
            List<String> dropsLines = readZipEntry(zip, prefix + "drops.txt", pack, warnReporter);
            if (dropsLines == null) dropsLines = readZipEntry(zip, prefix + "config/drops.txt", pack, warnReporter);
            if (dropsLines != null) {
                List<String> sanitized = sanitizeLines(dropsLines, pack, "drops.txt", warnReporter);
                drops.addAll(sanitized);
                addonDropLines = sanitized;
            }

            // bow_drops.txt
            List<String> bowLines = readZipEntry(zip, prefix + "bow_drops.txt", pack, warnReporter);
            if (bowLines == null) bowLines = readZipEntry(zip, prefix + "config/bow_drops.txt", pack, warnReporter);
            if (bowLines != null) {
                List<String> sanitized = sanitizeLines(bowLines, pack, "bow_drops.txt", warnReporter);
                bowDrops.addAll(sanitized);
                addonBowDropLines = sanitized;
            }

            // sword_drops.txt
            List<String> swordLines = readZipEntry(zip, prefix + "sword_drops.txt", pack, warnReporter);
            if (swordLines == null) swordLines = readZipEntry(zip, prefix + "config/sword_drops.txt", pack, warnReporter);
            if (swordLines != null) {
                List<String> sanitized = sanitizeLines(swordLines, pack, "sword_drops.txt", warnReporter);
                swordDrops.addAll(sanitized);
                addonSwordDropLines = sanitized;
            }

            // potion_drops.txt
            List<String> potionLines = readZipEntry(zip, prefix + "potion_drops.txt", pack, warnReporter);
            if (potionLines == null) potionLines = readZipEntry(zip, prefix + "config/potion_drops.txt", pack, warnReporter);
            if (potionLines != null) {
                List<String> sanitized = sanitizeLines(potionLines, pack, "potion_drops.txt", warnReporter);
                potionDrops.addAll(sanitized);
                addonPotionDropLines = sanitized;
            }

            // properties.txt
            List<String> propLines = readZipEntry(zip, prefix + "properties.txt", pack, warnReporter);
            if (propLines == null) propLines = readZipEntry(zip, prefix + "config/properties.txt", pack, warnReporter);
            if (propLines != null) {
                properties = LuckyAddonProperties.parse(propLines);
            }

            // structures.txt
            List<String> structLines = readZipEntry(zip, prefix + "structures.txt", pack, warnReporter);
            if (structLines == null) structLines = readZipEntry(zip, prefix + "config/structures.txt", pack, warnReporter);
            if (structLines != null) {
                structureEntries = Collections.unmodifiableList(LuckyStructureEntry.parseLines(structLines));
            }

            // natural_gen.txt
            List<String> genLines = readZipEntry(zip, prefix + "natural_gen.txt", pack, warnReporter);
            if (genLines == null) genLines = readZipEntry(zip, prefix + "config/natural_gen.txt", pack, warnReporter);
            if (genLines != null) {
                naturalGenEntries = Collections.unmodifiableList(LuckyNaturalGenEntry.parseLines(genLines));
            }

        } catch (Exception e) {
            warnReporter.warn("LuckyAddonLoad", "I/O error reading zip " + zipPath + ": " + e.getMessage(), loc);
        }
        return new LuckyAddonData(pack.id(), pack, pluginInit,
            Collections.unmodifiableList(addonDropLines),
            Collections.unmodifiableList(addonBowDropLines),
            Collections.unmodifiableList(addonSwordDropLines),
            Collections.unmodifiableList(addonPotionDropLines),
            parseLines(addonDropLines,       warnReporter, loc),
            parseLines(addonBowDropLines,    warnReporter, loc),
            parseLines(addonSwordDropLines,  warnReporter, loc),
            parseLines(addonPotionDropLines, warnReporter, loc),
            properties, structureEntries, naturalGenEntries);
    }

    private static LuckyAddonData loadFromDirectory(AddonPack pack, Path dir,
                                                     List<String> drops, List<String> bowDrops,
                                                     List<String> swordDrops, List<String> potionDrops,
                                                     LegacyWarnReporter warnReporter, SourceLoc loc) {
        List<String> addonDropLines = List.of();
        List<String> addonBowDropLines = List.of();
        List<String> addonSwordDropLines = List.of();
        List<String> addonPotionDropLines = List.of();
        LuckyPluginInit pluginInit = null;
        LuckyAddonProperties properties = null;
        List<LuckyStructureEntry> structureEntries = List.of();
        List<LuckyNaturalGenEntry> naturalGenEntries = List.of();

        // plugin_init.txt
        Path piFile = dir.resolve("plugin_init.txt");
        if (!Files.exists(piFile)) piFile = dir.resolve("config/plugin_init.txt");
        if (Files.exists(piFile)) {
            try {
                List<String> lines = readDirFile(piFile);
                pluginInit = LuckyPluginInit.parse(String.join("\n", lines));
            } catch (Exception e) {
                warnReporter.warn("LuckyAddonLoad", "error reading " + piFile + ": " + e.getMessage(), loc);
            }
        }

        // drops.txt
        Path dropsFile = resolveFile(dir, "drops.txt");
        if (dropsFile != null) {
            try {
                List<String> sanitized = sanitizeLines(readDirFile(dropsFile), pack, "drops.txt", warnReporter);
                drops.addAll(sanitized);
                addonDropLines = sanitized;
            } catch (Exception e) {
                warnReporter.warn("LuckyAddonLoad", "error reading " + dropsFile + ": " + e.getMessage(), loc);
            }
        }

        // bow_drops.txt
        Path bowFile = resolveFile(dir, "bow_drops.txt");
        if (bowFile != null) {
            try {
                List<String> sanitized = sanitizeLines(readDirFile(bowFile), pack, "bow_drops.txt", warnReporter);
                bowDrops.addAll(sanitized);
                addonBowDropLines = sanitized;
            } catch (Exception e) {
                warnReporter.warn("LuckyAddonLoad", "error reading " + bowFile + ": " + e.getMessage(), loc);
            }
        }

        // sword_drops.txt
        Path swordFile = resolveFile(dir, "sword_drops.txt");
        if (swordFile != null) {
            try {
                List<String> sanitized = sanitizeLines(readDirFile(swordFile), pack, "sword_drops.txt", warnReporter);
                swordDrops.addAll(sanitized);
                addonSwordDropLines = sanitized;
            } catch (Exception e) {
                warnReporter.warn("LuckyAddonLoad", "error reading " + swordFile + ": " + e.getMessage(), loc);
            }
        }

        // potion_drops.txt
        Path potionFile = resolveFile(dir, "potion_drops.txt");
        if (potionFile != null) {
            try {
                List<String> sanitized = sanitizeLines(readDirFile(potionFile), pack, "potion_drops.txt", warnReporter);
                potionDrops.addAll(sanitized);
                addonPotionDropLines = sanitized;
            } catch (Exception e) {
                warnReporter.warn("LuckyAddonLoad", "error reading " + potionFile + ": " + e.getMessage(), loc);
            }
        }

        // properties.txt
        Path propFile = resolveFile(dir, "properties.txt");
        if (propFile != null) {
            try {
                properties = LuckyAddonProperties.parse(readDirFile(propFile));
            } catch (Exception e) {
                warnReporter.warn("LuckyAddonLoad", "error reading " + propFile + ": " + e.getMessage(), loc);
            }
        }

        // structures.txt
        Path structFile = resolveFile(dir, "structures.txt");
        if (structFile != null) {
            try {
                structureEntries = Collections.unmodifiableList(
                    LuckyStructureEntry.parseLines(readDirFile(structFile)));
            } catch (Exception e) {
                warnReporter.warn("LuckyAddonLoad", "error reading " + structFile + ": " + e.getMessage(), loc);
            }
        }

        // natural_gen.txt
        Path genFile = resolveFile(dir, "natural_gen.txt");
        if (genFile != null) {
            try {
                naturalGenEntries = Collections.unmodifiableList(
                    LuckyNaturalGenEntry.parseLines(readDirFile(genFile)));
            } catch (Exception e) {
                warnReporter.warn("LuckyAddonLoad", "error reading " + genFile + ": " + e.getMessage(), loc);
            }
        }

        return new LuckyAddonData(pack.id(), pack, pluginInit,
            Collections.unmodifiableList(addonDropLines),
            Collections.unmodifiableList(addonBowDropLines),
            Collections.unmodifiableList(addonSwordDropLines),
            Collections.unmodifiableList(addonPotionDropLines),
            parseLines(addonDropLines,       warnReporter, loc),
            parseLines(addonBowDropLines,    warnReporter, loc),
            parseLines(addonSwordDropLines,  warnReporter, loc),
            parseLines(addonPotionDropLines, warnReporter, loc),
            properties, structureEntries, naturalGenEntries);
    }

    /** Returns the first existing path among {@code dir/<name>} and {@code dir/config/<name>}. */
    private static Path resolveFile(Path dir, String name) {
        Path p = dir.resolve(name);
        if (Files.exists(p)) return p;
        p = dir.resolve("config/" + name);
        if (Files.exists(p)) return p;
        return null;
    }

    /**
     * Loads base drops from {@code config/lucky/<version>/drops.txt} in the game directory.
     */
    private static List<String> loadBaseDrops(LegacyWarnReporter warnReporter) {
        try {
            Path configLucky = FabricLoader.getInstance().getGameDir()
                .resolve("config").resolve("lucky");
            if (!Files.isDirectory(configLucky)) return List.of();
            // Walk subdirectories for the first drops.txt
            Path[] found = {null};
            Files.walkFileTree(configLucky, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().equalsIgnoreCase("drops.txt")) {
                        found[0] = file;
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            if (found[0] == null) return List.of();
            List<String> lines = readDirFile(found[0]);
            // Create a dummy pack for sanitization context
            AddonPack dummyPack = new AddonPack("config_lucky_base", found[0].getParent());
            return new ArrayList<>(sanitizeLines(lines, dummyPack, "drops.txt", warnReporter));
        } catch (Exception e) {
            warnReporter.warn("LuckyAddonLoad", "Failed to load base config drops: " + e.getMessage(), null);
            return List.of();
        }
    }

    private static List<String> readZipEntry(ZipFile zip, String entryName, AddonPack pack,
                                              LegacyWarnReporter warnReporter) {
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null) return null;
        try {
            return PackFileReader.readLines(zip, entry);
        } catch (Exception e) {
            warnReporter.warn("LuckyAddonLoad", "error reading " + entryName + " from " + pack.id() + ": " + e.getMessage(),
                new SourceLoc(pack.id(), "", entryName, 0, ""));
            return null;
        }
    }

    private static List<String> readDirFile(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        String content;
        try {
            content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            content = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        }
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') content = content.substring(1);
        return List.of(content.split("\r?\n", -1));
    }

    // -------------------------------------------------------------------------
    // Line continuation + sanitization
    // -------------------------------------------------------------------------

    /**
     * Merges line continuations ({@code \} at end-of-line or {@code ,} continuation)
     * and runs {@link LegacyDropSanitizer#sanitize} on each logical line.
     */
    private static List<String> sanitizeLines(List<String> rawLines, AddonPack pack,
                                               String fileName, LegacyWarnReporter warnReporter) {
        List<String> merged = mergeLineContinuations(rawLines);
        List<String> result = new ArrayList<>(merged.size());
        int lineNum = 0;
        for (String line : merged) {
            lineNum++;
            if (line == null || line.strip().isEmpty()) continue;
            SourceLoc loc = new SourceLoc(pack.id(), pack.zipPath() != null ? pack.zipPath().toString() : pack.id(),
                fileName, lineNum, line);
            try {
                String sanitized = LegacyDropSanitizer.sanitize(line, warnReporter);
                if (sanitized != null && !sanitized.strip().isEmpty()) {
                    result.add(sanitized);
                }
            } catch (Exception e) {
                warnReporter.warn("LuckyDropSanitize", "error sanitizing line: " + preview(line) + " — " + e.getMessage(), loc);
            }
        }
        return result;
    }

    /**
     * Merges lines ending with {@code \} (continuation) into a single line.
     * Also merges lines ending with {@code ,} if the next line looks like a continuation
     * (starts with a key=value attribute, not with a comment or blank).
     */
    static List<String> mergeLineContinuations(List<String> lines) {
        List<String> result = new ArrayList<>();
        StringBuilder current = null;
        for (String rawLine : lines) {
            if (rawLine == null) continue;
            if (rawLine.endsWith("\\")) {
                // Explicit backslash continuation
                if (current == null) current = new StringBuilder();
                current.append(rawLine, 0, rawLine.length() - 1);
            } else {
                if (current != null) {
                    current.append(rawLine);
                    result.add(current.toString());
                    current = null;
                } else {
                    result.add(rawLine);
                }
            }
        }
        if (current != null) {
            result.add(current.toString()); // flush incomplete continuation
        }
        return result;
    }

    private static String preview(String s) {
        if (s == null) return "(null)";
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }
}
