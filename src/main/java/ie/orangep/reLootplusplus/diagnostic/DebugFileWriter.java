package ie.orangep.reLootplusplus.diagnostic;

import ie.orangep.reLootplusplus.config.ReLootPlusPlusConfig;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Writes debug and trace log lines to a per-run file.
 *
 * <p>All {@link Log#debug} and {@link Log#trace} calls are written here unconditionally
 * (regardless of console detail-level or module filter), giving a complete trace even when
 * console output is filtered or suppressed.
 *
 * <p>The file is created when {@link #open} is called during bootstrap (whenever
 * {@code logDetailLevel} is {@code detail} or {@code trace}). It is named
 * {@code debug-<yyyyMMdd-HHmmss>.log} inside the configured export directory,
 * and {@code debug-latest.log} is updated with the path to the most recent file.
 *
 * <h3>Line format</h3>
 * <pre>{@code [HH:mm:ss.SSS] [TRACE] [Module] message}</pre>
 */
public final class DebugFileWriter {

    private static final DateTimeFormatter STAMP      = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter LINE_STAMP = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static volatile BufferedWriter writer;
    private static final AtomicBoolean opened = new AtomicBoolean(false);
    private static final AtomicBoolean failed = new AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicLong lineCount = new java.util.concurrent.atomic.AtomicLong(0);

    private DebugFileWriter() {}

    /**
     * Opens the debug log file.  Called once during bootstrap when detail level &ge; DETAIL.
     *
     * @param exportDir root export directory (same as DiagnosticExporter)
     * @param levelName the configured detail level string (for the file header)
     * @param filters   module filter string (for the file header)
     */
    public static void open(Path exportDir, String levelName, String filters) {
        if (!opened.compareAndSet(false, true)) return;
        try {
            Files.createDirectories(exportDir);
            String stamp = LocalDateTime.now().format(STAMP);
            Path logFile = exportDir.resolve("debug-" + stamp + ".log");
            writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8);
            writeHeader(stamp, levelName, filters);
            // latest.txt pointer
            try {
                Files.writeString(exportDir.resolve("debug-latest.log"),
                    logFile.toAbsolutePath().toString() + "\n", StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
        } catch (IOException e) {
            failed.set(true);
            Log.LOGGER.warn("ReLoot++ [DebugFileWriter] Cannot open debug log: {}", e.getMessage());
        }
    }

    /** Writes one debug/trace line.  No-op if the writer was never opened or has failed. */
    public static void write(String level, String module, String message) {
        if (writer == null || failed.get()) return;
        // Enforce line limit from config
        ReLootPlusPlusConfig cfg = RuntimeState.config();
        int maxLines = cfg != null ? cfg.debugFileMaxLines : 0;
        if (maxLines > 0) {
            long n = lineCount.incrementAndGet();
            if (n == maxLines + 1) {
                // Write truncation notice then stop
                try {
                    synchronized (DebugFileWriter.class) {
                        writer.write("# [DebugFileWriter] Line limit reached (" + maxLines + ") — log truncated here.\n");
                        writer.flush();
                    }
                } catch (IOException ignored) {}
                failed.set(true);
                return;
            } else if (n > maxLines + 1) {
                return;
            }
        }
        try {
            String line = "[" + LocalDateTime.now().format(LINE_STAMP) + "] ["
                + level + "] [" + (module != null ? module : "") + "] " + message + "\n";
            synchronized (DebugFileWriter.class) {
                writer.write(line);
                writer.flush();
            }
        } catch (IOException e) {
            failed.set(true);
        }
    }

    /** Flushes and closes the debug log file. */
    public static void close() {
        BufferedWriter w = writer;
        writer = null;
        if (w != null) {
            try { w.close(); } catch (IOException ignored) {}
        }
    }

    /** Returns {@code true} if the writer is open and healthy. */
    public static boolean isActive() {
        return writer != null && !failed.get();
    }

    // -------------------------------------------------------------------------

    private static void writeHeader(String stamp, String levelName, String filters) {
        try {
            writer.write("# Re-LootPlusPlus debug trace\n");
            writer.write("# started=" + stamp + "  level=" + levelName + "  filters=" + filters + "\n");
            writer.write("# Format: [HH:mm:ss.SSS] [LEVEL] [Module] message\n");
            writer.write("#\n");
            writer.flush();
        } catch (IOException ignored) {}
    }
}
