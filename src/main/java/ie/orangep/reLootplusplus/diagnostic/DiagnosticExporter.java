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

public final class DiagnosticExporter {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private DiagnosticExporter() {
    }

    public static void export(ReLootPlusPlusConfig config, LegacyWarnReporter warnReporter, BootstrapReport report, List<AddonPack> packs) {
        if (config == null || !config.exportReports) {
            return;
        }
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            Path dir = config.resolveExportDir(gameDir);
            Files.createDirectories(dir);
            String stamp = ZonedDateTime.now(ZoneId.systemDefault()).format(STAMP);
            Path latestRoot = dir.resolve("Latest");
            Files.createDirectories(latestRoot);
            Path runDir = latestRoot.resolve(stamp);
            Files.createDirectories(runDir);
            writeWarnings(runDir.resolve("warnings.tsv"), warnReporter, config.exportRawLines);
            writeSummary(runDir.resolve("summary.txt"), report, warnReporter, packs, config);
            writeWarnTypes(runDir.resolve("warn_types.txt"), warnReporter);
            writePacks(runDir.resolve("packs.txt"), packs);
            writeCounts(runDir.resolve("counts.txt"), report);
            writeThrown(runDir.resolve("thrown_items.txt"));
            writeIndex(dir.resolve("latest.txt"), runDir);
        } catch (Exception e) {
            Log.warn("Failed to export diagnostic report", e);
        }
    }

    private static void writeWarnings(Path file, LegacyWarnReporter warnReporter, boolean includeRaw) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("Re-LootPlusPlus warnings\n");
        sb.append("total=").append(warnReporter.totalWarnCount()).append('\n');
        sb.append("unique=").append(warnReporter.uniqueWarnCount()).append('\n');
        sb.append("format=type\\tdetail\\tpackId\\tinnerPath\\tline\\tpackPath");
        if (includeRaw) {
            sb.append("\\trawLine");
        }
        sb.append('\n');
        for (WarnEntry entry : warnReporter.entries()) {
            SourceLoc loc = entry.sourceLoc();
            sb.append(entry.type()).append('\t').append(entry.detail());
            if (loc != null) {
                sb.append('\t').append(nullToEmpty(loc.packId()));
                sb.append('\t').append(nullToEmpty(loc.innerPath()));
                sb.append('\t').append(loc.lineNumber());
                sb.append('\t').append(nullToEmpty(loc.packPath()));
                if (includeRaw) {
                    sb.append('\t').append(nullToEmpty(loc.rawLine()));
                }
            } else {
                sb.append("\t\t\t\t");
                if (includeRaw) {
                    sb.append('\t');
                }
            }
            sb.append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private static void writeSummary(Path file, BootstrapReport report, LegacyWarnReporter warnReporter, List<AddonPack> packs, ReLootPlusPlusConfig config) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("Re-LootPlusPlus summary\n");
        sb.append("dryRun=").append(config.dryRun).append('\n');
        sb.append("packs=").append(report.packCount()).append('\n');
        sb.append("files=").append(report.fileCount()).append('\n');
        sb.append("lines=").append(report.lineCount()).append('\n');
        sb.append("warnTotal=").append(report.totalWarnCount()).append('\n');
        sb.append("warnUnique=").append(report.uniqueWarnCount()).append('\n');
        sb.append('\n');
        sb.append("packList:\n");
        for (AddonPack pack : packs) {
            sb.append("- ").append(pack.id()).append(" -> ").append(pack.zipPath()).append('\n');
        }
        sb.append('\n');
        sb.append("counts:\n");
        for (Map.Entry<String, Integer> entry : report.counts().entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        sb.append('\n');
        sb.append("warnTypes:\n");
        warnReporter.warnTypeCounts().entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .forEach(entry -> sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n'));

        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private static void writeWarnTypes(Path file, LegacyWarnReporter warnReporter) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("warnTypes\n");
        warnReporter.warnTypeCounts().entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .forEach(entry -> sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n'));
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private static void writePacks(Path file, List<AddonPack> packs) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("packs\n");
        for (AddonPack pack : packs) {
            sb.append(pack.id()).append('\t').append(pack.zipPath()).append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private static void writeCounts(Path file, BootstrapReport report) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("counts\n");
        for (Map.Entry<String, Integer> entry : report.counts().entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private static void writeIndex(Path file, Path runDir) throws Exception {
        Files.writeString(file, runDir.toString(), StandardCharsets.UTF_8);
    }

    private static void writeThrown(Path file) throws Exception {
        var defs = ie.orangep.reLootplusplus.runtime.RuntimeState.thrownDefs();
        if (defs == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("thrown\n");
        for (var def : defs) {
            sb.append(def.itemId());
            if (def.sourceLoc() != null) {
                sb.append('\t').append(def.sourceLoc().formatShort());
            }
            sb.append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
