package ie.orangep.reLootplusplus.diagnostic;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

public final class LegacyWarnReporter {
    private final Set<WarnKey> seen = Collections.synchronizedSet(new HashSet<>());
    private final List<WarnEntry> entries = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> typeCounts = new ConcurrentHashMap<>();
    private boolean logToConsole = true;

    public void setLogToConsole(boolean enabled) {
        this.logToConsole = enabled;
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
            Log.warn("[LootPP-Legacy] {} {}{}", type, detail, where);
        }
        entries.add(new WarnEntry(type, detail, loc, System.currentTimeMillis()));
        typeCounts.merge(type, 1, Integer::sum);
    }

    public int uniqueWarnCount() {
        return seen.size();
    }

    public int totalWarnCount() {
        return entries.size();
    }

    public List<WarnEntry> entries() {
        return List.copyOf(entries);
    }

    public Map<String, Integer> warnTypeCounts() {
        return Map.copyOf(typeCounts);
    }
}
