package ie.orangep.reLootplusplus.lucky.drop;

import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.LiteralText;

import java.util.List;
import java.util.Random;

/**
 * Top-level Lucky drop evaluation API.
 *
 * <p>Called by {@code LuckyBlockBreakHook} and {@code LuckyDropEvalCommand}.
 *
 * <h3>Evaluation pipeline</h3>
 * <ol>
 *   <li>Receive pre-parsed {@link LuckyDropLine} instances (from {@code LuckyAddonLoader})</li>
 *   <li>Roll one drop via {@link LuckyDropRoller} using the context luck value</li>
 *   <li>Test the {@code @chance} gate</li>
 *   <li>Dispatch via {@link LuckyDropEvaluator}</li>
 * </ol>
 *
 * <p><b>Performance:</b> Prefer the {@code List<LuckyDropLine>} overloads so that parsing
 * happens once at load time (in {@code LuckyAddonLoader}) rather than on every block break.
 * The {@code List<String>} overloads are kept for backwards-compatibility (e.g. block entity
 * custom drops) but are slower.
 */
public final class LuckyDropEngine {

    private LuckyDropEngine() {}

    /**
     * Evaluates a pre-parsed Lucky drop list.
     * Prefer this over the raw-string overload — no re-parsing cost.
     *
     * @param ctx    runtime context
     * @param drops  pre-parsed drop lines from {@code LuckyAddonLoader}
     * @param dryRun if true, rolls and logs but does not execute actions
     */
    public static LuckyDropLine evaluate(LuckyDropContext ctx, List<LuckyDropLine> drops, boolean dryRun) {
        if (drops == null || drops.isEmpty()) return null;

        Log.debug("LuckyDrop", String.format("[ENGINE] evaluate — parsedDrops=%d luck=%d pos=%s",
            drops.size(), ctx.luck(), ctx.pos()));

        Random random = ctx.world().getRandom();
        LuckyDropLine selected = LuckyDropRoller.roll(drops, ctx.luck(), random);
        if (selected == null) {
            Log.debug("LuckyDrop", "[ENGINE] roll returned null — no drop selected");
            return null;
        }

        Log.debug("LuckyDrop", String.format("[ENGINE] rolled → type=%-10s isGroup=%b chance=%.2f luckWeight=%d",
            selected.type(), selected.isGroup(), selected.chance(), selected.luckWeight()));

        if (!LuckyDropRoller.passesChance(selected, random)) {
            Log.debug("LuckyDrop", String.format("[ENGINE] chance gate FAILED (chance=%.2f)", selected.chance()));
            return null;
        }

        if (!dryRun) {
            Log.debug("LuckyDrop", "[ENGINE] executing drop...");
            LuckyDropEvaluator.evaluate(selected, ctx);
        }

        logDropSelection(ctx, selected, drops.size(), dryRun);
        return selected;
    }

    /** Convenience overload — not a dry run (pre-parsed drops). */
    public static LuckyDropLine evaluate(LuckyDropContext ctx, List<LuckyDropLine> drops) {
        return evaluate(ctx, drops, false);
    }

    /**
     * Parses raw strings then evaluates. Slower — use when pre-parsed drops are unavailable
     * (e.g. block entity custom drops).
     */
    public static LuckyDropLine evaluateRaw(LuckyDropContext ctx, List<String> rawLines, boolean dryRun) {
        if (rawLines == null || rawLines.isEmpty()) return null;
        LuckyDropParser parser = new LuckyDropParser(ctx.warnReporter(), ctx.sourceLoc());
        List<LuckyDropLine> drops = parser.parseLines(rawLines);
        Log.debug("LuckyDrop", String.format("[ENGINE] evaluateRaw — rawLines=%d → parsed=%d", rawLines.size(), drops.size()));
        return evaluate(ctx, drops, dryRun);
    }

    /** Convenience overload — not a dry run (raw strings). */
    public static LuckyDropLine evaluateRaw(LuckyDropContext ctx, List<String> rawLines) {
        return evaluateRaw(ctx, rawLines, false);
    }

    /**
     * Simulates N evaluations and returns a count map.
     * Prefers pre-parsed drops but falls back to raw parsing.
     */
    public static java.util.Map<String, Integer> simulateCounts(LuckyDropContext ctx, List<LuckyDropLine> drops, int times) {
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        if (drops == null || drops.isEmpty()) return counts;
        Random random = ctx.world().getRandom();
        for (int i = 0; i < times; i++) {
            LuckyDropLine selected = LuckyDropRoller.roll(drops, ctx.luck(), random);
            if (selected == null) continue;
            if (!LuckyDropRoller.passesChance(selected, random)) {
                counts.merge("(chance_failed)", 1, Integer::sum);
                continue;
            }
            String key = selected.isGroup() ? "(group)" : (selected.type() + ":" + selected.rawId());
            counts.merge(key, 1, Integer::sum);
        }
        return counts;
    }

    /** Raw-string overload for simulateCounts — use when pre-parsed drops are unavailable. */
    public static java.util.Map<String, Integer> simulateCountsRaw(LuckyDropContext ctx, List<String> rawLines, int times) {
        if (rawLines == null || rawLines.isEmpty()) return new java.util.LinkedHashMap<>();
        LuckyDropParser parser = new LuckyDropParser(ctx.warnReporter(), ctx.sourceLoc());
        return simulateCounts(ctx, parser.parseLines(rawLines), times);
    }

    // -------------------------------------------------------------------------
    // Drop selection logging
    // -------------------------------------------------------------------------

    /**
     * Logs a pretty-printed summary of the selected drop.
     * Console output is gated behind {@code logDebug=true}.
     * Chat message is always sent to the player (when present).
     */
    private static void logDropSelection(LuckyDropContext ctx, LuckyDropLine drop, int poolSize, boolean dryRun) {
        boolean doLog = Log.isDebugEnabled();
        boolean hasPlayer = ctx.player() != null;
        if (!doLog && !hasPlayer) return;

        String type    = drop.isGroup() ? "group" : drop.type();
        String id      = drop.isGroup() ? "(group-" + (drop.groupEntries() != null ? drop.groupEntries().size() : 0) + ")" : (drop.rawId() != null ? drop.rawId() : "(none)");
        String pos     = ctx.pos().getX() + ", " + ctx.pos().getY() + ", " + ctx.pos().getZ();
        String luck    = (ctx.luck() >= 0 ? "+" : "") + ctx.luck();
        float  weight  = LuckyDropRoller.computeWeight(drop.luckWeight(), ctx.luck());
        float  chance  = drop.chance();
        String dryTag  = dryRun ? " [DRY]" : "";
        String source  = ctx.sourceLoc() != null ? ctx.sourceLoc().packId() : "?";

        if (doLog) {
            String line1 = String.format("╔══ Lucky Drop%s ══════════════════════════════╗", dryTag);
            String line2 = String.format("║  type=%-10s  id=%-28s║", type, shorten(id, 28));
            String line3 = String.format("║  pos=%-18s  luck=%-5s  pool=%d      ║", pos, luck, poolSize);
            String line4 = String.format("║  weight=%-7.2f  chance=%-5.2f  src=%-18s║", weight, chance, shorten(source, 18));
            String line5 = "╚══════════════════════════════════════════════════╝";
            Log.debug("LuckyDrop", line1);
            Log.debug("LuckyDrop", line2);
            Log.debug("LuckyDrop", line3);
            Log.debug("LuckyDrop", line4);
            Log.debug("LuckyDrop", line5);
        }

        // Send chat to player with Minecraft color codes
        if (hasPlayer && !ctx.world().isClient()) {
            String typeColor = switch (type) {
                case "item"      -> "§b";
                case "entity"    -> "§a";
                case "command"   -> "§e";
                case "block"     -> "§6";
                case "structure" -> "§5";
                case "nothing"   -> "§8";
                default          -> "§f";
            };
            String msg = "§7[§aLucky Drop§7]" + dryTag + " "
                + typeColor + type + "§7: §f" + shorten(id, 40)
                + "  §7luck:§d" + luck
                + "  §7w:§a" + String.format("%.1f", weight);
            try {
                ctx.player().sendMessage(new LiteralText(msg), false);
            } catch (Exception ignored) {
                // Don't crash on display failure
            }
        }
    }

    private static String shorten(String s, int max) {
        if (s == null) return "(null)";
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }
}
