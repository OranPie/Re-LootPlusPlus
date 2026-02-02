package ie.orangep.reLootplusplus.config.model.drop;

public sealed interface DropEntry permits DropEntryItem, DropEntryEntity, DropEntryCommand {
    int weight();
}
