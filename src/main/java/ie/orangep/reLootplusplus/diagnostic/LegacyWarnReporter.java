package ie.orangep.reLootplusplus.diagnostic;

import java.util.Collections;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class LegacyWarnReporter {
    private final Set<WarnKey> seen = Collections.synchronizedSet(new HashSet<>());
    private final List<WarnEntry> entries = new ArrayList<>(8192);
    private final Object entriesLock = new Object();
    private final Map<String, Integer> typeCounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> consoleTypeCounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> consoleSuppressedTypeCounts = new ConcurrentHashMap<>();
    private boolean logToConsole = true;
    private int consoleLimitPerType = Integer.MAX_VALUE;
    private boolean logSuppressionSummary = true;

    public void setLogToConsole(boolean enabled) {
        this.logToConsole = enabled;
    }

    public void setConsoleLimitPerType(int limitPerType) {
        this.consoleLimitPerType = limitPerType <= 0 ? Integer.MAX_VALUE : limitPerType;
    }

    public void setLogSuppressionSummary(boolean enabled) {
        this.logSuppressionSummary = enabled;
    }

    public void warnOnce(String type, String detail, SourceLoc loc) {
        WarnKey key = WarnKey.of(type, detail, loc);
        if (seen.add(key)) {
            warn(type, detail, loc);
        }
    }

    public void warn(String type, String detail, SourceLoc loc) {
        String where = loc == null ? "" : (" @ " + loc.formatShort());
        if (logToConsole) {
            int seenForType = consoleTypeCounts.merge(type, 1, Integer::sum);
            if (seenForType <= consoleLimitPerType) {
                Log.warnAlways("Legacy", "{} {}{}", type, detail, where);
            } else {
                int suppressed = consoleSuppressedTypeCounts.merge(type, 1, Integer::sum);
                if (suppressed == 1) {
                    Log.warnAlways(
                        "Legacy",
                        "{} more warnings suppressed after first {} entries (summary at end)",
                        type,
                        consoleLimitPerType
                    );
                }
            }
        }
        synchronized (entriesLock) {
            entries.add(new WarnEntry(type, detail, loc, System.currentTimeMillis()));
        }
        typeCounts.merge(type, 1, Integer::sum);
    }

    public void flushConsoleSummary() {
        if (!logToConsole || !logSuppressionSummary || consoleSuppressedTypeCounts.isEmpty()) {
            return;
        }
        String summary = consoleSuppressedTypeCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining(", "));
        Log.warnAlways("Legacy", "suppressed warning details: {}", summary);
    }

    public int uniqueWarnCount() {
        return seen.size();
    }

    public int totalWarnCount() {
        synchronized (entriesLock) {
            return entries.size();
        }
    }

    public List<WarnEntry> entries() {
        synchronized (entriesLock) {
            return List.copyOf(entries);
        }
    }

    public Map<String, Integer> warnTypeCounts() {
        return Map.copyOf(typeCounts);
    }
}
