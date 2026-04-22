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
    public int legacyWarnConsoleLimitPerType = 5;
    public boolean legacyWarnConsoleSummary = true;
    public List<String> disabledAddonPacks = new ArrayList<>();

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
        if (legacyWarnConsoleLimitPerType < 0) {
            legacyWarnConsoleLimitPerType = 0;
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
