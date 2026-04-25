package ie.orangep.reLootplusplus.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import ie.orangep.reLootplusplus.lucky.block.NativeLuckyBlockEntity;
import ie.orangep.reLootplusplus.lucky.registry.LuckyRegistrar;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;

import java.util.Collection;

/**
 * Gives a Lucky Block item with a specific luck value to a player.
 *
 * <p>Usage:
 * <pre>
 *   /givelucky                        — give self 1 Lucky Block (luck 0)
 *   /givelucky &lt;luck&gt;                 — give self 1 Lucky Block with given luck
 *   /givelucky &lt;target&gt; &lt;luck&gt;       — give target player(s) 1 Lucky Block with given luck
 * </pre>
 *
 * <p>Luck is clamped to [-100, 100].
 * Requires operator permission level 2.
 */
public final class GiveLuckyCommand {

    private GiveLuckyCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("givelucky")
            .requires(source -> source.hasPermissionLevel(2))
            // /givelucky  →  give self, luck 0
            .executes(ctx -> give(ctx.getSource(), selfList(ctx.getSource()), 0))
            // /givelucky <luck>  →  give self, specified luck
            .then(CommandManager.argument("luck", IntegerArgumentType.integer(-100, 100))
                .executes(ctx -> give(
                    ctx.getSource(),
                    selfList(ctx.getSource()),
                    IntegerArgumentType.getInteger(ctx, "luck")))
                // /givelucky <luck> <target>  is unnatural — use target-first variant below
            )
            // /givelucky <target>  →  give target, luck 0
            .then(CommandManager.argument("target", EntityArgumentType.players())
                .executes(ctx -> give(
                    ctx.getSource(),
                    EntityArgumentType.getPlayers(ctx, "target"),
                    0))
                // /givelucky <target> <luck>
                .then(CommandManager.argument("luck", IntegerArgumentType.integer(-100, 100))
                    .executes(ctx -> give(
                        ctx.getSource(),
                        EntityArgumentType.getPlayers(ctx, "target"),
                        IntegerArgumentType.getInteger(ctx, "luck")))))
        );
    }

    private static int give(ServerCommandSource source,
                             Collection<ServerPlayerEntity> targets,
                             int luck) {
        if (targets.isEmpty()) {
            source.sendError(new LiteralText("givelucky: no targets matched"));
            return 0;
        }
        int given = 0;
        for (ServerPlayerEntity player : targets) {
            ItemStack stack = new ItemStack(LuckyRegistrar.LUCKY_BLOCK_ITEM, 1);
            NativeLuckyBlockEntity.setLuckOnItemStack(stack, luck);
            boolean inserted = player.getInventory().insertStack(stack);
            if (!inserted) {
                // Drop at feet if inventory is full
                player.dropItem(stack, false, false);
            }
            given++;
        }
        String luckLabel = luck >= 0 ? "+" + luck : String.valueOf(luck);
        if (targets.size() == 1) {
            ServerPlayerEntity only = targets.iterator().next();
            source.sendFeedback(new LiteralText(
                "givelucky: gave Lucky Block (luck " + luckLabel + ") to " + only.getName().getString()), false);
        } else {
            source.sendFeedback(new LiteralText(
                "givelucky: gave Lucky Block (luck " + luckLabel + ") to " + given + " players"), false);
        }
        return given;
    }

    private static Collection<ServerPlayerEntity> selfList(ServerCommandSource source) {
        try {
            return java.util.List.of(source.getPlayer());
        } catch (CommandSyntaxException e) {
            return java.util.List.of();
        }
    }
}
