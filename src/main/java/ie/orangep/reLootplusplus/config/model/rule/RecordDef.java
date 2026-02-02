package ie.orangep.reLootplusplus.config.model.rule;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

public final class RecordDef {
    private final String recordName;
    private final String description;
    private final SourceLoc sourceLoc;

    public RecordDef(String recordName, String description, SourceLoc sourceLoc) {
        this.recordName = recordName;
        this.description = description;
        this.sourceLoc = sourceLoc;
    }

    public String recordName() {
        return recordName;
    }

    public String description() {
        return description;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
