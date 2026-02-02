package ie.orangep.reLootplusplus.runtime;

import ie.orangep.reLootplusplus.config.model.rule.EntityDropRule;

import java.util.ArrayList;
import java.util.List;

public final class EntityDropRegistry {
    private final List<EntityDropRule> rules = new ArrayList<>();

    public void addRule(EntityDropRule rule) {
        rules.add(rule);
    }

    public List<EntityDropRule> rules() {
        return rules;
    }
}
