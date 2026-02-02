package ie.orangep.reLootplusplus.runtime;

import ie.orangep.reLootplusplus.config.model.rule.StackSizeRule;
import net.minecraft.item.Item;
import net.minecraft.util.registry.Registry;

import java.util.HashMap;
import java.util.Map;

public final class StackSizeRegistry {
    private final Map<String, Integer> overrides = new HashMap<>();

    public void addRule(StackSizeRule rule) {
        overrides.put(rule.itemId(), rule.maxSize());
    }

    public int overrideFor(Item item) {
        String id = Registry.ITEM.getId(item).toString();
        return overrides.getOrDefault(id, -1);
    }
}
