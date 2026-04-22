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
        ThrownDef def = byItemId.get(itemId);
        if (def != null) {
            return def;
        }
        String fallback = stripSuffix(itemId);
        if (fallback == null || fallback.equals(itemId)) {
            return null;
        }
        return byItemId.get(fallback);
    }

    private String stripSuffix(String itemId) {
        if (itemId == null) {
            return null;
        }
        int idx = itemId.indexOf(':');
        if (idx <= 0 || idx >= itemId.length() - 1) {
            return null;
        }
        String ns = itemId.substring(0, idx);
        String path = itemId.substring(idx + 1);
        int underscore = path.lastIndexOf('_');
        if (underscore <= 0 || underscore >= path.length() - 1) {
            return null;
        }
        String suffix = path.substring(underscore + 1);
        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) {
                return null;
            }
        }
        String base = path.substring(0, underscore);
        return ns + ":" + base;
    }
}
