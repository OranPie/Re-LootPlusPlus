package ie.orangep.reLootplusplus.lucky.drop.action;

import ie.orangep.reLootplusplus.lucky.drop.LuckyDropContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.template.LuckyTemplateVars;
import net.minecraft.world.explosion.Explosion;

/**
 * Executes {@code type=explosion} Lucky drops.
 *
 * <p>Creates an explosion at the block centre (offset +0.5 on all axes).
 * The explosion radius/power is read from the {@code radius} or {@code damage} attribute
 * (both are supported; {@code radius} takes precedence as the more common legacy name).
 * The {@code fire} attribute controls whether the explosion starts fires.
 */
public final class LuckyExplosionDropAction {

    private LuckyExplosionDropAction() {}

    public static void execute(LuckyDropLine drop, LuckyDropContext ctx) {
        LuckyTemplateVars.EvalContext evalCtx = new LuckyTemplateVars.EvalContext(
            ctx.world(), ctx.pos(), ctx.player(), ctx.world().getRandom()
        );

        // Support both "radius" (legacy config name) and "damage" (internal Lucky name)
        float radius = parseFloatAttr(drop, "radius", evalCtx, -1f);
        if (radius < 0) radius = parseFloatAttr(drop, "damage", evalCtx, 6.0f);

        boolean fire = parseBoolAttr(drop, "fire", evalCtx, false);

        double x = ctx.pos().getX() + 0.5;
        double y = ctx.pos().getY() + 0.5;
        double z = ctx.pos().getZ() + 0.5;

        ctx.world().createExplosion(null, x, y, z, radius, fire,
            Explosion.DestructionType.BREAK);
    }

    private static float parseFloatAttr(LuckyDropLine drop, String key,
                                         LuckyTemplateVars.EvalContext evalCtx, float fallback) {
        String s = drop.getString(key);
        if (s == null) return fallback;
        s = LuckyTemplateVars.evaluate(s, evalCtx).trim();
        try { return Float.parseFloat(s); } catch (NumberFormatException e) { return fallback; }
    }

    private static boolean parseBoolAttr(LuckyDropLine drop, String key,
                                          LuckyTemplateVars.EvalContext evalCtx, boolean fallback) {
        String s = drop.getString(key);
        if (s == null) return fallback;
        s = LuckyTemplateVars.evaluate(s, evalCtx).trim().toLowerCase();
        if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) return true;
        if ("false".equals(s) || "0".equals(s) || "no".equals(s)) return false;
        return fallback;
    }
}
