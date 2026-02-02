package ie.orangep.reLootplusplus.config.model.drop;

public final class DropEntryCommand implements DropEntry {
    private final int weight;
    private final String command;

    public DropEntryCommand(int weight, String command) {
        this.weight = weight;
        this.command = command;
    }

    @Override
    public int weight() {
        return weight;
    }

    public String command() {
        return command;
    }
}
