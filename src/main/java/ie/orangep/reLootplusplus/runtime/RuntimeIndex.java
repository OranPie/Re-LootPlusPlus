package ie.orangep.reLootplusplus.runtime;

import ie.orangep.reLootplusplus.config.model.rule.CommandRule;
import ie.orangep.reLootplusplus.config.model.rule.EffectRule;
import ie.orangep.reLootplusplus.runtime.trigger.TriggerType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RuntimeIndex {
    private final Map<TriggerType, Map<String, List<CommandRule>>> commandRules = new HashMap<>();
    private final Map<TriggerType, Map<String, List<EffectRule>>> effectRules = new HashMap<>();
    private final Map<TriggerType, Map<String, List<EffectRule>>> blockEffectRules = new HashMap<>();
    private final Map<TriggerType, Map<String, List<CommandRule>>> blockCommandRules = new HashMap<>();

    public void addCommandRule(TriggerType trigger, CommandRule rule) {
        if (rule.itemKey() != null) {
            commandRules
                .computeIfAbsent(trigger, unused -> new HashMap<>())
                .computeIfAbsent(rule.itemKey().itemId(), unused -> new ArrayList<>())
                .add(rule);
        } else if (rule.blockKey() != null) {
            blockCommandRules
                .computeIfAbsent(trigger, unused -> new HashMap<>())
                .computeIfAbsent(rule.blockKey().blockId(), unused -> new ArrayList<>())
                .add(rule);
        }
    }

    public void addEffectRule(TriggerType trigger, EffectRule rule) {
        if (rule.itemKey() != null) {
            effectRules
                .computeIfAbsent(trigger, unused -> new HashMap<>())
                .computeIfAbsent(rule.itemKey().itemId(), unused -> new ArrayList<>())
                .add(rule);
        } else if (rule.blockKey() != null) {
            blockEffectRules
                .computeIfAbsent(trigger, unused -> new HashMap<>())
                .computeIfAbsent(rule.blockKey().blockId(), unused -> new ArrayList<>())
                .add(rule);
        }
    }

    public List<CommandRule> commandRules(TriggerType trigger, String itemId) {
        Map<String, List<CommandRule>> byItem = commandRules.getOrDefault(trigger, Map.of());
        List<CommandRule> direct = byItem.get(itemId);
        List<CommandRule> wildcard = byItem.get("*");
        if (wildcard == null || wildcard.isEmpty()) {
            return direct == null ? List.of() : direct;
        }
        if (direct == null || direct.isEmpty()) {
            return wildcard;
        }
        List<CommandRule> combined = new ArrayList<>(direct.size() + wildcard.size());
        combined.addAll(direct);
        combined.addAll(wildcard);
        return combined;
    }

    public List<CommandRule> blockCommandRules(TriggerType trigger, String blockId) {
        return blockCommandRules
            .getOrDefault(trigger, Map.of())
            .getOrDefault(blockId, List.of());
    }

    public List<EffectRule> effectRules(TriggerType trigger, String itemId) {
        Map<String, List<EffectRule>> byItem = effectRules.getOrDefault(trigger, Map.of());
        List<EffectRule> direct = byItem.get(itemId);
        List<EffectRule> wildcard = byItem.get("*");
        if (wildcard == null || wildcard.isEmpty()) {
            return direct == null ? List.of() : direct;
        }
        if (direct == null || direct.isEmpty()) {
            return wildcard;
        }
        List<EffectRule> combined = new ArrayList<>(direct.size() + wildcard.size());
        combined.addAll(direct);
        combined.addAll(wildcard);
        return combined;
    }

    public List<EffectRule> blockEffectRules(TriggerType trigger, String blockId) {
        return blockEffectRules
            .getOrDefault(trigger, Map.of())
            .getOrDefault(blockId, List.of());
    }
}
