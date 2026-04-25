package ie.orangep.reLootplusplus.lucky.structure;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import ie.orangep.reLootplusplus.pack.AddonPack;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Loads structure file bytes from an addon pack (zip or directory). */
public final class StructureFileLoader {

    private StructureFileLoader() {}

    @Nullable
    public static byte[] loadStructureFile(AddonPack pack, String relPath,
                                            LegacyWarnReporter reporter, SourceLoc loc) {
        Path zipPath = pack.zipPath();
        if (zipPath == null) {
            reporter.warn("LuckyStructure", "pack '" + pack.id() + "' has no zip path", loc);
            return null;
        }

        List<String> candidates = pathCandidates(relPath);

        if (Files.isDirectory(zipPath)) {
            for (String candidate : candidates) {
                Path fp = zipPath.resolve(candidate);
                if (Files.exists(fp)) {
                    try {
                        return Files.readAllBytes(fp);
                    } catch (IOException e) {
                        reporter.warn("LuckyStructure", "I/O error reading " + fp + ": " + e.getMessage(), loc);
                        return null;
                    }
                }
            }
        } else {
            try (ZipFile zip = new ZipFile(zipPath.toFile())) {
                for (String candidate : candidates) {
                    ZipEntry entry = zip.getEntry(candidate);
                    if (entry == null) {
                        // Case-insensitive search
                        String candidateLower = candidate.toLowerCase(Locale.ROOT);
                        Enumeration<? extends ZipEntry> entries = zip.entries();
                        while (entries.hasMoreElements()) {
                            ZipEntry e = entries.nextElement();
                            if (e.getName().toLowerCase(Locale.ROOT).equals(candidateLower)) {
                                entry = e;
                                break;
                            }
                        }
                    }
                    if (entry != null) {
                        try (InputStream in = zip.getInputStream(entry)) {
                            return in.readAllBytes();
                        } catch (IOException e) {
                            reporter.warn("LuckyStructure", "error reading zip entry " + candidate + ": " + e.getMessage(), loc);
                            return null;
                        }
                    }
                }
            } catch (IOException e) {
                reporter.warn("LuckyStructure", "I/O error reading zip " + zipPath + ": " + e.getMessage(), loc);
            }
        }
        return null;
    }

    private static List<String> pathCandidates(String relPath) {
        String cleaned = relPath.replace('\\', '/').replaceAll("^/+", "");
        return List.of(
            "structures/" + cleaned,
            "config/structures/" + cleaned,
            cleaned
        );
    }
}
