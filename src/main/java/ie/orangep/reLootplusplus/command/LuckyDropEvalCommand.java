package ie.orangep.reLootplusplus.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import ie.orangep.reLootplusplus.command.exec.ExecContext;
import ie.orangep.reLootplusplus.config.model.drop.DropEntry;
import ie.orangep.reLootplusplus.config.model.drop.DropEntryCommand;
import ie.orangep.reLootplusplus.config.model.drop.DropEntryEntity;
import ie.orangep.reLootplusplus.config.model.drop.DropEntryItem;
import ie.orangep.reLootplusplus.config.model.drop.DropGroup;
import ie.orangep.reLootplusplus.config.model.drop.DropRoller;
import ie.orangep.reLootplusplus.config.model.rule.BlockDropRule;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import ie.orangep.reLootplusplus.legacy.nbt.LenientNbtParser;
import ie.orangep.reLootplusplus.runtime.BlockDropRegistry;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import ie.orangep.reLootplusplus.lucky.block.NativeLuckyBlockEntity;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropEngine;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.loader.LuckyAddonLoader;
import ie.orangep.reLootplusplus.lucky.registry.LuckyRegistrar;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class LuckyDropEvalCommand {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private LuckyDropEvalCommand() {
    }

    public static void register(DumpNbtCommand.CommandDispatcherWrapper dispatcher) {
        dispatcher.register(CommandManager.literal("lppdrop")
            .requires(source -> source.hasPermissionLevel(2))
            .then(CommandManager.literal("eval")
                .executes(ctx -> run(ctx, false, null))
                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                    .executes(ctx -> run(ctx, false, BlockPosArgumentType.getBlockPos(ctx, "pos")))))
            .then(CommandManager.literal("eval_dry")
                .executes(ctx -> run(ctx, true, null))
                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                    .executes(ctx -> run(ctx, true, BlockPosArgumentType.getBlockPos(ctx, "pos")))))
            .then(CommandManager.literal("eval_counts")
                .then(CommandManager.argument("times", IntegerArgumentType.integer(1, 2000))
                    .executes(ctx -> runCounts(
                        ctx,
                        IntegerArgumentType.getInteger(ctx, "times"),
                        null
                    ))
                    .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                        .executes(ctx -> runCounts(
                            ctx,
                            IntegerArgumentType.getInteger(ctx, "times"),
                            BlockPosArgumentType.getBlockPos(ctx, "pos")
                        )))))
            .then(CommandManager.literal("lucky_eval")
                .executes(ctx -> runLuckyEval(ctx, null))
                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                    .executes(ctx -> runLuckyEval(ctx, BlockPosArgumentType.getBlockPos(ctx, "pos")))))
            .then(CommandManager.literal("lucky_eval_bulk")
                .then(CommandManager.argument("times", IntegerArgumentType.integer(1, 500))
                    .executes(ctx -> runLuckyEvalBulk(ctx, IntegerArgumentType.getInteger(ctx, "times"), null, true))
                    .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                        .executes(ctx -> runLuckyEvalBulk(
                            ctx,
                            IntegerArgumentType.getInteger(ctx, "times"),
                            BlockPosArgumentType.getBlockPos(ctx, "pos"),
                            true
                        )))))
            .then(CommandManager.literal("lucky_eval_bulk_dry")
                .then(CommandManager.argument("times", IntegerArgumentType.integer(1, 5000))
                    .executes(ctx -> runLuckyEvalBulk(ctx, IntegerArgumentType.getInteger(ctx, "times"), null, false))
                    .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                        .executes(ctx -> runLuckyEvalBulk(
                            ctx,
                            IntegerArgumentType.getInteger(ctx, "times"),
                            BlockPosArgumentType.getBlockPos(ctx, "pos"),
                            false
                        )))))
            // ---- Pack / drop inspection commands ----
            .then(CommandManager.literal("list_packs")
                .executes(LuckyDropEvalCommand::runListPacks))
            .then(CommandManager.literal("list_drops")
                .then(CommandManager.argument("packId", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(ctx -> runListDrops(ctx,
                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "packId"),
                        1, 20))))
            .then(CommandManager.literal("lucky_sim_counts")
                .then(CommandManager.argument("times", IntegerArgumentType.integer(1, 10000))
                    .executes(ctx -> runLuckySimCounts(ctx, IntegerArgumentType.getInteger(ctx, "times"), null))
                    .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                        .executes(ctx -> runLuckySimCounts(
                            ctx,
                            IntegerArgumentType.getInteger(ctx, "times"),
                            BlockPosArgumentType.getBlockPos(ctx, "pos"))))))
        );
    }

    private static int run(CommandContext<ServerCommandSource> ctx, boolean dryRun, BlockPos providedPos) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos pos = resolvePos(source, providedPos);
        if (pos == null) {
            source.sendError(new LiteralText("lppdrop: no target block; look at a block or pass /lppdrop eval <x y z>"));
            return 0;
        }

        BlockDropRegistry registry = RuntimeState.blockDropRegistry();
        if (registry == null) {
            source.sendError(new LiteralText("lppdrop: block drop registry is not initialized"));
            return 0;
        }

        String blockId = Registry.BLOCK.getId(world.getBlockState(pos).getBlock()).toString();
        List<BlockDropRule> matches = registry.addRules().stream()
            .filter(rule -> blockId.equals(rule.blockId()))
            .toList();
        if (matches.isEmpty()) {
            source.sendError(new LiteralText("lppdrop: no block_drop adding rules matched block " + blockId));
            return 0;
        }

        EvalStats stats = new EvalStats();
        stats.matchedRules = matches.size();
        LegacyWarnReporter warnReporter = RuntimeState.warnReporter();
        LegacyCommandRunner commandRunner = RuntimeState.commandRunner();

        for (BlockDropRule rule : matches) {
            stats.totalRuleGroups += rule.groups().size();
            if (rule.blockMeta() != 32767) stats.nonDefaultHeaderFields++;
            if (rule.rarity() != 1.0f) stats.nonDefaultHeaderFields++;
            if (rule.onlyPlayerMined()) stats.nonDefaultHeaderFields++;
            if (rule.dropWithSilk()) stats.nonDefaultHeaderFields++;
            if (rule.affectedByFortune()) stats.nonDefaultHeaderFields++;

            DropGroup group = DropRoller.rollGroup(rule.groups(), world.getRandom());
            if (group == null) {
                stats.nullGroups++;
                continue;
            }
            stats.rolledGroups++;
            for (DropEntry entry : group.entries()) {
                executeEntry(entry, world, pos, rule.sourceLoc(), dryRun, warnReporter, commandRunner, stats);
            }
        }

        source.sendFeedback(new LiteralText(
            "lppdrop: mode=" + (dryRun ? "dry" : "run")
                + " block=" + blockId
                + " pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + " matchedRules=" + stats.matchedRules
                + " groups=" + stats.rolledGroups + "/" + stats.totalRuleGroups
        ), false);
        source.sendFeedback(new LiteralText(
            "lppdrop: entries total=" + stats.totalEntries
                + " items=" + stats.itemEntries
                + " entities=" + stats.entityEntries
                + " commands=" + stats.commandEntries
                + " invalidItems=" + stats.invalidItems
                + " invalidEntities=" + stats.invalidEntities
        ), false);
        if (stats.nonDefaultHeaderFields > 0) {
            source.sendFeedback(new LiteralText(
                "lppdrop: note " + stats.nonDefaultHeaderFields
                    + " legacy header fields detected (rarity/meta/playerMined/silk/fortune); runtime currently ignores them"
            ), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int runCounts(CommandContext<ServerCommandSource> ctx, int times, BlockPos providedPos) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos pos = resolvePos(source, providedPos);
        if (pos == null) {
            source.sendError(new LiteralText("lppdrop: no target block; look at a block or pass /lppdrop eval_counts <times> <x y z>"));
            return 0;
        }

        BlockDropRegistry registry = RuntimeState.blockDropRegistry();
        if (registry == null) {
            source.sendError(new LiteralText("lppdrop: block drop registry is not initialized"));
            return 0;
        }

        String blockId = Registry.BLOCK.getId(world.getBlockState(pos).getBlock()).toString();
        List<BlockDropRule> matches = registry.addRules().stream()
            .filter(rule -> blockId.equals(rule.blockId()))
            .toList();
        if (matches.isEmpty()) {
            source.sendError(new LiteralText("lppdrop: no block_drop adding rules matched block " + blockId));
            return 0;
        }

        EvalStats total = new EvalStats();
        total.matchedRules = matches.size();
        total.totalRuleGroups = matches.stream().mapToInt(rule -> rule.groups().size()).sum();
        for (BlockDropRule rule : matches) {
            if (rule.blockMeta() != 32767) total.nonDefaultHeaderFields++;
            if (rule.rarity() != 1.0f) total.nonDefaultHeaderFields++;
            if (rule.onlyPlayerMined()) total.nonDefaultHeaderFields++;
            if (rule.dropWithSilk()) total.nonDefaultHeaderFields++;
            if (rule.affectedByFortune()) total.nonDefaultHeaderFields++;
        }

        int zeroRollIterations = 0;
        int invalidIterations = 0;
        for (int i = 0; i < times; i++) {
            EvalStats iter = evalOnceDry(world, pos, matches);
            total.rolledGroups += iter.rolledGroups;
            total.nullGroups += iter.nullGroups;
            total.totalEntries += iter.totalEntries;
            total.itemEntries += iter.itemEntries;
            total.entityEntries += iter.entityEntries;
            total.commandEntries += iter.commandEntries;
            total.invalidItems += iter.invalidItems;
            total.invalidEntities += iter.invalidEntities;
            if (iter.totalEntries == 0) {
                zeroRollIterations++;
            }
            if (iter.invalidItems > 0 || iter.invalidEntities > 0) {
                invalidIterations++;
            }
        }

        source.sendFeedback(new LiteralText(
            "lppdrop: mode=counts times=" + times
                + " block=" + blockId
                + " pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + " matchedRules=" + total.matchedRules
        ), false);
        source.sendFeedback(new LiteralText(
            "lppdrop: iterations zeroEntries=" + zeroRollIterations
                + " invalidEntryIters=" + invalidIterations
                + " rolledGroups=" + total.rolledGroups
                + " nullGroups=" + total.nullGroups
        ), false);
        source.sendFeedback(new LiteralText(
            "lppdrop: entries total=" + total.totalEntries
                + " items=" + total.itemEntries
                + " entities=" + total.entityEntries
                + " commands=" + total.commandEntries
                + " invalidItems=" + total.invalidItems
                + " invalidEntities=" + total.invalidEntities
        ), false);
        if (total.nonDefaultHeaderFields > 0) {
            source.sendFeedback(new LiteralText(
                "lppdrop: note " + total.nonDefaultHeaderFields
                    + " legacy header fields detected (rarity/meta/playerMined/silk/fortune); runtime currently ignores them"
            ), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static EvalStats evalOnceDry(ServerWorld world, BlockPos pos, List<BlockDropRule> matches) {
        EvalStats iter = new EvalStats();
        LegacyWarnReporter warnReporter = RuntimeState.warnReporter();
        LegacyCommandRunner commandRunner = RuntimeState.commandRunner();
        for (BlockDropRule rule : matches) {
            DropGroup group = DropRoller.rollGroup(rule.groups(), world.getRandom());
            if (group == null) {
                iter.nullGroups++;
                continue;
            }
            iter.rolledGroups++;
            for (DropEntry entry : group.entries()) {
                executeEntry(entry, world, pos, rule.sourceLoc(), true, warnReporter, commandRunner, iter);
            }
        }
        return iter;
    }

    private static int runLuckyEval(CommandContext<ServerCommandSource> ctx, BlockPos providedPos) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos pos = resolvePos(source, providedPos);
        if (pos == null) {
            source.sendError(new LiteralText("lppdrop: no target block; look at a block or pass /lppdrop lucky_eval <x y z>"));
            return 0;
        }

        var state = world.getBlockState(pos);
        var block = state.getBlock();
        String blockId = Registry.BLOCK.getId(block).toString();
        if (!blockId.startsWith("lucky:")) {
            source.sendError(new LiteralText("lppdrop: block is not a lucky: block: " + blockId));
            return 0;
        }

        NativeLuckyBlockData data = resolveLuckyBlockData(world, pos);
        int luck = data.luck();
        List<String> customDrops = data.customDrops();

        ServerPlayerEntity player = null;
        try { player = source.getPlayer(); } catch (Exception ignored) {}

        SourceLoc loc = new SourceLoc("lucky", "lucky_eval_cmd", pos.toShortString(), 0, "");
        LegacyWarnReporter warnReporter = RuntimeState.warnReporter();
        LuckyDropContext dropCtx = new LuckyDropContext(world, pos, player, luck, warnReporter, loc);

        LuckyDropLine selected;
        try {
            if (customDrops != null && !customDrops.isEmpty()) {
                selected = LuckyDropEngine.evaluateRaw(dropCtx, customDrops);
            } else {
                selected = LuckyDropEngine.evaluate(dropCtx, LuckyAddonLoader.getMergedDrops());
            }
        } catch (Exception e) {
            source.sendError(new LiteralText("lppdrop: lucky_eval failed: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            appendBulkLog("single", 1, 0, 1, 0L, blockId, pos, luck,
                customDrops == null ? 0 : customDrops.size(),
                e.getClass().getSimpleName() + ": " + e.getMessage());
            return 0;
        }

        int customDropCount = customDrops == null ? 0 : customDrops.size();
        source.sendFeedback(new LiteralText(
            "lppdrop: lucky_eval executed for " + blockId
                + " at " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + " luck=" + luck
                + " customDrops=" + customDropCount
                + (selected != null ? " selected=" + selected.type() + ":" + selected.rawId() : " selected=(none)")
        ), false);
        appendBulkLog("single", 1, selected != null ? 1 : 0, selected != null ? 0 : 1, 0L, blockId, pos, luck, customDropCount, null);
        return Command.SINGLE_SUCCESS;
    }

    private static int runLuckyEvalBulk(CommandContext<ServerCommandSource> ctx, int times, BlockPos providedPos, boolean apply) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos pos = resolvePos(source, providedPos);
        if (pos == null) {
            source.sendError(new LiteralText("lppdrop: no target block; look at a block or pass /lppdrop lucky_eval_bulk <times> <x y z>"));
            return 0;
        }

        var state = world.getBlockState(pos);
        var block = state.getBlock();
        String blockId = Registry.BLOCK.getId(block).toString();
        if (!blockId.startsWith("lucky:")) {
            source.sendError(new LiteralText("lppdrop: block is not a lucky: block: " + blockId));
            return 0;
        }

        NativeLuckyBlockData baseData = resolveLuckyBlockData(world, pos);
        int luck = baseData.luck();
        List<String> customDrops = baseData.customDrops();
        int customDropCount = customDrops == null ? 0 : customDrops.size();
        // Resolve pre-parsed drops for bulk eval (custom drops still parsed at eval time)
        List<LuckyDropLine> parsedDrops = (customDrops == null || customDrops.isEmpty())
            ? LuckyAddonLoader.getMergedDrops() : null;

        if (!apply) {
            source.sendFeedback(new LiteralText(
                "lppdrop: lucky_eval_bulk_dry times=" + times
                    + " block=" + blockId
                    + " pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                    + " luck=" + luck
                    + " customDrops=" + customDropCount
                    + " (no side effects)"
            ), false);
            appendBulkLog("bulk_dry", times, times, 0, 0L, blockId, pos, luck, customDropCount, null);
            return Command.SINGLE_SUCCESS;
        }

        ServerPlayerEntity player = null;
        try { player = source.getPlayer(); } catch (Exception ignored) {}

        LegacyWarnReporter warnReporter = RuntimeState.warnReporter();
        SourceLoc loc = new SourceLoc("lucky", "lucky_eval_bulk_cmd", pos.toShortString(), 0, "");

        int success = 0;
        int failed = 0;
        String firstError = null;
        long startNs = System.nanoTime();
        for (int i = 0; i < times; i++) {
            try {
                LuckyDropContext dropCtx = new LuckyDropContext(world, pos, player, luck, warnReporter, loc);
                LuckyDropLine selected;
                if (parsedDrops != null) {
                    selected = LuckyDropEngine.evaluate(dropCtx, parsedDrops);
                } else {
                    selected = LuckyDropEngine.evaluateRaw(dropCtx, customDrops);
                }
                success++;
            } catch (Exception e) {
                failed++;
                if (firstError == null) {
                    firstError = e.getClass().getSimpleName() + ": " + e.getMessage();
                }
            }
        }
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        source.sendFeedback(new LiteralText(
            "lppdrop: lucky_eval_bulk times=" + times
                + " success=" + success
                + " failed=" + failed
                + " elapsedMs=" + elapsedMs
                + " block=" + blockId
                + " pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
        ), false);
        source.sendFeedback(new LiteralText("lppdrop: warning bulk apply causes real world side effects"), false);
        if (firstError != null) {
            source.sendError(new LiteralText("lppdrop: first error: " + firstError));
        }
        appendBulkLog("bulk", times, success, failed, elapsedMs, blockId, pos, luck, customDropCount, firstError);
        return success > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private record NativeLuckyBlockData(int luck, List<String> customDrops) {}

    private static NativeLuckyBlockData resolveLuckyBlockData(ServerWorld world, BlockPos pos) {
        var be = world.getBlockEntity(pos);
        if (be instanceof NativeLuckyBlockEntity luckyBe) {
            return new NativeLuckyBlockData(luckyBe.getLuck(), luckyBe.getCustomDrops());
        }
        return new NativeLuckyBlockData(0, null);
    }

    private static void appendBulkLog(
        String mode,
        int times,
        int success,
        int failed,
        long elapsedMs,
        String blockId,
        BlockPos pos,
        int luck,
        int customDrops,
        String firstError
    ) {
        String ts = TS.format(LocalDateTime.now());
        String line = ts
            + " mode=" + mode
            + " times=" + times
            + " success=" + success
            + " failed=" + failed
            + " elapsedMs=" + elapsedMs
            + " block=" + blockId
            + " pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
            + " luck=" + luck
            + " customDrops=" + customDrops
            + (firstError == null ? "" : " firstError=\"" + firstError.replace('"', '\'') + "\"");
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            Path log = gameDir.resolve("logs").resolve("re_lpp").resolve("lucky_eval_bulk.log");
            Files.createDirectories(log.getParent());
            Files.writeString(log, line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // Logging failures must not fail command execution.
        }
    }

    private static void executeEntry(
        DropEntry entry,
        ServerWorld world,
        BlockPos pos,
        SourceLoc loc,
        boolean dryRun,
        LegacyWarnReporter warnReporter,
        LegacyCommandRunner commandRunner,
        EvalStats stats
    ) {
        stats.totalEntries++;
        if (entry instanceof DropEntryItem itemDrop) {
            stats.itemEntries++;
            int count = itemDrop.minCount();
            if (itemDrop.maxCount() > itemDrop.minCount()) {
                count = itemDrop.minCount() + world.getRandom().nextInt(itemDrop.maxCount() - itemDrop.minCount() + 1);
            }
            Identifier id = Identifier.tryParse(itemDrop.itemId());
            if (id == null || !Registry.ITEM.containsId(id)) {
                stats.invalidItems++;
                return;
            }
            ItemStack stack = new ItemStack(Registry.ITEM.get(id), count);
            if (itemDrop.nbtRaw() != null && !itemDrop.nbtRaw().isEmpty()) {
                NbtCompound tag = LenientNbtParser.parseOrEmpty(itemDrop.nbtRaw(), warnReporter, loc, "LegacyNBT");
                if (tag != null) {
                    stack.setNbt(tag);
                }
            }
            if (!dryRun) {
                world.spawnEntity(new net.minecraft.entity.ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
            }
            return;
        }
        if (entry instanceof DropEntryEntity entityDrop) {
            stats.entityEntries++;
            Identifier id = Identifier.tryParse(entityDrop.entityId());
            if (id == null || !Registry.ENTITY_TYPE.containsId(id)) {
                stats.invalidEntities++;
                return;
            }
            NbtCompound tag = LenientNbtParser.parseOrEmpty(entityDrop.nbtRaw(), warnReporter, loc, "LegacyNBT");
            if (!dryRun) {
                EntityType<?> type = Registry.ENTITY_TYPE.get(id);
                var entity = type.create(world);
                if (entity != null) {
                    if (tag == null) {
                        tag = new NbtCompound();
                    }
                    tag.putString("id", entityDrop.entityId());
                    entity.readNbt(tag);
                    entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, entity.getYaw(), entity.getPitch());
                    world.spawnEntity(entity);
                }
            }
            return;
        }
        if (entry instanceof DropEntryCommand cmd) {
            stats.commandEntries++;
            if (!dryRun) {
                ExecContext exec = new ExecContext(world, pos, null, world.getRandom(), loc, warnReporter);
                commandRunner.run(cmd.command(), exec);
            }
        }
    }

    private static BlockPos resolvePos(ServerCommandSource source, BlockPos providedPos) {
        if (providedPos != null) {
            return providedPos;
        }
        try {
            ServerPlayerEntity player = source.getPlayer();
            HitResult hit = player.raycast(6.0, 0.0f, false);
            if (hit.getType() == HitResult.Type.BLOCK) {
                return ((BlockHitResult) hit).getBlockPos();
            }
        } catch (Exception ignored) {
            // Ignore and fall through.
        }
        return null;
    }

    // =========================================================================
    // /lppdrop list_packs — lists all loaded Lucky addon packs with drop counts
    // =========================================================================

    private static int runListPacks(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        var packs = LuckyAddonLoader.getAddonDataList();
        if (packs.isEmpty()) {
            source.sendFeedback(new LiteralText("§7lppdrop: §cNo Lucky addon packs loaded."), false);
            return 0;
        }
        source.sendFeedback(new LiteralText(
            "§6§l─── Lucky Addon Packs (" + packs.size() + ") ───"), false);
        for (var data : packs) {
            String src = data.pack().zipPath() != null
                ? data.pack().zipPath().getFileName().toString()
                : data.packId();
            String drops  = "§adrops:" + data.parsedDrops().size();
            String bow    = data.parsedBowDrops().isEmpty()   ? "" : " §3bow:" + data.parsedBowDrops().size();
            String sword  = data.parsedSwordDrops().isEmpty() ? "" : " §esword:" + data.parsedSwordDrops().size();
            String potion = data.parsedPotionDrops().isEmpty()? "" : " §5potion:" + data.parsedPotionDrops().size();
            source.sendFeedback(new LiteralText(
                "§b" + data.packId() + " §7(" + src + ") " + drops + bow + sword + potion), false);
        }
        // Merged totals
        source.sendFeedback(new LiteralText(
            "§7Merged totals: §adrops=" + LuckyAddonLoader.getMergedDrops().size()
                + " §3bow=" + LuckyAddonLoader.getMergedBowDrops().size()
                + " §esword=" + LuckyAddonLoader.getMergedSwordDrops().size()
                + " §5potion=" + LuckyAddonLoader.getMergedPotionDrops().size()
                + " §8base=" + LuckyAddonLoader.getBaseDrops().size()), false);
        return Command.SINGLE_SUCCESS;
    }

    // =========================================================================
    // /lppdrop list_drops <packId> — shows first N parsed drop entries for a pack
    // =========================================================================

    private static int runListDrops(CommandContext<ServerCommandSource> ctx,
                                    String packId, int page, int pageSize) {
        ServerCommandSource source = ctx.getSource();
        // Accept "merged" as a special packId to show the merged drop pool
        var drops = java.util.Collections.<LuckyDropLine>emptyList();
        if ("merged".equalsIgnoreCase(packId)) {
            drops = LuckyAddonLoader.getMergedDrops();
        } else if ("base".equalsIgnoreCase(packId)) {
            drops = LuckyAddonLoader.getBaseDrops();
        } else {
            for (var data : LuckyAddonLoader.getAddonDataList()) {
                if (data.packId().equalsIgnoreCase(packId)) {
                    drops = data.parsedDrops();
                    break;
                }
            }
        }
        if (drops.isEmpty()) {
            source.sendError(new LiteralText(
                "lppdrop list_drops: pack '" + packId + "' not found or has no drops. "
                    + "Use 'merged' or 'base' for global pools."));
            return 0;
        }
        int total  = drops.size();
        int from   = (page - 1) * pageSize;
        int to     = Math.min(from + pageSize, total);
        if (from >= total) {
            source.sendError(new LiteralText("lppdrop list_drops: page " + page + " out of range (total=" + total + ")"));
            return 0;
        }
        source.sendFeedback(new LiteralText(
            "§6§l─── " + packId + " drops [" + (from + 1) + "-" + to + " / " + total + "] ───"), false);
        for (int i = from; i < to; i++) {
            LuckyDropLine drop = drops.get(i);
            String type = drop.isGroup() ? "group" : (drop.type() != null ? drop.type() : "?");
            String typeColor = switch (type) {
                case "item"      -> "§b";
                case "entity"    -> "§a";
                case "block"     -> "§6";
                case "command"   -> "§e";
                case "structure" -> "§d";
                case "group"     -> "§c";
                default          -> "§7";
            };
            String id = drop.rawId();
            if (id == null || id.isBlank()) {
                String cmd = drop.getString("command");
                id = cmd != null ? ("/" + (cmd.length() > 30 ? cmd.substring(0, 30) + "…" : cmd)) : "(none)";
            }
            int lw  = drop.luckWeight();
            float ch = drop.chance();
            String luck    = (lw != 0) ? " §7w:" + (lw > 0 ? "+" : "") + lw : "";
            String chance  = (ch < 1.0f) ? " §7ch:" + String.format("%.0f%%", ch * 100) : "";
            String grpSuffix = drop.isGroup() && drop.groupEntries() != null
                ? " §c[×" + drop.groupEntries().size() + "]" : "";
            source.sendFeedback(new LiteralText(
                "§8" + (i + 1) + ". " + typeColor + type + "§f: " + id + luck + chance + grpSuffix), false);
        }
        if (to < total) {
            source.sendFeedback(new LiteralText(
                "§7... " + (total - to) + " more. (max " + pageSize + " shown)"), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    // =========================================================================
    // /lppdrop lucky_sim_counts <times> [pos] — simulate N Lucky drops, show type counts
    // =========================================================================

    private static int runLuckySimCounts(CommandContext<ServerCommandSource> ctx, int times, BlockPos providedPos) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos pos = resolvePos(source, providedPos);
        if (pos == null) {
            // Fall back to player foot position for simulation
            try {
                pos = source.getPlayer().getBlockPos();
            } catch (Exception ignored) {}
        }
        if (pos == null) {
            source.sendError(new LiteralText("lppdrop: cannot resolve position"));
            return 0;
        }
        SourceLoc loc = new SourceLoc("lucky", "lucky_sim_counts", pos.toShortString(), 0, "");
        LuckyDropContext dropCtx = new LuckyDropContext(world, pos, null, 0, RuntimeState.warnReporter(), loc);
        var drops = LuckyAddonLoader.getMergedDrops();
        if (drops.isEmpty()) drops = LuckyAddonLoader.getBaseDrops();
        if (drops.isEmpty()) {
            source.sendError(new LiteralText("lppdrop: no drops loaded"));
            return 0;
        }
        long startNs = System.nanoTime();
        var counts = LuckyDropEngine.simulateCounts(dropCtx, drops, times);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        source.sendFeedback(new LiteralText(
            "§6§l─── Lucky Sim Counts: " + times + " rolls in " + elapsedMs + "ms ───"), false);
        counts.entrySet().stream()
            .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(25)
            .forEach(e -> {
                float pct = 100f * e.getValue() / (float) times;
                source.sendFeedback(new LiteralText(
                    "§7  " + e.getKey() + "§f: " + e.getValue() + " §7(" + String.format("%.1f%%", pct) + ")"), false);
            });
        if (counts.size() > 25) {
            source.sendFeedback(new LiteralText("§7  ... and " + (counts.size() - 25) + " more types"), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static final class EvalStats {
        int matchedRules;
        int totalRuleGroups;
        int rolledGroups;
        int nullGroups;
        int totalEntries;
        int itemEntries;
        int entityEntries;
        int commandEntries;
        int invalidItems;
        int invalidEntities;
        int nonDefaultHeaderFields;
    }
}
