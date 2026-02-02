package ie.orangep.reLootplusplus.diagnostic;

public final class SourceLoc {
    private final String packId;
    private final String packPath;
    private final String innerPath;
    private final int lineNumber;
    private final String rawLine;

    public SourceLoc(String packId, String packPath, String innerPath, int lineNumber, String rawLine) {
        this.packId = packId;
        this.packPath = packPath;
        this.innerPath = innerPath;
        this.lineNumber = lineNumber;
        this.rawLine = rawLine;
    }

    public String packId() {
        return packId;
    }

    public String packPath() {
        return packPath;
    }

    public String innerPath() {
        return innerPath;
    }

    public int lineNumber() {
        return lineNumber;
    }

    public String rawLine() {
        return rawLine;
    }

    public String formatShort() {
        return packId + ":" + innerPath + ":" + lineNumber;
    }

    public String formatFull() {
        return packPath + ":" + innerPath + ":" + lineNumber;
    }
}
