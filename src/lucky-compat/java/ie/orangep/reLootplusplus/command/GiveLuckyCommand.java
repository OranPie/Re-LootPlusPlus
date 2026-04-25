package ie.orangep.reLootplusplus.command;

import com.mojang.brigadier.Command;
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
import java.util.Map;

/**
 * Gives a Lucky Block item (base or addon) with a specific luck value to a player.
 *
 * <p>Usage:
 * <pre>
 *   /givelucky                              — give self 1 Lucky Block (luck 0)
 *   /givelucky &lt;luck&gt;                       — give self Lucky Block with given luck
 *   /givelucky &lt;target&gt; &lt;luck&gt;             — give target player(s) Lucky Block with given luck
 *
 *   /givelucky addon &lt;packId|blockId&gt;       — give self an addon Lucky Block (luck 0)
 *   /givelucky addon &lt;packId|blockId&gt; &lt;luck&gt;         — give self addon block with luck
 *   /givelucky addon &lt;packId|blockId&gt; &lt;luck&gt; &lt;target&gt; — give target addon block with luck
 * </pre>
 *
 * <p>The addon identifier can be either the pack ID (e.g. {@code lucky_astral_lucky_block})
 * or the normalized block ID (e.g. {@code astral_fairy}).
 * Luck is clamped to [-100, 100]. Requires operator permission level 2.
 */
public final class GiveLuckyCommand {

    private GiveLuckyCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("givelucky")
            .requires(source -> source.hasPermissionLevel(2))
            // /givelucky  →  give self, base block, luck 0
            .executes(ctx -> give(ctx.getSource(), selfList(ctx.getSource()),
                LuckyRegistrar.LUCKY_BLOCK_ITEM, 0))
            // /givelucky <luck>
            .then(CommandManager.argument("luck", IntegerArgumentType.integer(-100, 100))
                .executes(ctx -> give(ctx.getSource(), selfList(ctx.getSource()),
                    LuckyRegistrar.LUCKY_BLOCK_ITEM,
                    IntegerArgumentType.getInteger(ctx, "luck"))))
            // /givelucky <target>  →  give target, luck 0
            .then(CommandManager.argument("target", EntityArgumentType.players())
                .executes(ctx -> give(ctx.getSource(),
                    EntityArgumentType.getPlayers(ctx, "target"),
                    LuckyRegistrar.LUCKY_BLOCK_ITEM, 0))
                // /givelucky <target> <luck>
                .then(CommandManager.argument("luck", IntegerArgumentType.integer(-100, 100))
                    .executes(ctx -> give(ctx.getSource(),
                        EntityArgumentType.getPlayers(ctx, "target"),
                        LuckyRegistrar.LUCKY_BLOCK_ITEM,
                        IntegerArgumentType.getInteger(ctx, "luck")))))
            // /givelucky addon <packId|blockId> [<luck>] [<target>]
            .then(CommandManager.literal("addon")
                .then(CommandManager.argument("addonId", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        // Suggest both pack IDs and block IDs for convenience
                        for (Map.Entry<String, LuckyAddonData> e :
                                AddonLuckyRegistrar.getBlockIdToAddonMap().entrySet()) {
                            builder.suggest(e.getValue().packId()); // by pack id
                            builder.suggest(e.getKey());             // by block id
                        }
                        return builder.buildFuture();
                    })
                    // /givelucky addon <id>  → luck 0, give self
                    .executes(ctx -> giveAddon(ctx.getSource(),
                        selfList(ctx.getSource()),
                        StringArgumentType.getString(ctx, "addonId"), 0))
                    // /givelucky addon <id> <luck>
                    .then(CommandManager.argument("luck", IntegerArgumentType.integer(-100, 100))
                        .executes(ctx -> giveAddon(ctx.getSource(),
                            selfList(ctx.getSource()),
                            StringArgumentType.getString(ctx, "addonId"),
                            IntegerArgumentType.getInteger(ctx, "luck")))
                        // /givelucky addon <id> <luck> <target>
                        .then(CommandManager.argument("target", EntityArgumentType.players())
                            .executes(ctx -> giveAddon(ctx.getSource(),
                                EntityArgumentType.getPlayers(ctx, "target"),
                                StringArgumentType.getString(ctx, "addonId"),
                                IntegerArgumentType.getInteger(ctx, "luck")))))))
        );
    }

    // -----------------------------------------------------------------------
    // Execution helpers
    // -----------------------------------------------------------------------

    private static int give(ServerCommandSource source,
                             Collection<ServerPlayerEntity> targets,
                             Item item, int luck) {
        if (targets.isEmpty()) {
            source.sendError(new LiteralText("givelucky: no targets matched"));
            return 0;
        }
        int given = 0;
        for (ServerPlayerEntity player : targets) {
            ItemStack stack = new ItemStack(item, 1);
            NativeLuckyBlockEntity.setLuckOnItemStack(stack, luck);
            if (!player.getInventory().insertStack(stack)) {
                player.dropItem(stack, false, false);
            }
            given++;
        }
        String luckLabel = luck >= 0 ? "+" + luck : String.valueOf(luck);
        String itemLabel = Registry.ITEM.getId(item).toString();
        sendFeedback(source, targets, given, itemLabel, luckLabel);
        return given;
    }

    private static int giveAddon(ServerCommandSource source,
                                  Collection<ServerPlayerEntity> targets,
                                  String addonId, int luck) {
        Item item = resolveAddonItem(addonId);
        if (item == null) {
            source.sendError(new LiteralText(
                "givelucky: unknown addon '" + addonId
                + "' — use pack ID or block ID (tab-complete for options)"));
            return 0;
        }
        return give(source, targets, item, luck);
    }

    /**
     * Resolves an addon Lucky Block item by pack ID or normalized block ID.
     * Returns {@code null} if no match is found.
     */
    private static Item resolveAddonItem(String query) {
        Map<String, LuckyAddonData> byBlockId = AddonLuckyRegistrar.getBlockIdToAddonMap();

        // 1. Try direct block-id match (normalized)
        String normalizedQuery = query.toLowerCase(Locale.ROOT).replace('.', '_');
        LuckyAddonData direct = byBlockId.get(normalizedQuery);
        if (direct != null) {
            return lookupItem(direct);
        }

        // 2. Try matching by pack ID
        for (LuckyAddonData data : byBlockId.values()) {
            if (data.packId().equalsIgnoreCase(query)) {
                return lookupItem(data);
            }
        }

        // 3. Try partial pack-id prefix match (convenience for long pack IDs)
        for (LuckyAddonData data : byBlockId.values()) {
            if (data.packId().toLowerCase(Locale.ROOT).startsWith(normalizedQuery)) {
                return lookupItem(data);
            }
        }

        return null;
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
        // Guard: must actually be a NativeLuckyBlockItem
        return item instanceof NativeLuckyBlockItem ? item : null;
    }

    private static void sendFeedback(ServerCommandSource source,
                                      Collection<ServerPlayerEntity> targets,
                                      int given, String itemLabel, String luckLabel) {
        if (targets.size() == 1) {
            ServerPlayerEntity only = targets.iterator().next();
            source.sendFeedback(new LiteralText(
                "givelucky: gave " + itemLabel + " (luck " + luckLabel
                + ") to " + only.getName().getString()), false);
        } else {
            source.sendFeedback(new LiteralText(
                "givelucky: gave " + itemLabel + " (luck " + luckLabel
                + ") to " + given + " players"), false);
        }
    }

    private static Collection<ServerPlayerEntity> selfList(ServerCommandSource source) {
        try {
            return java.util.List.of(source.getPlayer());
        } catch (CommandSyntaxException e) {
            return java.util.List.of();
        }
    }
}

