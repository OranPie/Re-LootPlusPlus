package ie.orangep.reLootplusplus.config.model.drop;

import java.util.List;
import java.util.Random;

public final class DropRoller {
    private DropRoller() {
    }

    public static DropGroup rollGroup(List<DropGroup> groups, Random random) {
        if (groups == null || groups.isEmpty()) {
            return null;
        }
        int total = 0;
        for (DropGroup group : groups) {
            total += weightOf(group);
        }
        if (total <= 0) {
            return groups.get(0);
        }
        int roll = random.nextInt(total);
        int cursor = 0;
        for (DropGroup group : groups) {
            cursor += weightOf(group);
            if (roll < cursor) {
                return group;
            }
        }
        return groups.get(groups.size() - 1);
    }

    public static int weightOf(DropGroup group) {
        if (group == null || group.entries().isEmpty()) {
            return 0;
        }
        int weight = group.entries().get(0).weight();
        return Math.max(1, weight);
    }
}
