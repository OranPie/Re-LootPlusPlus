package ie.orangep.reLootplusplus.runtime;

import ie.orangep.reLootplusplus.config.model.rule.BlockDropRemoval;
import ie.orangep.reLootplusplus.config.model.rule.BlockDropRule;

import java.util.ArrayList;
import java.util.List;

public final class BlockDropRegistry {
    private final List<BlockDropRule> addRules = new ArrayList<>();
    private final List<BlockDropRemoval> removeRules = new ArrayList<>();

    public void addRule(BlockDropRule rule) {
        addRules.add(rule);
    }

    public void addRemoval(BlockDropRemoval removal) {
        removeRules.add(removal);
    }

    public List<BlockDropRule> addRules() {
        return addRules;
    }

    public List<BlockDropRemoval> removeRules() {
        return removeRules;
    }
}
