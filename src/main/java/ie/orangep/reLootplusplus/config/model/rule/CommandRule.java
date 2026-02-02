package ie.orangep.reLootplusplus.config.model.rule;

import ie.orangep.reLootplusplus.config.model.key.ItemKey;
import ie.orangep.reLootplusplus.config.model.key.BlockKey;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

import java.util.Collections;
import java.util.List;

public final class CommandRule {
    private final String triggerKey;
    private final ItemKey itemKey;
    private final BlockKey blockKey;
    private final double probability;
    private final String command;
    private final List<String> setBonusItems;
    private final SourceLoc sourceLoc;

    public CommandRule(
        String triggerKey,
        ItemKey itemKey,
        double probability,
        String command,
        List<String> setBonusItems,
        SourceLoc sourceLoc
    ) {
        this.triggerKey = triggerKey;
        this.itemKey = itemKey;
        this.blockKey = null;
        this.probability = probability;
        this.command = command;
        this.setBonusItems = setBonusItems == null ? Collections.emptyList() : List.copyOf(setBonusItems);
        this.sourceLoc = sourceLoc;
    }

    public CommandRule(
        String triggerKey,
        BlockKey blockKey,
        double probability,
        String command,
        SourceLoc sourceLoc
    ) {
        this.triggerKey = triggerKey;
        this.itemKey = null;
        this.blockKey = blockKey;
        this.probability = probability;
        this.command = command;
        this.setBonusItems = Collections.emptyList();
        this.sourceLoc = sourceLoc;
    }

    public String triggerKey() {
        return triggerKey;
    }

    public ItemKey itemKey() {
        return itemKey;
    }

    public BlockKey blockKey() {
        return blockKey;
    }

    public double probability() {
        return probability;
    }

    public String command() {
        return command;
    }

    public List<String> setBonusItems() {
        return setBonusItems;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
