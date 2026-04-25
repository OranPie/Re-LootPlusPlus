package ie.orangep.reLootplusplus.lucky.drop.action;

import ie.orangep.reLootplusplus.lucky.drop.LuckyDropContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.template.LuckyTemplateVars;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/**
 * Executes {@code type=sound} Lucky drops.
 *
 * <p>Plays a sound event at the block position.
 * Attributes:
 * <ul>
 *   <li>{@code ID} — sound event identifier</li>
 *   <li>{@code volume} — volume multiplier (default 1.0)</li>
 *   <li>{@code pitch} — pitch multiplier (default 1.0)</li>
 * </ul>
 */
public final class LuckySoundDropAction {

    private LuckySoundDropAction() {}

    public static void execute(LuckyDropLine drop, LuckyDropContext ctx) {
        LuckyTemplateVars.EvalContext evalCtx = new LuckyTemplateVars.EvalContext(
            ctx.world(), ctx.pos(), ctx.player(), ctx.world().getRandom()
        );

        String soundId = drop.rawId();
        if (soundId == null || soundId.isBlank()) {
            ctx.warnReporter().warn("LuckySoundDrop", "missing sound id", ctx.sourceLoc());
            return;
        }
        soundId = LuckyTemplateVars.evaluate(soundId, evalCtx).trim();

        float volume = parseFloatAttr(drop, "volume", evalCtx, 1.0f);
        float pitch  = parseFloatAttr(drop, "pitch",  evalCtx, 1.0f);

        Identifier id = Identifier.tryParse(soundId);
        if (id == null) id = Identifier.tryParse("minecraft:" + soundId.replace('.', '/'));
        if (id == null) {
            ctx.warnReporter().warn("LuckySoundDrop", "invalid sound id: " + soundId, ctx.sourceLoc());
            return;
        }

        SoundEvent sound = Registry.SOUND_EVENT.get(id);
        if (sound == null) {
            // Dynamically create for unknown sound IDs (addon sounds)
            sound = new SoundEvent(id);
        }

        ctx.world().playSound(
            null,
            ctx.pos().getX() + 0.5, ctx.pos().getY() + 0.5, ctx.pos().getZ() + 0.5,
            sound, SoundCategory.BLOCKS, volume, pitch
        );
    }

    private static float parseFloatAttr(LuckyDropLine drop, String key,
                                         LuckyTemplateVars.EvalContext evalCtx, float fallback) {
        String s = drop.getString(key);
        if (s == null) return fallback;
        s = LuckyTemplateVars.evaluate(s, evalCtx).trim();
        try { return Float.parseFloat(s); } catch (NumberFormatException e) { return fallback; }
    }
}
