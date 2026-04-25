package ie.orangep.reLootplusplus.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import ie.orangep.reLootplusplus.lucky.block.NativeLuckyBlockEntity;
import ie.orangep.reLootplusplus.lucky.block.NativeLuckyBlockItem;
import ie.orangep.reLootplusplus.lucky.loader.LuckyAddonData;
import ie.orangep.reLootplusplus.lucky.registry.AddonLuckyRegistrar;
import ie.orangep.reLootplusplus.lucky.registry.LuckyRegistrar;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.Collection;
import java.util.Locale;

/**
 * Gives a Lucky Block item (base or addon) with a specific luck value to a player.
 *
 * <p>Usage:
 * <pre>
 *   /givelucky                                    — self, luck 0, 1 block
 *   /givelucky &lt;luck&gt;                             — self, luck N, 1 block
 *   /givelucky &lt;luck&gt; &lt;count&gt;                     — self, luck N, M blocks
 *   /givelucky &lt;luck&gt; &lt;count&gt; &lt;target&gt;           — target, luck N, M blocks
 *
 *   /givelucky addon &lt;blockId&gt;                    — self, addon block, luck 0, 1 block
 *   /givelucky addon &lt;blockId&gt; &lt;luck&gt;             — self, addon block, luck N, 1 block
 *   /givelucky addon &lt;blockId&gt; &lt;luck&gt; &lt;count&gt;     — self, addon block, luck N, M blocks
 *   /givelucky addon &lt;blockId&gt; &lt;luck&gt; &lt;count&gt; &lt;target&gt; — target, addon block
 * </pre>
 *
 * <p>Luck is clamped to [-100, 100]. Count is 1–64.
 * Requires operator permission level 2.
 */
public final class GiveLuckyCommand {

    private GiveLuckyCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var luckArg   = CommandManager.argument("luck",  IntegerArgumentType.integer(-100, 100));
        var countArg  = CommandManager.argument("count", IntegerArgumentType.integer(1, 64));
        var targetArg = CommandManager.argument("target", EntityArgumentType.players());

        dispatcher.register(CommandManager.literal("givelucky")
            .requires(src -> src.hasPermissionLevel(2))
            // /givelucky
            .executes(ctx -> give(ctx.getSource(), selfList(ctx.getSource()),
                LuckyRegistrar.LUCKY_BLOCK_ITEM, 0, 1))
            // /givelucky <luck>
            .then(CommandManager.argument("luck", IntegerArgumentType.integer(-100, 100))
                .executes(ctx -> give(ctx.getSource(), selfList(ctx.getSource()),
                    LuckyRegistrar.LUCKY_BLOCK_ITEM,
                    IntegerArgumentType.getInteger(ctx, "luck"), 1))
                // /givelucky <luck> <count>
                .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 64))
                    .executes(ctx -> give(ctx.getSource(), selfList(ctx.getSource()),
                        LuckyRegistrar.LUCKY_BLOCK_ITEM,
                        IntegerArgumentType.getInteger(ctx, "luck"),
                        IntegerArgumentType.getInteger(ctx, "count")))
                    // /givelucky <luck> <count> <target>
                    .then(CommandManager.argument("target", EntityArgumentType.players())
                        .executes(ctx -> give(ctx.getSource(),
                            EntityArgumentType.getPlayers(ctx, "target"),
                            LuckyRegistrar.LUCKY_BLOCK_ITEM,
                            IntegerArgumentType.getInteger(ctx, "luck"),
                            IntegerArgumentType.getInteger(ctx, "count"))))))
            // /givelucky addon <blockId> [<luck> [<count> [<target>]]]
            .then(CommandManager.literal("addon")
                .then(CommandManager.argument("blockId", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        AddonLuckyRegistrar.getBlockIdToAddonMap().keySet()
                            .forEach(builder::suggest);
                        return builder.buildFuture();
                    })
                    // /givelucky addon <blockId>
                    .executes(ctx -> giveAddon(ctx.getSource(), selfList(ctx.getSource()),
                        StringArgumentType.getString(ctx, "blockId"), 0, 1))
                    // /givelucky addon <blockId> <luck>
                    .then(CommandManager.argument("luck", IntegerArgumentType.integer(-100, 100))
                        .executes(ctx -> giveAddon(ctx.getSource(), selfList(ctx.getSource()),
                            StringArgumentType.getString(ctx, "blockId"),
                            IntegerArgumentType.getInteger(ctx, "luck"), 1))
                        // /givelucky addon <blockId> <luck> <count>
                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 64))
                            .executes(ctx -> giveAddon(ctx.getSource(), selfList(ctx.getSource()),
                                StringArgumentType.getString(ctx, "blockId"),
                                IntegerArgumentType.getInteger(ctx, "luck"),
                                IntegerArgumentType.getInteger(ctx, "count")))
                            // /givelucky addon <blockId> <luck> <count> <target>
                            .then(CommandManager.argument("target", EntityArgumentType.players())
                                .executes(ctx -> giveAddon(ctx.getSource(),
                                    EntityArgumentType.getPlayers(ctx, "target"),
                                    StringArgumentType.getString(ctx, "blockId"),
                                    IntegerArgumentType.getInteger(ctx, "luck"),
                                    IntegerArgumentType.getInteger(ctx, "count"))))))))
        );
    }

    // -----------------------------------------------------------------------
    // Execution helpers
    // -----------------------------------------------------------------------

    private static int give(ServerCommandSource source,
                             Collection<ServerPlayerEntity> targets,
                             Item item, int luck, int count) {
        if (targets.isEmpty()) {
            source.sendError(new LiteralText("givelucky: no targets matched"));
            return 0;
        }
        int given = 0;
        for (ServerPlayerEntity player : targets) {
            ItemStack stack = new ItemStack(item, count);
            NativeLuckyBlockEntity.setLuckOnItemStack(stack, luck);
            if (!player.getInventory().insertStack(stack)) {
                player.dropItem(stack, false, false);
            }
            given++;
        }
        String luckLabel = luck >= 0 ? "+" + luck : String.valueOf(luck);
        String itemLabel = Registry.ITEM.getId(item).getPath(); // just the path, cleaner in chat
        if (targets.size() == 1) {
            source.sendFeedback(new LiteralText(
                "givelucky: gave " + count + "× " + itemLabel
                + " (luck " + luckLabel + ") to " + targets.iterator().next().getName().getString()),
                false);
        } else {
            source.sendFeedback(new LiteralText(
                "givelucky: gave " + count + "× " + itemLabel
                + " (luck " + luckLabel + ") to " + given + " players"), false);
        }
        return given;
    }

    private static int giveAddon(ServerCommandSource source,
                                  Collection<ServerPlayerEntity> targets,
                                  String blockId, int luck, int count) {
        Item item = resolveAddonItem(blockId);
        if (item == null) {
            source.sendError(new LiteralText(
                "givelucky: unknown block ID '" + blockId + "' (tab-complete for options)"));
            return 0;
        }
        return give(source, targets, item, luck, count);
    }

    /** Resolves an addon Lucky Block item by normalized block ID. */
    private static Item resolveAddonItem(String query) {
        String normalizedQuery = query.toLowerCase(Locale.ROOT).replace('.', '_');
        LuckyAddonData data = AddonLuckyRegistrar.getBlockIdToAddonMap().get(normalizedQuery);
        return data != null ? lookupItem(data) : null;
    }

    /** Looks up the registered Item for the block declared by this addon's plugin.init. */
    private static Item lookupItem(LuckyAddonData data) {
        if (data.pluginInit() == null) return null;
        String rawBlockId = data.pluginInit().blockId();
        if (rawBlockId == null || rawBlockId.isBlank()) return null;
        String blockId = rawBlockId.toLowerCase(Locale.ROOT).replace('.', '_');
        Identifier id = new Identifier("lucky", blockId);
        if (!Registry.ITEM.containsId(id)) return null;
        Item item = Registry.ITEM.get(id);
        return item instanceof NativeLuckyBlockItem ? item : null;
    }

    private static Collection<ServerPlayerEntity> selfList(ServerCommandSource source) {
        try {
            return java.util.List.of(source.getPlayer());
        } catch (CommandSyntaxException e) {
            return java.util.List.of();
        }
    }
}

