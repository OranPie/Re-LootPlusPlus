package ie.orangep.reLootplusplus.command;

import ie.orangep.reLootplusplus.command.exec.CommandChain;
import ie.orangep.reLootplusplus.command.exec.ExecContext;
import ie.orangep.reLootplusplus.command.exec.ExecResult;
import ie.orangep.reLootplusplus.content.entity.LootThrownItemEntity;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import ie.orangep.reLootplusplus.legacy.mapping.LegacyEntityIdFixer;
import ie.orangep.reLootplusplus.legacy.nbt.LenientNbtParser;
import ie.orangep.reLootplusplus.legacy.nbt.NbtPredicate;
import ie.orangep.reLootplusplus.legacy.mapping.LegacyEffectIdMapper;
import ie.orangep.reLootplusplus.legacy.mapping.LegacyParticleIdMapper;
import ie.orangep.reLootplusplus.legacy.selector.LegacySelectorParser;
import ie.orangep.reLootplusplus.legacy.selector.SelectorContext;
import ie.orangep.reLootplusplus.registry.EntityRegistrar;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.util.registry.Registry;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

public final class LegacyCommandRunner {
    private final LegacySelectorParser selectorParser = new LegacySelectorParser();

    public ExecResult run(String rawCommand, ExecContext ctx) {
        if (rawCommand == null || rawCommand.isEmpty()) {
            return ExecResult.success(0);
        }
        LegacyWarnReporter warnReporter = ctx.warnReporter();
        SourceLoc loc = ctx.sourceLoc();
        CommandChain chain = CommandChain.parse(rawCommand, warnReporter, loc);

        int success = 0;
        for (String command : chain.commands()) {
            success += runSingle(command, ctx).successCount();
        }
        return ExecResult.success(success);
    }

    private ExecResult runSingle(String raw, ExecContext ctx) {
        if (raw == null || raw.isEmpty()) {
            return ExecResult.success(0);
        }
        String trimmed = trimLeadingSlash(raw.trim());
        List<String> tokens = tokenize(trimmed);
        if (tokens.isEmpty()) {
            return ExecResult.success(0);
        }
        String verb = tokens.get(0).toLowerCase(Locale.ROOT);
        return switch (verb) {
            case "lppcondition" -> runLppCondition(tokens, ctx);
            case "lppeffect" -> runLppEffect(tokens, ctx);
            case "clear" -> runClear(tokens, ctx);
            case "effect" -> runEffect(tokens, ctx);
            case "playsound" -> runPlaysound(tokens, ctx);
            case "particle" -> runParticle(tokens, ctx);
            case "scoreboard" -> runScoreboard(tokens, ctx);
            case "execute" -> runExecute(tokens, ctx);
            case "testfor" -> runTestfor(tokens, ctx);
            case "summon" -> runSummon(tokens, ctx);
            case "setblock" -> runSetblock(tokens, ctx);
            case "kill" -> runKill(tokens, ctx);
            case "enchant" -> runEnchant(tokens, ctx);
            case "gamerule" -> runGamerule(tokens, ctx);
            default -> {
                if (ctx.warnReporter() != null) {
                    ctx.warnReporter().warn("LegacyCommand", "unknown verb " + verb, ctx.sourceLoc());
                }
                yield ExecResult.success(0);
            }
        };
    }

    private ExecResult runLppCondition(List<String> tokens, ExecContext ctx) {
        int trueIndex = tokens.indexOf("_if_true_");
        if (trueIndex < 0) {
            ctx.warnReporter().warn("LegacyCommand", "lppcondition parse", ctx.sourceLoc());
            return ExecResult.success(0);
        }
        int falseIndex = tokens.indexOf("_if_false_");
        String cond = join(tokens, 1, trueIndex);
        String thenCmd = falseIndex > trueIndex ? join(tokens, trueIndex + 1, falseIndex) : join(tokens, trueIndex + 1, tokens.size());
        String elseCmd = falseIndex > trueIndex ? join(tokens, falseIndex + 1, tokens.size()) : null;

        int condResult = run(cond, ctx).successCount();
        if (condResult > 0) {
            return run(thenCmd, ctx);
        }
        if (elseCmd != null && !elseCmd.isEmpty()) {
            return run(elseCmd, ctx);
        }
        return ExecResult.success(0);
    }

    private ExecResult runLppEffect(List<String> tokens, ExecContext ctx) {
        if (tokens.size() < 3) {
            ctx.warnReporter().warn("LegacyCommand", "lppeffect arity", ctx.sourceLoc());
            return ExecResult.success(0);
        }
        if ("clear".equals(tokens.get(1))) {
            return runEffectClear(tokens, ctx, 2);
        }
        return runLppEffectApply(tokens, ctx);
    }

    private ExecResult runClear(List<String> tokens, ExecContext ctx) {
        if (tokens.size() < 2) {
            ctx.warnReporter().warn("LegacyCommand", "clear arity", ctx.sourceLoc());
            return ExecResult.success(0);
        }
        List<Entity> targets = parseTargets(tokens.get(1), ctx);
        String itemId = tokens.size() >= 3 ? tokens.get(2) : null;
        int meta = tokens.size() >= 4 ? parseInt(tokens.get(3), 0, ctx) : 0;
        int count = tokens.size() >= 5 ? parseInt(tokens.get(4), -1, ctx) : -1;

        if (tokens.size() >= 4) {
            ctx.warnReporter().warnOnce("LegacyMeta", "meta used " + meta, ctx.sourceLoc());
        }

        int removed = 0;
        for (Entity entity : targets) {
            if (!(entity instanceof PlayerEntity player)) {
                continue;
            }
            if (itemId == null) {
                removed += clearAll(player);
                continue;
            }
            removed += clearItem(player, itemId, count, ctx);
        }
        return ExecResult.success(removed);
    }

    private ExecResult runEffect(List<String> tokens, ExecContext ctx) {
        if (tokens.size() < 3) {
            ctx.warnReporter().warn("LegacyCommand", "effect arity", ctx.sourceLoc());
            return ExecResult.success(0);
        }
        if ("clear".equals(tokens.get(2))) {
            return runEffectClear(tokens, ctx, 1);
        }
        return runEffectApply(tokens, ctx, 1);
    }

    private ExecResult runEffectClear(List<String> tokens, ExecContext ctx, int targetIndex) {
        if (tokens.size() <= targetIndex) {
            ctx.warnReporter().warn("LegacyCommand", "effect clear arity", ctx.sourceLoc());
            return ExecResult.success(0);
        }
        List<Entity> targets = parseTargets(tokens.get(targetIndex), ctx);
        String effectId = tokens.size() > targetIndex + 1 ? tokens.get(targetIndex + 1) : null;
        int cleared = 0;
        for (Entity entity : targets) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (effectId == null) {
                int before = living.getStatusEffects().size();
                living.clearStatusEffects();
                if (before > 0) {
                    cleared++;
                }
            } else {
                var effect = resolveStatusEffect(effectId, ctx);
                if (effect != null && living.hasStatusEffect(effect)) {
                    living.removeStatusEffect(effect);
                    cleared++;
                }
            }
        }
        return ExecResult.success(cleared);
    }

    private ExecResult runEffectApply(List<String> tokens, ExecContext ctx, int targetIndex) {
        if (tokens.size() <= targetIndex + 1) {
            ctx.warnReporter().warn("LegacyCommand", "effect apply arity", ctx.sourceLoc());
            return ExecResult.success(0);
        }
        List<Entity> targets = parseTargets(tokens.get(targetIndex), ctx);
        String effectId = tokens.get(targetIndex + 1);
        int seconds = tokens.size() > targetIndex + 2 ? parseInt(tokens.get(targetIndex + 2), 10, ctx) : 10;
        int amplifier = tokens.size() > targetIndex + 3 ? parseInt(tokens.get(targetIndex + 3), 0, ctx) : 0;
        boolean showParticles = true;
        if (tokens.size() > targetIndex + 4) {
            String raw = tokens.get(targetIndex + 4);
            boolean hideParticles = raw.equalsIgnoreCase("true");
            showParticles = !hideParticles;
        }

        var effect = resolveStatusEffect(effectId, ctx);
        if (effect == null) {
            return ExecResult.success(0);
        }
        int applied = 0;
        for (Entity entity : targets) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            living.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(effect, seconds * 20, amplifier, false, showParticles));
            applied++;
        }
        return ExecResult.success(applied);
    }

    private ExecResult runLppEffectApply(List<String> tokens, ExecContext ctx) {
        if (tokens.size() < 6) {
            ctx.warnReporter().warn("LegacyCommand", "lppeffect apply arity", ctx.sourceLoc());
            return ExecResult.success(0);
        }
        List<Entity> targets = parseTargets(tokens.get(1), ctx);
        String effectId = tokens.get(2);
        int seconds = parseInt(tokens.get(3), 10, ctx);
        int amplifier = parseInt(tokens.get(4), 0, ctx);
        boolean showParticles = Boolean.parseBoolean(tokens.get(5));

        var effect = resolveStatusEffect(effectId, ctx);
        if (effect == null) {
            return ExecResult.success(0);
        }
        int applied = 0;
        for (Entity entity : targets) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            living.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(effect, seconds * 20, amplifier, false, showParticles));
            applied++;
        }
        return ExecResult.success(applied);
    }

    private ExecResult runPlaysound(List<String> tokens, ExecContext ctx) {
        if (tokens.size() < 3) {
            ctx.warnReporter().warn("LegacyCommand", "playsound arity", ctx.sourceLoc());
            return ExecResult.success(0);
        }
        String soundId = tokens.get(1);
        List<Entity> targets = parseTargets(tokens.get(2), ctx);
        BlockPos origin = ctx.origin();
        double x = origin.getX();
        double y = origin.getY();
        double z = origin.getZ();
        float volume = 1.0f;
        float pitch = 1.0f;

        int index = 3;
        if (tokens.size() > index + 2) {
            x = resolveCoordDouble(origin.getX(), tokens.get(index), x, ctx);
            y = resolveCoordDouble(origin.getY(), tokens.get(index + 1), y, ctx);
            z = resolveCoordDouble(origin.getZ(), tokens.get(index + 2), z, ctx);
            index += 3;
        }
        if (tokens.size() > index) {
            volume = (float) parseDouble(tokens.get(index), volume, ctx);
            index++;
        }
        if (tokens.size() > index) {
            pitch = (float) parseDouble(tokens.get(index), pitch, ctx);
        }

        SoundEvent sound = resolveSound(soundId, ctx);
        if (sound == null) {
            return ExecResult.success(0);
        }

        int played = 0;
        for (Entity entity : targets) {
            if (entity instanceof ServerPlayerEntity player) {
                player.playSound(sound, SoundCategory.MASTER, volume, pitch);
                played++;
            }
        }
        return ExecResult.success(played);
    }

    private ExecResult runParticle(List<String> tokens, ExecContext ctx) {
        if (tokens.size() < 2) {
            ctx.warnReporter().warn("LegacyCommand", "particle arity", ctx.sourceLoc());
            return ExecResult.success(0);
        }
        String particleName = tokens.get(1);
        Identifier id = LegacyParticleIdMapper.resolve(particleName, ctx.warnReporter(), ctx.sourceLoc());
        if (id == null || !Registry.PARTICLE_TYPE.containsId(id)) {
            ctx.warnReporter().warn("LegacyParticle", "missing " + particleName, ctx.sourceLoc());
            return ExecResult.success(0);
        }
        ParticleType<?> type = Registry.PARTICLE_TYPE.get(id);
        if (!(type instanceof ParticleEffect effect)) {
            ctx.warnReporter().warn("LegacyParticle", "unsupported particle " + id, ctx.sourceLoc());
            return ExecResult.success(0);
        }

        BlockPos origin = ctx.origin();
        double x = origin.getX();
        double y = origin.getY();
        double z = origin.getZ();
        int index = 2;
        if (tokens.size() >= index + 3) {
            x = parseDouble(tokens.get(index), x, ctx);
            y = parseDouble(tokens.get(index + 1), y, ctx);
            z = parseDouble(tokens.get(index + 2), z, ctx);
            index += 3;
        }
        double dx = tokens.size() > index ? parseDouble(tokens.get(index), 0.0, ctx) : 0.0;
        double dy = tokens.size() > index + 1 ? parseDouble(tokens.get(index + 1), 0.0, ctx) : 0.0;
        double dz = tokens.size() > index + 2 ? parseDouble(tokens.get(index + 2), 0.0, ctx) : 0.0;
        double speed = tokens.size() > index + 3 ? parseDouble(tokens.get(index + 3), 0.0, ctx) : 0.0;
        int count = tokens.size() > index + 4 ? parseInt(tokens.get(index + 4), 1, ctx) : 1;

        ctx.world().spawnParticles(effect, x, y, z, count, dx, dy, dz, speed);
        return ExecResult.success(1);
    }

    private ExecResult runScoreboard(List<String> tokens, ExecContext ctx) {
        if (tokens.size() < 5) {
            ctx.warnReporter().warn("LegacyCommand", "scoreboard arity", ctx.sourceLoc());
            return ExecResult.success(0);
        }
        if (!"players".equals(tokens.get(1))) {
            ctx.warnReporter().warn("LegacyCommand", "scoreboard subcommand unsupported", ctx.sourceLoc());
            return ExecResult.success(0);
        }
        String action = tokens.get(2);
        List<Entity> targets = parseTargets(tokens.get(3), ctx);
        String objectiveName = tokens.get(4);
        int value = tokens.size() > 5 ? parseInt(tokens.get(5), 0, ctx) : 0;

        Scoreboard scoreboard = ctx.world().getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjective(objectiveName);
        if (objective == null) {
            ctx.warnReporter().warn("LegacyScore", "objective missing " + objectiveName, ctx.sourceLoc());
            return ExecResult.success(0);
        }

        int updated = 0;
        for (Entity entity : targets) {
            String name = entity.getName().getString();
        ScoreboardPlayerScore score = scoreboard.getPlayerScore(name, objective);
            switch (action) {
                case "set" -> score.setScore(value);
                case "add" -> score.incrementScore(value);
                case "remove" -> score.incrementScore(-value);
                default -> {
                    ctx.warnReporter().warn("LegacyCommand", "scoreboard action unsupported " + action, ctx.sourceLoc());
                    return ExecResult.success(0);
                }
            }
            updated++;
        }
        return ExecResult.success(updated);
    }

    private ExecResult runExecute(List<String> tokens, ExecContext ctx) {
        if (tokens.size() < 6) {
            ctx.warnReporter().warn("LegacyCommand", "execute arity", ctx.sourceLoc());
            return ExecResult.success(0);
        }
        List<Entity> targets = parseTargets(tokens.get(1), ctx);
        String xRaw = tokens.get(2);
        String yRaw = tokens.get(3);
        String zRaw = tokens.get(4);
        String subCommand = join(tokens, 5, tokens.size());

        int total = 0;
        for (Entity target : targets) {
            BlockPos base = target.getBlockPos();
            BlockPos origin = new BlockPos(
                resolveCoord(base.getX(), xRaw),
                resolveCoord(base.getY(), yRaw),
                resolveCoord(base.getZ(), zRaw)
            );
            ExecContext child = new ExecContext(
                ctx.world(),
                origin,
                target,
                ctx.random(),
                ctx.sourceLoc(),
                ctx.warnReporter()
            );
            total += run(subCommand, child).successCount();
        }
        return ExecResult.success(total);
    }

    private ExecResult runTestfor(List<String> tokens, ExecContext ctx) {
        if (tokens.size() < 2) {
            ctx.warnReporter().warn("LegacyCommand", "testfor arity", ctx.sourceLoc());
            return ExecResult.success(0);
        }
        List<Entity> targets = parseTargets(tokens.get(1), ctx);
        if (tokens.size() >= 3) {
            ctx.warnReporter().warn("LegacyNBT", "testfor nbt used", ctx.sourceLoc());
            NbtCompound needle = LenientNbtParser.parseOrNull(tokens.get(2), ctx.warnReporter(), ctx.sourceLoc(), "LegacyNBT");
            if (needle != null) {
                targets.removeIf(entity -> {
                    NbtCompound data = new NbtCompound();
                    entity.writeNbt(data);
                    return !NbtPredicate.matches(data, needle);
                });
            }
        }
        return ExecResult.success(targets.size());
    }

    private ExecResult runSummon(List<String> tokens, ExecContext ctx) {
        if (tokens.size() < 2) {
            ctx.warnReporter().warn("LegacyCommand", "summon arity", ctx.sourceLoc());
            return ExecResult.success(0);
        }
        String entityId = tokens.get(1);
        // Normalize legacy entity IDs (e.g. lootplusplus.ThrownItem → re-lootplusplus:loot_thrown)
        entityId = LegacyEntityIdFixer.normalizeEntityId(entityId, ctx.warnReporter(),
            ctx.sourceLoc() == null ? null : ctx.sourceLoc().formatShort());
        BlockPos origin = ctx.origin();
        int index = 2;
        double x = origin.getX();
        double y = origin.getY();
        double z = origin.getZ();
        if (tokens.size() >= index + 3) {
            x = parseDouble(tokens.get(index), x, ctx);
            y = parseDouble(tokens.get(index + 1), y, ctx);
            z = parseDouble(tokens.get(index + 2), z, ctx);
            index += 3;
        }
        NbtCompound nbt = null;
        if (tokens.size() > index) {
            ctx.warnReporter().warn("LegacyNBT", "summon nbt used", ctx.sourceLoc());
            nbt = LenientNbtParser.parseOrEmpty(tokens.get(index), ctx.warnReporter(), ctx.sourceLoc(), "LegacyNBT");
        }
        EntityType<?> type = resolveEntityType(entityId, ctx);
        if (type == null) {
            return ExecResult.success(0);
        }
        // Special handling for thrown items: read ItemThrown from NBT to set the ThrownDef
        if (type == EntityRegistrar.THROWN_ENTITY && nbt != null
                && nbt.contains("ItemThrown", net.minecraft.nbt.NbtElement.STRING_TYPE)) {
            String itemId = nbt.getString("ItemThrown");
            var thrownRegistry = RuntimeState.thrownRegistry();
            var thrownDef = thrownRegistry != null ? thrownRegistry.get(itemId) : null;
            var commandRunner = RuntimeState.commandRunner();
            var warnReporter = RuntimeState.warnReporter();
            LootThrownItemEntity thrown = new LootThrownItemEntity(
                EntityRegistrar.THROWN_ENTITY, ctx.world(), thrownDef, commandRunner, warnReporter);
            thrown.readNbt(nbt);
            thrown.refreshPositionAndAngles(x, y, z, thrown.getYaw(), thrown.getPitch());
            boolean spawned = ctx.world().spawnEntity(thrown);
            return ExecResult.success(spawned ? 1 : 0);
        }
        Entity entity = type.create(ctx.world());
        if (entity == null) {
            return ExecResult.success(0);
        }
        entity.refreshPositionAndAngles(x, y, z, entity.getYaw(), entity.getPitch());
        if (nbt != null) {
            entity.readNbt(nbt);
        }
        boolean spawned = ctx.world().spawnEntity(entity);
        return ExecResult.success(spawned ? 1 : 0);
    }

    private ExecResult runSetblock(List<String> tokens, ExecContext ctx) {
        if (tokens.size() < 5) {
            ctx.warnReporter().warn("LegacyCommand", "setblock arity", ctx.sourceLoc());
            return ExecResult.success(0);
        }
        BlockPos origin = ctx.origin();
        int x = resolveCoord(origin.getX(), tokens.get(1));
        int y = resolveCoord(origin.getY(), tokens.get(2));
        int z = resolveCoord(origin.getZ(), tokens.get(3));
        String blockId = tokens.get(4);
        int meta = tokens.size() >= 6 ? parseInt(tokens.get(5), 0, ctx) : 0;
        String mode = tokens.size() >= 7 ? tokens.get(6) : "replace";
        NbtCompound nbt = tokens.size() >= 8
            ? LenientNbtParser.parseOrEmpty(tokens.get(7), ctx.warnReporter(), ctx.sourceLoc(), "LegacyNBT")
            : null;

        if (tokens.size() >= 6) {
            ctx.warnReporter().warnOnce("LegacyMeta", "block meta used " + meta, ctx.sourceLoc());
        }
        Block block = resolveBlock(blockId, ctx);
        if (block == null) {
            return ExecResult.success(0);
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = block.getDefaultState();

        switch (mode) {
            case "keep" -> {
                if (!ctx.world().getBlockState(pos).isAir()) {
                    return ExecResult.success(0);
                }
            }
            case "destroy" -> ctx.world().breakBlock(pos, true);
            default -> {
            }
        }

        boolean success = ctx.world().setBlockState(pos, state);
        if (success && nbt != null) {
            var blockEntity = ctx.world().getBlockEntity(pos);
            if (blockEntity != null) {
                blockEntity.readNbt(nbt);
            }
        }
        return ExecResult.success(success ? 1 : 0);
    }

    private ExecResult runKill(List<String> tokens, ExecContext ctx) {
        if (tokens.size() < 2) {
            ctx.warnReporter().warn("LegacyCommand", "kill arity", ctx.sourceLoc());
            return ExecResult.success(0);
        }
        List<Entity> targets = parseTargets(tokens.get(1), ctx);
        int killed = 0;
        for (Entity entity : targets) {
            entity.kill();
            killed++;
        }
        return ExecResult.success(killed);
    }

    private ExecResult runEnchant(List<String> tokens, ExecContext ctx) {
        if (tokens.size() < 3) {
            ctx.warnReporter().warn("LegacyCommand", "enchant arity", ctx.sourceLoc());
            return ExecResult.success(0);
        }
        List<Entity> targets = parseTargets(tokens.get(1), ctx);
        String enchantId = tokens.get(2);
        int level = tokens.size() > 3 ? parseInt(tokens.get(3), 1, ctx) : 1;
        Enchantment enchantment = resolveEnchantment(enchantId, ctx);
        if (enchantment == null) {
            return ExecResult.success(0);
        }
        int enchanted = 0;
        for (Entity entity : targets) {
            if (!(entity instanceof PlayerEntity player)) {
                continue;
            }
            ItemStack stack = player.getMainHandStack();
            if (stack.isEmpty()) {
                continue;
            }
            stack.addEnchantment(enchantment, level);
            enchanted++;
        }
        return ExecResult.success(enchanted);
    }

    private ExecResult runGamerule(List<String> tokens, ExecContext ctx) {
        if (tokens.size() < 3) {
            ctx.warnReporter().warn("LegacyCommand", "gamerule arity", ctx.sourceLoc());
            return ExecResult.success(0);
        }
        String ruleName = tokens.get(1);
        String value = tokens.get(2);
        boolean success = setGamerule(ctx, ruleName, value);
        if (!success) {
            ctx.warnReporter().warn("LegacyGamerule", "unknown gamerule " + ruleName, ctx.sourceLoc());
        }
        return ExecResult.success(success ? 1 : 0);
    }

    private List<Entity> parseTargets(String token, ExecContext ctx) {
        if (token.startsWith("@")) {
            SelectorContext sctx = new SelectorContext(ctx.world(), ctx.origin(), ctx.random(), ctx.sourceLoc(), ctx.warnReporter());
            return selectorParser.select(token, sctx);
        }
        ServerPlayerEntity player = ctx.world().getServer().getPlayerManager().getPlayer(token);
        if (player != null) {
            return List.of(player);
        }
        return List.of();
    }

    private int clearAll(PlayerEntity player) {
        int removed = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                removed += stack.getCount();
                player.getInventory().setStack(i, ItemStack.EMPTY);
            }
        }
        return removed;
    }

    private int clearItem(PlayerEntity player, String itemId, int count, ExecContext ctx) {
        Item item = resolveItem(itemId, ctx);
        if (item == null) {
            return 0;
        }
        int remaining = count < 0 ? Integer.MAX_VALUE : count;
        int removed = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty() || stack.getItem() != item) {
                continue;
            }
            int take = Math.min(stack.getCount(), remaining);
            stack.decrement(take);
            removed += take;
            remaining -= take;
            if (stack.isEmpty()) {
                player.getInventory().setStack(i, ItemStack.EMPTY);
            }
            if (remaining <= 0) {
                break;
            }
        }
        return removed;
    }

    private net.minecraft.entity.effect.StatusEffect resolveStatusEffect(String id, ExecContext ctx) {
        Identifier identifier = LegacyEffectIdMapper.resolve(id, ctx.warnReporter(), ctx.sourceLoc());
        if (identifier == null) {
            return null;
        }
        if (!Registry.STATUS_EFFECT.containsId(identifier)) {
            ctx.warnReporter().warn("LegacyEffect", "missing " + identifier, ctx.sourceLoc());
            return null;
        }
        return Registry.STATUS_EFFECT.get(identifier);
    }

    private SoundEvent resolveSound(String id, ExecContext ctx) {
        Identifier identifier = Identifier.tryParse(id);
        String normalized = null;
        if (identifier == null) {
            normalized = normalizeSoundId(id);
            identifier = normalized == null ? null : Identifier.tryParse(normalized);
            if (identifier == null) {
                ctx.warnReporter().warn("LegacySound", "bad id " + id, ctx.sourceLoc());
                return null;
            }
        }
        if (!Registry.SOUND_EVENT.containsId(identifier)) {
            if (normalized == null) {
                normalized = normalizeSoundId(id);
            }
            Identifier retry = normalized == null ? null : Identifier.tryParse(normalized);
            if (retry != null && Registry.SOUND_EVENT.containsId(retry)) {
                ctx.warnReporter().warn("LegacySound", "normalized " + id + " -> " + normalized, ctx.sourceLoc());
                return Registry.SOUND_EVENT.get(retry);
            }
            ctx.warnReporter().warn("LegacySound", "missing " + id, ctx.sourceLoc());
            return null;
        }
        return Registry.SOUND_EVENT.get(identifier);
    }

    private static String normalizeSoundId(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String namespace = "minecraft";
        String path = trimmed;
        int idx = trimmed.indexOf(':');
        if (idx >= 0) {
            namespace = trimmed.substring(0, idx);
            path = trimmed.substring(idx + 1);
        }
        namespace = namespace.toLowerCase(java.util.Locale.ROOT);
        StringBuilder cleaned = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '/' || c == '.' || c == '_' || c == '-') {
                cleaned.append(c);
            } else if (Character.isUpperCase(c)) {
                cleaned.append(Character.toLowerCase(c));
            } else {
                cleaned.append('_');
            }
        }
        return namespace + ":" + cleaned;
    }

    private EntityType<?> resolveEntityType(String id, ExecContext ctx) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            ctx.warnReporter().warn("LegacyEntityId", "bad id " + id, ctx.sourceLoc());
            return null;
        }
        if (!Registry.ENTITY_TYPE.containsId(identifier)) {
            ctx.warnReporter().warn("LegacyEntityId", "missing " + id, ctx.sourceLoc());
            return null;
        }
        return Registry.ENTITY_TYPE.get(identifier);
    }

    private Block resolveBlock(String id, ExecContext ctx) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            ctx.warnReporter().warn("LegacyBlockId", "bad id " + id, ctx.sourceLoc());
            return null;
        }
        if (!Registry.BLOCK.containsId(identifier)) {
            ctx.warnReporter().warn("LegacyBlockId", "missing " + id, ctx.sourceLoc());
            return null;
        }
        return Registry.BLOCK.get(identifier);
    }

    private Item resolveItem(String id, ExecContext ctx) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            ctx.warnReporter().warn("LegacyItemId", "bad id " + id, ctx.sourceLoc());
            return null;
        }
        if (!Registry.ITEM.containsId(identifier)) {
            ctx.warnReporter().warn("LegacyItemId", "missing " + id, ctx.sourceLoc());
            return null;
        }
        return Registry.ITEM.get(identifier);
    }

    private Enchantment resolveEnchantment(String id, ExecContext ctx) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            ctx.warnReporter().warn("LegacyEnchant", "bad id " + id, ctx.sourceLoc());
            return null;
        }
        if (!Registry.ENCHANTMENT.containsId(identifier)) {
            ctx.warnReporter().warn("LegacyEnchant", "missing " + id, ctx.sourceLoc());
            return null;
        }
        return Registry.ENCHANTMENT.get(identifier);
    }

    private static boolean isNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static String trimLeadingSlash(String raw) {
        if (raw.startsWith("/")) {
            return raw.substring(1).trim();
        }
        return raw;
    }

    static List<String> tokenize(String raw) {
        List<String> tokens = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        int braceDepth = 0;
        boolean inSelector = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '{') {
                braceDepth++;
            } else if (c == '}') {
                braceDepth = Math.max(0, braceDepth - 1);
            } else if (c == '[') {
                if (!inSelector && current.length() > 0 && current.charAt(0) == '@') {
                    inSelector = true;
                }
            } else if (c == ']') {
                if (inSelector) {
                    inSelector = false;
                }
            }

            if (Character.isWhitespace(c) && braceDepth == 0 && !inSelector) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private String join(List<String> tokens, int start, int end) {
        if (start >= end) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) {
                sb.append(' ');
            }
            sb.append(tokens.get(i));
        }
        return sb.toString();
    }

    private int parseInt(String raw, int fallback, ExecContext ctx) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            ctx.warnReporter().warn("LegacyCommand", "bad int " + raw, ctx.sourceLoc());
            return fallback;
        }
    }

    private double parseDouble(String raw, double fallback, ExecContext ctx) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            ctx.warnReporter().warn("LegacyCommand", "bad double " + raw, ctx.sourceLoc());
            return fallback;
        }
    }

    private int resolveCoord(int base, String raw) {
        if (raw.startsWith("~")) {
            if (raw.length() == 1) {
                return base;
            }
            try {
                return base + Integer.parseInt(raw.substring(1));
            } catch (NumberFormatException e) {
                return base;
            }
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return base;
        }
    }

    private boolean setGamerule(ExecContext ctx, String ruleName, String value) {
        try {
            Class<?> gameRulesClass = GameRules.class;
            Object key = invokeStatic(gameRulesClass, "getRuleKey", new Class<?>[]{String.class}, new Object[]{ruleName});
            if (key == null) {
                key = invokeStatic(gameRulesClass, "getKey", new Class<?>[]{String.class}, new Object[]{ruleName});
            }
            if (key == null) {
                return false;
            }
            Object rules = ctx.world().getGameRules();
            Object rule = invokeInstance(rules, "get", new Class<?>[]{key.getClass()}, new Object[]{key});
            if (rule == null) {
                return false;
            }
            Object result = invokeInstance(rule, "set", new Class<?>[]{String.class, net.minecraft.server.MinecraftServer.class}, new Object[]{value, ctx.world().getServer()});
            if (result instanceof Boolean bool) {
                return bool;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Object invokeStatic(Class<?> type, String name, Class<?>[] types, Object[] args) {
        try {
            var method = type.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (Exception e) {
            return null;
        }
    }

    private Object invokeInstance(Object target, String name, Class<?>[] types, Object[] args) {
        try {
            var method = target.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (Exception e) {
            return null;
        }
    }

    private double resolveCoordDouble(double base, String raw, double fallback, ExecContext ctx) {
        if (raw.startsWith("~")) {
            if (raw.length() == 1) {
                return base;
            }
            try {
                return base + Double.parseDouble(raw.substring(1));
            } catch (NumberFormatException e) {
                ctx.warnReporter().warn("LegacyCommand", "bad coord " + raw, ctx.sourceLoc());
                return base;
            }
        }
        return parseDouble(raw, fallback, ctx);
    }
}
