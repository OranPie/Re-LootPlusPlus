package ie.orangep.reLootplusplus.config.model.drop;

import java.util.List;

public final class DropGroup {
    private final List<DropEntry> entries;

    public DropGroup(List<DropEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    public List<DropEntry> entries() {
        return entries;
    }
}
