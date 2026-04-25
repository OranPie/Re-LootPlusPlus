package ie.orangep.reLootplusplus.lucky.attr;

import java.util.List;
import java.util.Map;

/**
 * Sealed hierarchy of Lucky Block attribute value types.
 *
 * <p>Drop lines use a {@code key=value,key=value} format where values may be:
 * <ul>
 *   <li>{@link StringAttr} — plain string (or un-evaluated template like {@code #rand(1,3)})</li>
 *   <li>{@link DictAttr} — nested dict: {@code (key=value,...)}</li>
 *   <li>{@link ListAttr} — list: {@code [item;item;...]}</li>
 * </ul>
 */
public sealed interface LuckyAttr permits LuckyAttr.StringAttr, LuckyAttr.DictAttr, LuckyAttr.ListAttr {

    record StringAttr(String value) implements LuckyAttr {
        public static StringAttr of(String value) {
            return new StringAttr(value != null ? value : "");
        }

        /** Returns the raw string value, never null. */
        public String get() {
            return value;
        }
    }

    record DictAttr(Map<String, LuckyAttr> entries) implements LuckyAttr {
        public static DictAttr of(Map<String, LuckyAttr> entries) {
            return new DictAttr(java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(entries)));
        }

        public LuckyAttr get(String key) {
            return entries.get(key);
        }

        public String getString(String key) {
            LuckyAttr val = entries.get(key);
            if (val instanceof StringAttr s) return s.value();
            return val != null ? val.toString() : null;
        }
    }

    record ListAttr(List<LuckyAttr> items) implements LuckyAttr {
        public static ListAttr of(List<LuckyAttr> items) {
            return new ListAttr(java.util.Collections.unmodifiableList(new java.util.ArrayList<>(items)));
        }
    }
}
