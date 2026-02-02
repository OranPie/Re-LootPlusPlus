package ie.orangep.reLootplusplus.config.model.block;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

public final class RawBlockAdditionDef {
    private final String type;
    private final String rawLine;
    private final SourceLoc sourceLoc;

    public RawBlockAdditionDef(String type, String rawLine, SourceLoc sourceLoc) {
        this.type = type;
        this.rawLine = rawLine;
        this.sourceLoc = sourceLoc;
    }

    public String type() {
        return type;
    }

    public String rawLine() {
        return rawLine;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
