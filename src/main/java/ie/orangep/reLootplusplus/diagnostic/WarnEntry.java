package ie.orangep.reLootplusplus.diagnostic;

public final class WarnEntry {
    private final String type;
    private final String detail;
    private final SourceLoc sourceLoc;
    private final long timestamp;

    public WarnEntry(String type, String detail, SourceLoc sourceLoc, long timestamp) {
        this.type = type;
        this.detail = detail;
        this.sourceLoc = sourceLoc;
        this.timestamp = timestamp;
    }

    public String type() {
        return type;
    }

    public String detail() {
        return detail;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }

    public long timestamp() {
        return timestamp;
    }
}
