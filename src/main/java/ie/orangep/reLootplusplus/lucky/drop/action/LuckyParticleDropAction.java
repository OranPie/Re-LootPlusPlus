package ie.orangep.reLootplusplus.lucky.drop.action;

import ie.orangep.reLootplusplus.lucky.drop.LuckyDropContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.template.LuckyTemplateVars;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/**
 * Executes {@code type=particle} Lucky drops.
 *
 * <p>Spawns particles at the block centre.
 * Attributes:
 * <ul>
 *   <li>{@code ID} — particle type identifier</li>
 *   <li>{@code particleAmount} or {@code amount} — number of particles (default 10)</li>
 * </ul>
 */
public final class LuckyParticleDropAction {

    private LuckyParticleDropAction() {}

    public static void execute(LuckyDropLine drop, LuckyDropContext ctx) {
        LuckyTemplateVars.EvalContext evalCtx = new LuckyTemplateVars.EvalContext(
            ctx.world(), ctx.pos(), ctx.player(), ctx.world().getRandom()
        );

        String particleId = drop.rawId();
        if (particleId == null || particleId.isBlank()) {
            ctx.warnReporter().warn("LuckyParticleDrop", "missing particle id", ctx.sourceLoc());
            return;
        }
        particleId = LuckyTemplateVars.evaluate(particleId, evalCtx).trim();

        int count = parseIntAttr(drop, "particleAmount", evalCtx, -1);
        if (count < 0) count = parseIntAttr(drop, "amount", evalCtx, 10);

        Identifier id = Identifier.tryParse(particleId);
        if (id == null) id = Identifier.tryParse("minecraft:" + particleId.toLowerCase());
        if (id == null) {
            ctx.warnReporter().warn("LuckyParticleDrop", "invalid particle id: " + particleId, ctx.sourceLoc());
            return;
        }

        ParticleType<?> particleType = Registry.PARTICLE_TYPE.get(id);
        if (particleType == null) {
            ctx.warnReporter().warn("LuckyParticleDrop", "unknown particle: " + particleId, ctx.sourceLoc());
            return;
        }

        if (particleType instanceof ParticleEffect particleEffect) {
            double cx = ctx.pos().getX() + 0.5;
            double cy = ctx.pos().getY() + 0.5;
            double cz = ctx.pos().getZ() + 0.5;
            ctx.world().spawnParticles(particleEffect, cx, cy, cz, count, 0.5, 0.5, 0.5, 0.1);
        } else {
            ctx.warnReporter().warn("LuckyParticleDrop",
                "particle type not usable without parameters: " + particleId, ctx.sourceLoc());
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
