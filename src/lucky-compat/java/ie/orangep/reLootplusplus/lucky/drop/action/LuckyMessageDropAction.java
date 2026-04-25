package ie.orangep.reLootplusplus.lucky.drop.action;

import ie.orangep.reLootplusplus.lucky.drop.LuckyDropContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.template.LuckyTemplateVars;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;

/**
 * Executes {@code type=message} Lucky drops.
 *
 * <p>Sends the {@code ID} attribute as a chat message to the nearest player.
 * Legacy colour codes ({@code $0}–{@code $f}, {@code $r}) are converted to
 * the § (section sign) equivalents.
 */
public final class LuckyMessageDropAction {

    private LuckyMessageDropAction() {}

    public static void execute(LuckyDropLine drop, LuckyDropContext ctx) {
        LuckyTemplateVars.EvalContext evalCtx = new LuckyTemplateVars.EvalContext(
            ctx.world(), ctx.pos(), ctx.player(), ctx.world().getRandom()
        );

        String message = drop.rawId();
        if (message == null || message.isBlank()) {
            // Also check message= attr
            message = drop.getString("message");
        }
        if (message == null || message.isBlank()) return;

        message = LuckyTemplateVars.evaluate(message, evalCtx);
        // Convert legacy colour codes: $X → §X
        message = convertLegacyColors(message);

        LiteralText text = new LiteralText(message);
        if (ctx.player() instanceof ServerPlayerEntity sp) {
            sp.sendMessage(text, false);
        } else if (ctx.world().getServer() != null) {
            // Broadcast to all online players
            for (ServerPlayerEntity p : ctx.world().getServer().getPlayerManager().getPlayerList()) {
                p.sendMessage(text, false);
            }
        }
    }

    /** Replaces {@code $X} (where X is a hex digit or 'r'/'k'/'l'/'m'/'n'/'o') with {@code §X}. */
    private static String convertLegacyColors(String s) {
        if (s == null || !s.contains("$")) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '$' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                if (isColorCode(next)) {
                    sb.append('§').append(next);
                    i++; // skip next char
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static boolean isColorCode(char c) {
        return (c >= '0' && c <= '9')
            || (c >= 'a' && c <= 'f')
            || (c >= 'A' && c <= 'F')
            || c == 'r' || c == 'R'
            || c == 'k' || c == 'K'
            || c == 'l' || c == 'L'
            || c == 'm' || c == 'M'
            || c == 'n' || c == 'N'
            || c == 'o' || c == 'O';
    }
}
