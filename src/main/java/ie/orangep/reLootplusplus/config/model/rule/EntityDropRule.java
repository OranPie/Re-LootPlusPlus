package ie.orangep.reLootplusplus.config.model.rule;

import ie.orangep.reLootplusplus.config.model.drop.DropGroup;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

import java.util.List;

public final class EntityDropRule {
    private final String entityId;
    private final List<DropGroup> groups;
    private final SourceLoc sourceLoc;

    public EntityDropRule(String entityId, List<DropGroup> groups, SourceLoc sourceLoc) {
        this.entityId = entityId;
        this.groups = List.copyOf(groups);
        this.sourceLoc = sourceLoc;
    }

    public String entityId() {
        return entityId;
    }

    public List<DropGroup> groups() {
        return groups;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
