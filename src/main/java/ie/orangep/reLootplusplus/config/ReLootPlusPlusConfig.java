package ie.orangep.reLootplusplus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import ie.orangep.reLootplusplus.diagnostic.Log;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ReLootPlusPlusConfig {
    private static final String FILE_NAME = "relootplusplus.json";

    public boolean dryRun = false;
    public boolean exportReports = true;
    public boolean exportRawLines = true;
    public String exportDir = "logs/re_lootplusplus";
    public List<String> extraAddonDirs = new ArrayList<>();
    public String duplicateStrategy = "suffix";
    public String potioncoreNamespace = "re_potioncore";
    public boolean skipMissingEntityRenderers = false;
    public boolean injectResourcePacks = true;
    public boolean logWarnings = false;
    public boolean logLegacyWarnings = false;
    public boolean logDebug = false;
    public String logDetailLevel = null;
    public List<String> logDetailFilters = new ArrayList<>();
    public int legacyWarnConsoleLimitPerType = 5;
    public boolean legacyWarnConsoleSummary = true;
    public List<String> disabledAddonPacks = new ArrayList<>();

    // ── Drop engine ──────────────────────────────────────────────────────────
    /** Weight multiplier per luck point in LuckyDropRoller (matches Lucky Block default 0.1). */
    public float luckModifier = 0.1f;
    /** Baseline luck added to block-entity luck before every drop roll (may be negative). */
    public int defaultLuck = 0;
    /** When false, type=command Lucky drops are skipped (useful for untrusted packs). */
    public boolean commandDropEnabled = true;
    /** When false, the drop-result summary is not sent to the player's chat. */
    public boolean dropChatEnabled = true;

    // ── Server tick hook ─────────────────────────────────────────────────────
    /** Run per-player trigger checks once every N server ticks (1 = every tick). */
    public int tickIntervalTicks = 1;
    /**
     * Trigger types to enable (held, wearing_armour, in_inventory, standing_on_block,
     * inside_block). Empty list means all triggers are active.
     */
    public List<String> enabledTriggerTypes = new ArrayList<>();

    // ── Structure placement ───────────────────────────────────────────────────
    /**
     * Maximum width/height/length (blocks) allowed for a schematic structure.
     * Schematics that exceed this dimension in any axis are skipped with a WARN.
     * 0 = no limit.
     */
    public int structureMaxDimension = 256;

    // ── Pack discovery ────────────────────────────────────────────────────────
    /** When false, the mods/ directory is not scanned for addon packs. */
    public boolean scanModsDir = true;

    // ── World generation ──────────────────────────────────────────────────────
    /** When false, natural_gen rules (Lucky Block world-gen features) are not registered. */
    public boolean naturalGenEnabled = true;

    // ── Legacy compat ─────────────────────────────────────────────────────────
    /**
     * When false, LegacyDropSanitizer is bypassed and raw drop lines are parsed as-is.
     * For packs already written for 1.18.2 syntax that need no compat fixes.
     */
    public boolean legacySanitizeEnabled = true;

    // ── Debug file ────────────────────────────────────────────────────────────
    /**
     * When false, no debug log file is written even if logDetailLevel >= detail.
     * Console output is unaffected.
     */
    public boolean debugFileEnabled = true;
    /**
     * Maximum number of lines written to the debug log file per run.
     * 0 = unlimited. When the limit is reached, a truncation notice is written and
     * the file writer stops.
     */
    public int debugFileMaxLines = 0;

    public static ReLootPlusPlusConfig load() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configFile = configDir.resolve(FILE_NAME);
        ReLootPlusPlusConfig config = new ReLootPlusPlusConfig();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        if (Files.exists(configFile)) {
            try {
                String raw = Files.readString(configFile, StandardCharsets.UTF_8);
                ReLootPlusPlusConfig loaded = gson.fromJson(raw, ReLootPlusPlusConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (JsonSyntaxException e) {
                Log.error("Config", "Invalid config json {}, using defaults", configFile, e);
            } catch (Exception e) {
                Log.error("Config", "Failed to read config {}, using defaults", configFile, e);
            }
        } else {
            try {
                Files.createDirectories(configDir);
                Files.writeString(configFile, gson.toJson(config), StandardCharsets.UTF_8);
            } catch (Exception e) {
                Log.error("Config", "Failed to write default config {}", configFile, e);
            }
        }

        config.normalize();
        config.applyOverrides();
        config.normalize();
        return config;
    }

    public Path resolveExportDir(Path gameDir) {
        if (exportDir == null || exportDir.isBlank()) {
            return gameDir.resolve("logs").resolve("re_lootplusplus");
        }
        Path dir = Path.of(exportDir);
        if (dir.isAbsolute()) {
            return dir;
        }
        return gameDir.resolve(exportDir);
    }

    private void normalize() {
        if (exportDir == null || exportDir.isBlank()) {
            exportDir = "logs/re_lootplusplus";
        }
        if (extraAddonDirs == null) {
            extraAddonDirs = new ArrayList<>();
        }
        if (disabledAddonPacks == null) {
            disabledAddonPacks = new ArrayList<>();
        }
        if (duplicateStrategy == null || duplicateStrategy.isBlank()) {
            duplicateStrategy = "suffix";
        }
        if (potioncoreNamespace == null || potioncoreNamespace.isBlank()) {
            potioncoreNamespace = "re_potioncore";
        }
        if (logDetailLevel == null || logDetailLevel.isBlank()) {
            logDetailLevel = logDebug ? "trace" : "summary";
        }
        if (logDetailFilters == null) {
            logDetailFilters = new ArrayList<>();
        }
        if (legacyWarnConsoleLimitPerType < 0) {
            legacyWarnConsoleLimitPerType = 0;
        }
        // Drop engine
        if (luckModifier < 0f) {
            luckModifier = 0f;
        }
        // Tick hook
        if (tickIntervalTicks < 1) {
            tickIntervalTicks = 1;
        }
        if (enabledTriggerTypes == null) {
            enabledTriggerTypes = new ArrayList<>();
        }
        // Structure
        if (structureMaxDimension < 0) {
            structureMaxDimension = 0;
        }
        // Debug file
        if (debugFileMaxLines < 0) {
            debugFileMaxLines = 0;
        }
    }

    private void applyOverrides() {
        applyBooleanOverride("relootplusplus.dryRun", "RELOOTPLUSPLUS_DRY_RUN", value -> dryRun = value);
        applyBooleanOverride("relootplusplus.exportReports", "RELOOTPLUSPLUS_EXPORT_REPORTS", value -> exportReports = value);
        applyBooleanOverride("relootplusplus.exportRawLines", "RELOOTPLUSPLUS_EXPORT_RAW_LINES", value -> exportRawLines = value);
        applyBooleanOverride("relootplusplus.injectResourcePacks", "RELOOTPLUSPLUS_INJECT_RESOURCE_PACKS",
            value -> injectResourcePacks = value);
        applyBooleanOverride("relootplusplus.logWarnings", "RELOOTPLUSPLUS_LOG_WARNINGS",
            value -> logWarnings = value);
        applyBooleanOverride("relootplusplus.logLegacyWarnings", "RELOOTPLUSPLUS_LOG_LEGACY_WARNINGS",
            value -> logLegacyWarnings = value);
        applyBooleanOverride("relootplusplus.logDebug", "RELOOTPLUSPLUS_LOG_DEBUG",
            value -> logDebug = value);
        String logDetailLevelRaw = firstNonBlank(
            System.getProperty("relootplusplus.logDetailLevel"),
            System.getenv("RELOOTPLUSPLUS_LOG_DETAIL_LEVEL")
        );
        if (logDetailLevelRaw != null) {
            logDetailLevel = logDetailLevelRaw;
        }
        String logDetailFiltersRaw = firstNonBlank(
            System.getProperty("relootplusplus.logDetailFilters"),
            System.getenv("RELOOTPLUSPLUS_LOG_DETAIL_FILTERS")
        );
        if (logDetailFiltersRaw != null) {
            logDetailFilters = parseList(logDetailFiltersRaw);
        }
        applyIntOverride("relootplusplus.legacyWarnConsoleLimitPerType", "RELOOTPLUSPLUS_LEGACY_WARN_CONSOLE_LIMIT_PER_TYPE",
            value -> legacyWarnConsoleLimitPerType = value);
        applyBooleanOverride("relootplusplus.legacyWarnConsoleSummary", "RELOOTPLUSPLUS_LEGACY_WARN_CONSOLE_SUMMARY",
            value -> legacyWarnConsoleSummary = value);
        String exportDirProp = firstNonBlank(
            System.getProperty("relootplusplus.exportDir"),
            System.getenv("RELOOTPLUSPLUS_EXPORT_DIR")
        );
        if (exportDirProp != null) {
            exportDir = exportDirProp;
        }
        String extraDirsRaw = firstNonBlank(
            System.getProperty("relootplusplus.extraAddonDirs"),
            System.getenv("RELOOTPLUSPLUS_EXTRA_ADDON_DIRS")
        );
        if (extraDirsRaw != null) {
            extraAddonDirs = parseList(extraDirsRaw);
        }
        String duplicateRaw = firstNonBlank(
            System.getProperty("relootplusplus.duplicateStrategy"),
            System.getenv("RELOOTPLUSPLUS_DUPLICATE_STRATEGY")
        );
        if (duplicateRaw != null) {
            duplicateStrategy = duplicateRaw;
        }
        String potioncoreRaw = firstNonBlank(
            System.getProperty("relootplusplus.potioncoreNamespace"),
            System.getenv("RELOOTPLUSPLUS_POTIONCORE_NAMESPACE")
        );
        if (potioncoreRaw != null) {
            potioncoreNamespace = potioncoreRaw;
        }
        applyBooleanOverride("relootplusplus.skipMissingEntityRenderers", "RELOOTPLUSPLUS_SKIP_MISSING_ENTITY_RENDERERS",
            value -> skipMissingEntityRenderers = value);

        // Drop engine overrides
        applyFloatOverride("relootplusplus.luckModifier", "RELOOTPLUSPLUS_LUCK_MODIFIER",
            value -> luckModifier = value);
        applyIntOverride("relootplusplus.defaultLuck", "RELOOTPLUSPLUS_DEFAULT_LUCK",
            value -> defaultLuck = value);
        applyBooleanOverride("relootplusplus.commandDropEnabled", "RELOOTPLUSPLUS_COMMAND_DROP_ENABLED",
            value -> commandDropEnabled = value);
        applyBooleanOverride("relootplusplus.dropChatEnabled", "RELOOTPLUSPLUS_DROP_CHAT_ENABLED",
            value -> dropChatEnabled = value);

        // Tick hook overrides
        applyIntOverride("relootplusplus.tickIntervalTicks", "RELOOTPLUSPLUS_TICK_INTERVAL_TICKS",
            value -> tickIntervalTicks = value);
        String enabledTriggersRaw = firstNonBlank(
            System.getProperty("relootplusplus.enabledTriggerTypes"),
            System.getenv("RELOOTPLUSPLUS_ENABLED_TRIGGER_TYPES")
        );
        if (enabledTriggersRaw != null) {
            enabledTriggerTypes = parseList(enabledTriggersRaw);
        }

        // Structure overrides
        applyIntOverride("relootplusplus.structureMaxDimension", "RELOOTPLUSPLUS_STRUCTURE_MAX_DIMENSION",
            value -> structureMaxDimension = value);

        // Pack discovery overrides
        applyBooleanOverride("relootplusplus.scanModsDir", "RELOOTPLUSPLUS_SCAN_MODS_DIR",
            value -> scanModsDir = value);

        // World gen overrides
        applyBooleanOverride("relootplusplus.naturalGenEnabled", "RELOOTPLUSPLUS_NATURAL_GEN_ENABLED",
            value -> naturalGenEnabled = value);

        // Legacy compat overrides
        applyBooleanOverride("relootplusplus.legacySanitizeEnabled", "RELOOTPLUSPLUS_LEGACY_SANITIZE_ENABLED",
            value -> legacySanitizeEnabled = value);

        // Debug file overrides
        applyBooleanOverride("relootplusplus.debugFileEnabled", "RELOOTPLUSPLUS_DEBUG_FILE_ENABLED",
            value -> debugFileEnabled = value);
        applyIntOverride("relootplusplus.debugFileMaxLines", "RELOOTPLUSPLUS_DEBUG_FILE_MAX_LINES",
            value -> debugFileMaxLines = value);
    }

    public void save() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configFile = configDir.resolve(FILE_NAME);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            Files.createDirectories(configDir);
            Files.writeString(configFile, gson.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.error("Config", "Failed to write config {}", configFile, e);
        }
    }

    public String normalizedDuplicateStrategy() {
        if (duplicateStrategy == null) {
            return "suffix";
        }
        String normalized = duplicateStrategy.trim().toLowerCase(Locale.ROOT);
        if ("ignore".equals(normalized)) {
            return "ignore";
        }
        return "suffix";
    }

    public boolean isAddonEnabled(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        if (disabledAddonPacks == null) {
            return true;
        }
        return disabledAddonPacks.stream().noneMatch(id::equalsIgnoreCase);
    }

    public void setAddonEnabled(String id, boolean enabled) {
        if (id == null || id.isBlank()) {
            return;
        }
        if (disabledAddonPacks == null) {
            disabledAddonPacks = new ArrayList<>();
        }
        disabledAddonPacks.removeIf(id::equalsIgnoreCase);
        if (!enabled) {
            disabledAddonPacks.add(id);
        }
    }

    private void applyBooleanOverride(String propKey, String envKey, java.util.function.Consumer<Boolean> setter) {
        String raw = firstNonBlank(System.getProperty(propKey), System.getenv(envKey));
        if (raw == null) {
            return;
        }
        String lowered = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(lowered) || "1".equals(lowered) || "yes".equals(lowered)) {
            setter.accept(true);
        } else if ("false".equals(lowered) || "0".equals(lowered) || "no".equals(lowered)) {
            setter.accept(false);
        }
    }

    private void applyIntOverride(String propKey, String envKey, java.util.function.Consumer<Integer> setter) {
        String raw = firstNonBlank(System.getProperty(propKey), System.getenv(envKey));
        if (raw == null) {
            return;
        }
        try {
            setter.accept(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ignored) {
        }
    }

    private void applyFloatOverride(String propKey, String envKey, java.util.function.Consumer<Float> setter) {
        String raw = firstNonBlank(System.getProperty(propKey), System.getenv(envKey));
        if (raw == null) {
            return;
        }
        try {
            setter.accept(Float.parseFloat(raw.trim()));
        } catch (NumberFormatException ignored) {
        }
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private List<String> parseList(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }
}
