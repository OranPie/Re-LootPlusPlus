package ie.orangep.reLootplusplus.bootstrap;

public final class BootstrapReport {
    private int packCount;
    private int fileCount;
    private int lineCount;
    private int uniqueWarnCount;
    private int totalWarnCount;
    private final java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Long> timings = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Integer> packFileCounts = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Integer> packLineCounts = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, java.util.Map<String, Integer>> packCounts = new java.util.LinkedHashMap<>();

    public void setPackCount(int packCount) {
        this.packCount = packCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    public void setLineCount(int lineCount) {
        this.lineCount = lineCount;
    }

    public void setUniqueWarnCount(int warnCount) {
        this.uniqueWarnCount = warnCount;
    }

    public void setTotalWarnCount(int warnCount) {
        this.totalWarnCount = warnCount;
    }

    public void putCount(String key, int value) {
        counts.put(key, value);
    }

    public void putTiming(String key, long millis) {
        timings.put(key, millis);
    }

    public void setPackFileCount(String packId, int value) {
        packFileCounts.put(packId, value);
    }

    public void setPackLineCount(String packId, int value) {
        packLineCounts.put(packId, value);
    }

    public void addPackCount(String packId, String key, int delta) {
        packCounts.computeIfAbsent(packId, k -> new java.util.LinkedHashMap<>()).merge(key, delta, Integer::sum);
    }

    public int packCount() {
        return packCount;
    }

    public int fileCount() {
        return fileCount;
    }

    public int lineCount() {
        return lineCount;
    }

    public int uniqueWarnCount() {
        return uniqueWarnCount;
    }

    public int totalWarnCount() {
        return totalWarnCount;
    }

    public java.util.Map<String, Integer> counts() {
        return java.util.Collections.unmodifiableMap(counts);
    }

    public java.util.Map<String, Long> timings() {
        return java.util.Collections.unmodifiableMap(timings);
    }

    public java.util.Map<String, Integer> packFileCounts() {
        return java.util.Collections.unmodifiableMap(packFileCounts);
    }

    public java.util.Map<String, Integer> packLineCounts() {
        return java.util.Collections.unmodifiableMap(packLineCounts);
    }

    public java.util.Map<String, java.util.Map<String, Integer>> packCounts() {
        return java.util.Collections.unmodifiableMap(packCounts);
    }
}
