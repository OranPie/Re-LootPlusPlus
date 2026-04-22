package ie.orangep.reLootplusplus.lucky.drop.action;

import ie.orangep.reLootplusplus.lucky.drop.LuckyDropContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.template.LuckyTemplateVars;

/**
 * Executes {@code type=time} Lucky drops.
 *
 * <p>Sets the world time-of-day.  The {@code ID} attribute accepts named times
 * or a numeric tick value:
 * <ul>
 *   <li>{@code day} / {@code sunrise} → 1000</li>
 *   <li>{@code noon} → 6000</li>
 *   <li>{@code sunset} / {@code evening} → 12000</li>
 *   <li>{@code night} / {@code midnight} → 13000</li>
 *   <li>any integer → that exact tick value</li>
 * </ul>
 */
public final class LuckyTimeDropAction {

    private LuckyTimeDropAction() {}

    public static void execute(LuckyDropLine drop, LuckyDropContext ctx) {
        LuckyTemplateVars.EvalContext evalCtx = new LuckyTemplateVars.EvalContext(
            ctx.world(), ctx.pos(), ctx.player(), ctx.world().getRandom()
        );

        String timeId = drop.rawId();
        if (timeId == null || timeId.isBlank()) {
            ctx.warnReporter().warn("LuckyTimeDrop", "missing time id", ctx.sourceLoc());
            return;
        }
        timeId = LuckyTemplateVars.evaluate(timeId, evalCtx).trim().toLowerCase();

        long ticks = switch (timeId) {
            case "day", "sunrise" -> 1000L;
            case "noon" -> 6000L;
            case "sunset", "evening" -> 12000L;
            case "night", "midnight" -> 13000L;
            default -> {
                try { yield Long.parseLong(timeId); }
                catch (NumberFormatException e) { yield -1L; }
            }
        };

        if (ticks < 0) {
            ctx.warnReporter().warn("LuckyTimeDrop",
                "unknown time value: " + timeId + " (expected day/night or tick count)", ctx.sourceLoc());
            return;
        }

        ctx.world().setTimeOfDay(ticks);
    }
}
