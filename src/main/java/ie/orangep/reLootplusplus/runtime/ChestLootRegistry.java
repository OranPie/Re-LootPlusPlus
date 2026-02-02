package ie.orangep.reLootplusplus.runtime;

import ie.orangep.reLootplusplus.config.model.rule.ChestAmountRule;
import ie.orangep.reLootplusplus.config.model.rule.ChestLootRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChestLootRegistry {
    private final Map<String, ChestAmountRule> amounts = new HashMap<>();
    private final Map<String, List<ChestLootRule>> loots = new HashMap<>();

    public void addAmount(ChestAmountRule rule) {
        amounts.put(rule.chestType(), rule);
    }

    public void addLoot(ChestLootRule rule) {
        loots.computeIfAbsent(rule.chestType(), key -> new ArrayList<>()).add(rule);
    }

    public ChestAmountRule amountFor(String chestType) {
        return amounts.get(chestType);
    }

    public List<ChestLootRule> lootFor(String chestType) {
        return loots.getOrDefault(chestType, List.of());
    }

    public Map<String, List<ChestLootRule>> allLoots() {
        return loots;
    }
}
