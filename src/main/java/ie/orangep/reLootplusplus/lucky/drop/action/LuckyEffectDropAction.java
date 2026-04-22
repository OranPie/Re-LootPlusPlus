package ie.orangep.reLootplusplus.lucky.drop.action;

import ie.orangep.reLootplusplus.lucky.drop.LuckyDropContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.template.LuckyTemplateVars;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/**
 * Executes {@code type=effect} Lucky drops.
 *
 * <p>Applies a status effect to the player.  The {@code duration} attribute is in
 * <b>seconds</b>; it is multiplied by 20 to convert to game ticks (matching the
 * original Lucky Block behaviour).  Instant effects receive duration=1 regardless.
 *
 * <p>Special IDs:
 * <ul>
 *   <li>{@code special_fire} — sets the player on fire for {@code duration} seconds</li>
 * </ul>
 */
public final class LuckyEffectDropAction {

    private LuckyEffectDropAction() {}

    public static void execute(LuckyDropLine drop, LuckyDropContext ctx) {
        if (ctx.player() == null) return;

        LuckyTemplateVars.EvalContext evalCtx = new LuckyTemplateVars.EvalContext(
            ctx.world(), ctx.pos(), ctx.player(), ctx.world().getRandom()
        );

        String effectId = drop.rawId();
        if (effectId == null || effectId.isBlank()) {
            ctx.warnReporter().warn("LuckyEffectDrop", "missing effect id", ctx.sourceLoc());
            return;
        }
        effectId = LuckyTemplateVars.evaluate(effectId, evalCtx).trim();

        int durationSecs = parseIntAttr(drop, "duration", evalCtx, 30);
        int amplifier = parseIntAttr(drop, "amplifier", evalCtx, 0);

        // Special effect: fire
        if ("special_fire".equalsIgnoreCase(effectId)) {
            ctx.player().setOnFireFor(durationSecs);
            return;
        }

        // Look up status effect
        Identifier id = Identifier.tryParse(effectId);
        if (id == null) id = Identifier.tryParse("minecraft:" + effectId.toLowerCase());
        if (id == null || !Registry.STATUS_EFFECT.containsId(id)) {
            ctx.warnReporter().warn("LuckyEffectDrop", "unknown effect: " + effectId, ctx.sourceLoc());
            return;
        }
        StatusEffect effect = Registry.STATUS_EFFECT.get(id);
        if (effect == null) {
            ctx.warnReporter().warn("LuckyEffectDrop", "null effect for id: " + id, ctx.sourceLoc());
            return;
        }

        int durationTicks = effect.isInstant() ? 1 : durationSecs * 20;
        StatusEffectInstance instance = new StatusEffectInstance(effect, durationTicks, amplifier);

        if (ctx.player() != null) {
            ctx.player().addStatusEffect(instance);
        }
    }

    private static int parseIntAttr(LuckyDropLine drop, String key, LuckyTemplateVars.EvalContext evalCtx, int fallback) {
        String s = drop.getString(key);
        if (s == null) return fallback;
        s = LuckyTemplateVars.evaluate(s, evalCtx);
        try { return (int) Math.round(LuckyTemplateVars.evalArithmetic(s, fallback)); }
        catch (Exception ignored) { return fallback; }
    }
}
