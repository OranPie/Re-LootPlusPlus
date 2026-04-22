package ie.orangep.reLootplusplus.lucky.drop;

import java.util.List;
import java.util.Random;

/**
 * Selects a {@link LuckyDropLine} from a list using luck-weighted selection.
 *
 * <h3>Weight calculation</h3>
 * Lucky Block uses a weighted pool where the effective weight of each entry is:
 * <pre>  weight = max(0, base_weight + luck_modifier * luck_value)</pre>
 * where {@code luck_modifier = 0.1} (from Lucky Block source).
 *
 * <h3>@chance gate</h3>
 * After selection, the {@code @chance} value (0.0–1.0) is tested independently.
 * If the chance roll fails, the drop is skipped entirely.
 */
public final class LuckyDropRoller {

    private static final float LUCK_MODIFIER = 0.1f;

    private LuckyDropRoller() {}

    /**
     * Picks one drop line from {@code drops} using luck-weighted selection.
     * Returns null if the list is empty or all weights are zero.
     *
     * <p>The first entry's luck weight determines selection probability for group entries
     * (per the Loot++ 1.8.9 convention).
     */
    public static LuckyDropLine roll(List<LuckyDropLine> drops, int luck, Random random) {
        if (drops == null || drops.isEmpty()) return null;

        float[] weights = new float[drops.size()];
        float totalWeight = 0f;
        for (int i = 0; i < drops.size(); i++) {
            float w = computeWeight(drops.get(i).luckWeight(), luck);
            weights[i] = w;
            totalWeight += w;
        }

        if (totalWeight <= 0f) return null;

        float target = random.nextFloat() * totalWeight;
        float cumulative = 0f;
        for (int i = 0; i < drops.size(); i++) {
            cumulative += weights[i];
            if (target <= cumulative) {
                return drops.get(i);
            }
        }
        // Fallback (floating point edge case)
        return drops.get(drops.size() - 1);
    }

    /**
     * Tests the {@code @chance} gate for a drop line.
     * Returns {@code true} if the drop should proceed.
     */
    public static boolean passesChance(LuckyDropLine drop, Random random) {
        float chance = drop.chance();
        if (chance >= 1.0f) return true;
        if (chance <= 0.0f) return false;
        return random.nextFloat() <= chance;
    }

    /**
     * Computes the effective weight for a drop entry given the current luck value.
     */
    static float computeWeight(int luckWeight, int luck) {
        float w = 1.0f + luckWeight * LUCK_MODIFIER + luck * LUCK_MODIFIER;
        return Math.max(0f, w);
    }
}
