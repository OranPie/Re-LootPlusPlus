package ie.orangep.reLootplusplus.lucky.drop.action;

import ie.orangep.reLootplusplus.lucky.drop.LuckyDropContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.template.LuckyTemplateVars;
import net.minecraft.world.Difficulty;

/**
 * Executes {@code type=difficulty} Lucky drops.
 *
 * <p>Sets the world difficulty.  The {@code ID} attribute accepts the standard difficulty
 * names as well as their legacy numeric equivalents:
 * <ul>
 *   <li>{@code peaceful} / {@code p} / {@code 0}</li>
 *   <li>{@code easy} / {@code e} / {@code 1}</li>
 *   <li>{@code normal} / {@code n} / {@code 2}</li>
 *   <li>{@code hard} / {@code h} / {@code 3} / {@code 4}</li>
 * </ul>
 */
public final class LuckyDifficultyDropAction {

    private LuckyDifficultyDropAction() {}

    public static void execute(LuckyDropLine drop, LuckyDropContext ctx) {
        LuckyTemplateVars.EvalContext evalCtx = new LuckyTemplateVars.EvalContext(
            ctx.world(), ctx.pos(), ctx.player(), ctx.world().getRandom()
        );

        String diffId = drop.rawId();
        if (diffId == null || diffId.isBlank()) {
            ctx.warnReporter().warn("LuckyDifficultyDrop", "missing difficulty id", ctx.sourceLoc());
            return;
        }
        diffId = LuckyTemplateVars.evaluate(diffId, evalCtx).trim().toLowerCase();

        Difficulty difficulty = switch (diffId) {
            case "peaceful", "peacful", "p", "0" -> Difficulty.PEACEFUL;
            case "easy", "e", "1" -> Difficulty.EASY;
            case "normal", "n", "2" -> Difficulty.NORMAL;
            case "hard", "h", "3", "4" -> Difficulty.HARD;
            default -> null;
        };

        if (difficulty == null) {
            ctx.warnReporter().warn("LuckyDifficultyDrop",
                "unknown difficulty: " + diffId + " (expected peaceful/easy/normal/hard or 0-3)", ctx.sourceLoc());
            return;
        }

        if (ctx.world().getServer() != null) {
            ctx.world().getServer().setDifficulty(difficulty, true);
        }
    }
}
