package ie.orangep.reLootplusplus.lucky.drop.action;

import ie.orangep.reLootplusplus.command.exec.ExecContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.template.LuckyTemplateVars;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

/**
 * Executes {@code type=command} Lucky drops.
 *
 * <p>Runs a server-side command via {@code ServerCommandManager}.
 * Commands use the Lucky Block position as the command source.
 */
public final class LuckyCommandDropAction {

    private LuckyCommandDropAction() {}

    public static void execute(LuckyDropLine drop, LuckyDropContext ctx) {
        LuckyTemplateVars.EvalContext evalCtx = new LuckyTemplateVars.EvalContext(
            ctx.world(), ctx.pos(), ctx.player(), ctx.world().getRandom()
        );

        String command = drop.getString("command");
        if (command == null) command = drop.rawId();
        if (command == null || command.isBlank()) {
            ctx.warnReporter().warn("LuckyCommandDrop", "missing command", ctx.sourceLoc());
            return;
        }
        command = LuckyTemplateVars.evaluate(command, evalCtx);
        runCommand(command, drop, ctx);
    }

    /** Executes a raw command string (used for type=setblock / command-as-type drops). */
    public static void executeRaw(String rawCommand, LuckyDropLine drop, LuckyDropContext ctx) {
        LuckyTemplateVars.EvalContext evalCtx = new LuckyTemplateVars.EvalContext(
            ctx.world(), ctx.pos(), ctx.player(), ctx.world().getRandom()
        );
        String command = LuckyTemplateVars.evaluate(rawCommand, evalCtx);
        runCommand(command, drop, ctx);
    }

    private static void runCommand(String command, LuckyDropLine drop, LuckyDropContext ctx) {
        // Strip leading slash if present
        if (command.startsWith("/")) command = command.substring(1);

        if (isTeleportCommand(command)) {
            ExecContext execCtx = new ExecContext(
                ctx.world(),
                ctx.pos(),
                ctx.player(),
                ctx.world().getRandom(),
                ctx.sourceLoc(),
                ctx.warnReporter()
            );
            RuntimeState.commandRunner().run(command, execCtx);
            return;
        }

        ServerCommandSource source = ctx.world().getServer().getCommandSource()
            .withWorld(ctx.world())
            .withPosition(Vec3d.ofCenter(ctx.pos()))
            .withRotation(Vec2f.ZERO)
            .withLevel(2)
            .withSilent();

        // Execute with the player's source if available, for permission context
        if (ctx.player() != null) {
            source = ctx.player().getCommandSource()
                .withWorld(ctx.world())
                .withPosition(Vec3d.ofCenter(ctx.pos()))
                .withLevel(2)
                .withSilent();
        }

        try {
            ctx.world().getServer().getCommandManager().execute(source, command);
        } catch (Exception e) {
            ctx.warnReporter().warn("LuckyCommandDrop", "error executing command '" + preview(command) + "': " + e.getMessage(), ctx.sourceLoc());
        }
    }

    private static boolean isTeleportCommand(String command) {
        if (command == null) {
            return false;
        }
        String trimmed = command.stripLeading();
        if (trimmed.isEmpty()) {
            return false;
        }
        int end = 0;
        while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end))) {
            end++;
        }
        String verb = trimmed.substring(0, end);
        return "tp".equalsIgnoreCase(verb) || "teleport".equalsIgnoreCase(verb);
    }

    private static String preview(String s) {
        if (s == null) return "(null)";
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }
}
