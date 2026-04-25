package ie.orangep.reLootplusplus.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/**
 * In-game NBT editor.
 *
 * <p>Subcommands:
 * <pre>
 *   /editnbt item merge &lt;snbt&gt;
 *       Merges the given SNBT compound into the held item's tag.
 *
 *   /editnbt item set &lt;key&gt; &lt;int_value&gt;
 *       Sets a single integer key on the held item's tag.
 *
 *   /editnbt item clear
 *       Removes all custom NBT from the held item.
 *
 *   /editnbt block merge &lt;snbt&gt;
 *       Merges the given SNBT compound into the targeted block entity.
 *
 *   /editnbt block set &lt;key&gt; &lt;int_value&gt;
 *       Sets a single integer key on the targeted block entity's NBT.
 * </pre>
 *
 * <p>Requires operator permission level 2.
 */
public final class EditNbtCommand {

    private EditNbtCommand() {}

    public static void register(DumpNbtCommand.CommandDispatcherWrapper dispatcher) {
        dispatcher.register(CommandManager.literal("editnbt")
            .requires(source -> source.hasPermissionLevel(2))
            // --- item subcommand ---
            .then(CommandManager.literal("item")
                .then(CommandManager.literal("merge")
                    .then(CommandManager.argument("snbt", StringArgumentType.greedyString())
                        .executes(ctx -> editItemMerge(ctx,
                            StringArgumentType.getString(ctx, "snbt")))))
                .then(CommandManager.literal("set")
                    .then(CommandManager.argument("key", StringArgumentType.word())
                        .then(CommandManager.argument("value", IntegerArgumentType.integer())
                            .executes(ctx -> editItemSet(ctx,
                                StringArgumentType.getString(ctx, "key"),
                                IntegerArgumentType.getInteger(ctx, "value"))))))
                .then(CommandManager.literal("clear")
                    .executes(EditNbtCommand::editItemClear)))
            // --- block subcommand ---
            .then(CommandManager.literal("block")
                .then(CommandManager.literal("merge")
                    .then(CommandManager.argument("snbt", StringArgumentType.greedyString())
                        .executes(ctx -> editBlockMerge(ctx,
                            StringArgumentType.getString(ctx, "snbt")))))
                .then(CommandManager.literal("set")
                    .then(CommandManager.argument("key", StringArgumentType.word())
                        .then(CommandManager.argument("value", IntegerArgumentType.integer())
                            .executes(ctx -> editBlockSet(ctx,
                                StringArgumentType.getString(ctx, "key"),
                                IntegerArgumentType.getInteger(ctx, "value")))))))
        );
    }

    // -----------------------------------------------------------------------
    // item subcommands
    // -----------------------------------------------------------------------

    private static int editItemMerge(CommandContext<ServerCommandSource> ctx, String snbt) {
        ServerPlayerEntity player = getPlayer(ctx.getSource());
        if (player == null) return 0;
        var stack = player.getMainHandStack();
        if (stack == null || stack.isEmpty()) {
            ctx.getSource().sendError(new LiteralText("editnbt: not holding any item"));
            return 0;
        }
        NbtCompound parsed = parseSnbt(ctx.getSource(), snbt);
        if (parsed == null) return 0;

        NbtCompound tag = stack.getOrCreateNbt();
        tag.copyFrom(parsed);
        ctx.getSource().sendFeedback(
            new LiteralText("editnbt: merged into held item → " + summarise(tag)), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int editItemSet(CommandContext<ServerCommandSource> ctx, String key, int value) {
        ServerPlayerEntity player = getPlayer(ctx.getSource());
        if (player == null) return 0;
        var stack = player.getMainHandStack();
        if (stack == null || stack.isEmpty()) {
            ctx.getSource().sendError(new LiteralText("editnbt: not holding any item"));
            return 0;
        }
        stack.getOrCreateNbt().putInt(key, value);
        ctx.getSource().sendFeedback(
            new LiteralText("editnbt: set " + key + "=" + value + " on held item"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int editItemClear(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = getPlayer(ctx.getSource());
        if (player == null) return 0;
        var stack = player.getMainHandStack();
        if (stack == null || stack.isEmpty()) {
            ctx.getSource().sendError(new LiteralText("editnbt: not holding any item"));
            return 0;
        }
        // Re-create item without custom NBT (preserve only the stack count)
        int count = stack.getCount();
        stack.setNbt(null);
        stack.setCount(count);
        ctx.getSource().sendFeedback(new LiteralText("editnbt: cleared NBT on held item"), false);
        return Command.SINGLE_SUCCESS;
    }

    // -----------------------------------------------------------------------
    // block subcommands
    // -----------------------------------------------------------------------

    private static int editBlockMerge(CommandContext<ServerCommandSource> ctx, String snbt) {
        ServerPlayerEntity player = getPlayer(ctx.getSource());
        if (player == null) return 0;
        BlockEntity be = getTargetedBlockEntity(player);
        if (be == null) {
            ctx.getSource().sendError(new LiteralText("editnbt: not looking at a block entity"));
            return 0;
        }
        NbtCompound parsed = parseSnbt(ctx.getSource(), snbt);
        if (parsed == null) return 0;

        NbtCompound existing = be.createNbt();
        existing.copyFrom(parsed);
        be.readNbt(existing);
        be.markDirty();
        ctx.getSource().sendFeedback(
            new LiteralText("editnbt: merged into block entity at "
                + be.getPos().toShortString() + " → " + summarise(existing)), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int editBlockSet(CommandContext<ServerCommandSource> ctx, String key, int value) {
        ServerPlayerEntity player = getPlayer(ctx.getSource());
        if (player == null) return 0;
        BlockEntity be = getTargetedBlockEntity(player);
        if (be == null) {
            ctx.getSource().sendError(new LiteralText("editnbt: not looking at a block entity"));
            return 0;
        }
        NbtCompound nbt = be.createNbt();
        nbt.putInt(key, value);
        be.readNbt(nbt);
        be.markDirty();
        ctx.getSource().sendFeedback(
            new LiteralText("editnbt: set " + key + "=" + value
                + " on block entity at " + be.getPos().toShortString()), false);
        return Command.SINGLE_SUCCESS;
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private static ServerPlayerEntity getPlayer(ServerCommandSource source) {
        try {
            return source.getPlayer();
        } catch (Exception e) {
            source.sendError(new LiteralText("editnbt: only players can use this command"));
            return null;
        }
    }

    private static BlockEntity getTargetedBlockEntity(ServerPlayerEntity player) {
        HitResult hit = player.raycast(6.0, 0.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        return player.getWorld().getBlockEntity(pos);
    }

    private static NbtCompound parseSnbt(ServerCommandSource source, String snbt) {
        try {
            return StringNbtReader.parse(snbt);
        } catch (Exception e) {
            source.sendError(new LiteralText("editnbt: invalid SNBT — " + e.getMessage()));
            return null;
        }
    }

    /** Returns a short summary of the compound for feedback messages. */
    private static String summarise(NbtCompound nbt) {
        String text = nbt.toString();
        return text.length() <= 200 ? text : text.substring(0, 200) + "…";
    }
}
