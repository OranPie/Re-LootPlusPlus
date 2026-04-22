package ie.orangep.reLootplusplus.diagnostic;

import ie.orangep.reLootplusplus.bootstrap.BootstrapReport;
import ie.orangep.reLootplusplus.config.ReLootPlusPlusConfig;
import ie.orangep.reLootplusplus.pack.AddonPack;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Exports structured diagnostic reports after each bootstrap run.
 *
 * <h3>Output layout</h3>
 * <pre>
 * logs/re_lootplusplus/
 *   latest.txt               ← path to the most-recent run directory
 *   20260422-150000/
 *     report.json            ← structured JSON: config, bootstrap stats, warn-type counts, pack list
 *     warnings.tsv           ← all warnings, one per line, tab-separated
 *     thrown.tsv             ← thrown-item definitions (if any)
 * </pre>
 *
 * <h3>report.json schema</h3>
 * <pre>
 * {
 *   "timestamp": "20260422-150000",
 *   "config":    { "dryRun": false, "exportRawLines": true },
 *   "bootstrap": { "packs": 3, "files": 42, "lines": 1234,
 *                  "warnTotal": 56, "warnUnique": 23,
 *                  "counts": { ... }, "timings": { ... } },
 *   "warnTypes": { "ItemId": 5, "EntityId": 3, ... },
 *   "packs": [ { "id": "lucky", "path": "...", "files": 12, "lines": 234 }, ... ]
 * }
 * </pre>
 *
 * <h3>warnings.tsv columns</h3>
 * {@code type, detail, packId, innerPath, line, packPath[, rawLine]}
 */
public final class DiagnosticExporter {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private DiagnosticExporter() {}

    public static void export(ReLootPlusPlusConfig config, LegacyWarnReporter warnReporter,
                               BootstrapReport report, List<AddonPack> packs) {
        if (config == null || !config.exportReports) return;
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            Path exportRoot = config.resolveExportDir(gameDir);
            Files.createDirectories(exportRoot);

            String stamp = ZonedDateTime.now(ZoneId.systemDefault()).format(STAMP);
            Path runDir = exportRoot.resolve(stamp);
            Files.createDirectories(runDir);

            writeReport(runDir.resolve("report.json"), stamp, config, report, warnReporter, packs);
            writeWarnings(runDir.resolve("warnings.tsv"), warnReporter, config.exportRawLines);
            writeThrown(runDir.resolve("thrown.tsv"));

            // latest.txt: absolute path to most-recent run
            Files.writeString(exportRoot.resolve("latest.txt"),
                runDir.toAbsolutePath().toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            Log.error("Diagnostic", "Failed to export diagnostic report", e);
        }
    }

    // -------------------------------------------------------------------------
    // report.json — hand-rolled JSON to avoid an extra dependency
    // -------------------------------------------------------------------------

    private static void writeReport(Path file, String stamp, ReLootPlusPlusConfig config,
                                     BootstrapReport report, LegacyWarnReporter warnReporter,
                                     List<AddonPack> packs) throws Exception {
        StringBuilder j = new StringBuilder(4096);
        j.append("{\n");
        j.append("  \"timestamp\": ").append(jStr(stamp)).append(",\n");

        // config section
        j.append("  \"config\": {\n");
        j.append("    \"dryRun\": ").append(config.dryRun).append(",\n");
        j.append("    \"exportRawLines\": ").append(config.exportRawLines).append(",\n");
        j.append("    \"exportDir\": ").append(jStr(config.exportDir)).append(",\n");
        j.append("    \"duplicateStrategy\": ").append(jStr(config.duplicateStrategy)).append("\n");
        j.append("  },\n");

        // bootstrap section
        j.append("  \"bootstrap\": {\n");
        j.append("    \"packs\": ").append(report.packCount()).append(",\n");
        j.append("    \"files\": ").append(report.fileCount()).append(",\n");
        j.append("    \"lines\": ").append(report.lineCount()).append(",\n");
        j.append("    \"warnTotal\": ").append(report.totalWarnCount()).append(",\n");
        j.append("    \"warnUnique\": ").append(report.uniqueWarnCount()).append(",\n");
        j.append("    \"counts\": ").append(jIntMap(report.counts())).append(",\n");
        j.append("    \"timings\": ").append(jLongMap(report.timings())).append(",\n");
        // per-pack stats
        j.append("    \"packFiles\": ").append(jIntMap(report.packFileCounts())).append(",\n");
        j.append("    \"packLines\": ").append(jIntMap(report.packLineCounts())).append("\n");
        j.append("  },\n");

        // warnTypes section
        j.append("  \"warnTypes\": ");
        j.append(jIntMap(warnReporter.warnTypeCounts()));
        j.append(",\n");

        // packs section
        j.append("  \"packs\": [\n");
        List<AddonPack> sortedPacks = packs.stream()
            .sorted(Comparator.comparing(AddonPack::id)).toList();
        for (int i = 0; i < sortedPacks.size(); i++) {
            AddonPack p = sortedPacks.get(i);
            j.append("    {");
            j.append("\"id\": ").append(jStr(p.id()));
            j.append(", \"path\": ").append(jStr(p.zipPath() != null ? p.zipPath().toString() : ""));
            Integer fc = report.packFileCounts().get(p.id());
            Integer lc = report.packLineCounts().get(p.id());
            if (fc != null) j.append(", \"files\": ").append(fc);
            if (lc != null) j.append(", \"lines\": ").append(lc);
            j.append("}");
            if (i < sortedPacks.size() - 1) j.append(",");
            j.append("\n");
        }
        j.append("  ]\n");
        j.append("}\n");

        Files.writeString(file, j.toString(), StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // warnings.tsv
    // -------------------------------------------------------------------------

    private static void writeWarnings(Path file, LegacyWarnReporter warnReporter,
                                       boolean includeRaw) throws Exception {
        StringBuilder sb = new StringBuilder(65536);
        sb.append("# Re-LootPlusPlus warnings\n");
        sb.append("# total=").append(warnReporter.totalWarnCount())
          .append("\tunique=").append(warnReporter.uniqueWarnCount()).append("\n");
        sb.append("type\tdetail\tpackId\tinnerPath\tline\tpackPath");
        if (includeRaw) sb.append("\trawLine");
        sb.append("\n");

        for (WarnEntry entry : warnReporter.entries()) {
            SourceLoc loc = entry.sourceLoc();
            sb.append(tsv(entry.type())).append('\t').append(tsv(entry.detail()));
            if (loc != null) {
                sb.append('\t').append(tsv(loc.packId()));
                sb.append('\t').append(tsv(loc.innerPath()));
                sb.append('\t').append(loc.lineNumber());
                sb.append('\t').append(tsv(loc.packPath()));
                if (includeRaw) sb.append('\t').append(tsv(loc.rawLine()));
            } else {
                sb.append("\t\t0\t");
                if (includeRaw) sb.append('\t');
            }
            sb.append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // thrown.tsv
    // -------------------------------------------------------------------------

    private static void writeThrown(Path file) throws Exception {
        var defs = ie.orangep.reLootplusplus.runtime.RuntimeState.thrownDefs();
        if (defs == null || defs.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("# thrown item definitions\n");
        sb.append("itemId\tsourceLoc\n");
        for (var def : defs) {
            sb.append(tsv(def.itemId()));
            if (def.sourceLoc() != null) {
                sb.append('\t').append(tsv(def.sourceLoc().formatShort()));
            }
            sb.append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // Minimal JSON helpers (no external dependency)
    // -------------------------------------------------------------------------

    private static String jStr(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    private static String jIntMap(Map<String, Integer> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> e : map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(jStr(e.getKey())).append(": ").append(e.getValue());
        }
        return sb.append("}").toString();
    }

    private static String jLongMap(Map<String, Long> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Long> e : map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(jStr(e.getKey())).append(": ").append(e.getValue());
        }
        return sb.append("}").toString();
    }

    private static String tsv(String value) {
        if (value == null) return "";
        return value.replace("\t", " ").replace("\n", " ").replace("\r", "");
    }
}

