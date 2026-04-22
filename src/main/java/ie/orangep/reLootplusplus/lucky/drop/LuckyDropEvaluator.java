package ie.orangep.reLootplusplus.lucky.drop;

import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.lucky.drop.action.*;

import java.util.List;

/**
 * Dispatches a single {@link LuckyDropLine} to the appropriate action class.
 */
public final class LuckyDropEvaluator {

    private LuckyDropEvaluator() {}

    /**
     * Executes one drop line against the given context.
     * Group drops are expanded and each entry is evaluated individually.
     */
    public static void evaluate(LuckyDropLine drop, LuckyDropContext ctx) {
        if (drop == null) return;

        if (drop.isGroup()) {
            Log.debug("LuckyDrop", String.format("[EVAL] group selected — count=%d entries=%d",
                drop.groupCount(), drop.groupEntries() != null ? drop.groupEntries().size() : 0));
            evaluateGroup(drop, ctx);
            return;
        }

        String type = drop.type();
        Log.debug("LuckyDrop", String.format("[EVAL] type=%-12s id=%s", type, drop.rawId()));
        switch (type) {
            case "item" -> LuckyItemDropAction.execute(drop, ctx);
            case "entity" -> LuckyEntityDropAction.execute(drop, ctx);
            case "block" -> LuckyBlockDropAction.execute(drop, ctx);
            case "chest" -> LuckyChestDropAction.execute(drop, ctx);
            case "throw", "throwable" -> LuckyThrowDropAction.execute(drop, ctx);
            case "command" -> LuckyCommandDropAction.execute(drop, ctx);
            case "structure" -> LuckyStructureDropAction.execute(drop, ctx);
            case "fill" -> LuckyFillDropAction.execute(drop, ctx);
            case "message" -> LuckyMessageDropAction.execute(drop, ctx);
            case "effect" -> LuckyEffectDropAction.execute(drop, ctx);
            case "explosion" -> LuckyExplosionDropAction.execute(drop, ctx);
            case "sound" -> LuckySoundDropAction.execute(drop, ctx);
            case "particle" -> LuckyParticleDropAction.execute(drop, ctx);
            case "difficulty" -> LuckyDifficultyDropAction.execute(drop, ctx);
            case "time" -> LuckyTimeDropAction.execute(drop, ctx);
            case "nothing" -> { /* intentional no-op */ }
            case "luckyprojectile" -> LuckyProjectileDropAction.execute(drop, ctx);
            default -> {
                if (type.contains(" ")) {
                    // type field contains a space-separated command format (e.g. "setblock ~1 ~ ~ chest 0 replace {...}")
                    // Execute the full type value as a server command.
                    Log.debug("LuckyDrop", String.format("[EVAL] command-as-type: %s", type.substring(0, Math.min(type.length(), 60))));
                    LuckyCommandDropAction.executeRaw(type, drop, ctx);
                } else {
                    // Unknown type — treat as entity shorthand (type name = entity ID).
                    // Handles type=falling_block, type=armorstand, etc.
                    Log.debug("LuckyDrop", String.format("[EVAL] entity shorthand type=%s rawId=%s", type, drop.rawId()));
                    LuckyEntityDropAction.executeAsShorthand(drop, ctx, type);
                }
            }
        }
    }

    private static void evaluateGroup(LuckyDropLine group, LuckyDropContext ctx) {
        List<LuckyDropLine> entries = group.groupEntries();
        if (entries == null || entries.isEmpty()) {
            Log.debug("LuckyDrop", "[GROUP] empty entries — skipped");
            return;
        }

        int count = group.groupCount();
        Log.debug("LuckyDrop", String.format("[GROUP] count=%d pool=%d", count, entries.size()));
        for (int i = 0; i < entries.size(); i++) {
            LuckyDropLine e = entries.get(i);
            Log.debug("LuckyDrop", String.format("[GROUP]   [%d] type=%-10s id=%s", i, e.type(), e.rawId()));
        }

        if (count < 0) {
            // Execute all entries
            for (LuckyDropLine entry : entries) evaluate(entry, ctx);
        } else {
            // Pick 'count' random entries (without replacement)
            java.util.List<LuckyDropLine> pool = new java.util.ArrayList<>(entries);
            java.util.Random rng = ctx.world().getRandom();
            int toSelect = Math.min(count, pool.size());
            Log.debug("LuckyDrop", String.format("[GROUP] picking %d/%d entries", toSelect, pool.size()));
            for (int i = 0; i < toSelect; i++) {
                int idx = rng.nextInt(pool.size());
                LuckyDropLine picked = pool.remove(idx);
                Log.debug("LuckyDrop", String.format("[GROUP]   picked[%d] type=%-10s id=%s", i, picked.type(), picked.rawId()));
                evaluate(picked, ctx);
            }
        }
    }
}
