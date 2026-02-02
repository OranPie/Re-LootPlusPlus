package ie.orangep.reLootplusplus.runtime;

import ie.orangep.reLootplusplus.config.model.rule.ThrownDef;

import java.util.HashMap;
import java.util.Map;

public final class ThrownRegistry {
    private final Map<String, ThrownDef> byItemId = new HashMap<>();

    public void register(ThrownDef def) {
        byItemId.put(def.itemId(), def);
    }

    public ThrownDef get(String itemId) {
        return byItemId.get(itemId);
    }
}
