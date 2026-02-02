package ie.orangep.reLootplusplus.runtime;

import ie.orangep.reLootplusplus.command.LegacyCommandRunner;
import ie.orangep.reLootplusplus.command.exec.ExecContext;
import ie.orangep.reLootplusplus.config.model.key.ItemKey;
import ie.orangep.reLootplusplus.config.model.rule.CommandRule;
import ie.orangep.reLootplusplus.config.model.rule.EffectRule;
import ie.orangep.reLootplusplus.legacy.mapping.LegacyEffectIdMapper;
import ie.orangep.reLootplusplus.legacy.nbt.LenientNbtParser;
import ie.orangep.reLootplusplus.legacy.nbt.NbtPredicate;
import ie.orangep.reLootplusplus.runtime.trigger.TriggerType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;

import java.util.List;

public final class RuleEngine {
    private final LegacyCommandRunner commandRunner;
    private final RuntimeIndex index;

    public RuleEngine(RuntimeIndex index, LegacyCommandRunner commandRunner) {
        this.index = index;
        this.commandRunner = commandRunner;
    }

    public void executeForPlayer(RuntimeContext ctx, PlayerEntity player, TriggerType trigger) {
        executeForPlayer(ctx, player, trigger, player.getBlockPos());
    }

    public void executeForPlayer(RuntimeContext ctx, PlayerEntity player, TriggerType trigger, BlockPos origin) {
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) {
            return;
        }
        String itemId = net.minecraft.util.registry.Registry.ITEM.getId(stack.getItem()).toString();
        runRules(ctx, player, trigger, itemId, stack, origin);
    }

    public void executeForItem(RuntimeContext ctx, PlayerEntity player, TriggerType trigger, ItemStack stack, BlockPos origin) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String itemId = net.minecraft.util.registry.Registry.ITEM.getId(stack.getItem()).toString();
        runRules(ctx, player, trigger, itemId, stack, origin);
    }

    public void executeArmour(RuntimeContext ctx, PlayerEntity player, TriggerType trigger) {
        for (int i = 0; i < player.getInventory().armor.size(); i++) {
            ItemStack stack = player.getInventory().armor.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            String itemId = net.minecraft.util.registry.Registry.ITEM.getId(stack.getItem()).toString();
            runRules(ctx, player, trigger, itemId, stack, player.getBlockPos());
        }
    }

    public void executeInventory(RuntimeContext ctx, PlayerEntity player, TriggerType trigger) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            String itemId = net.minecraft.util.registry.Registry.ITEM.getId(stack.getItem()).toString();
            runRules(ctx, player, trigger, itemId, stack, player.getBlockPos());
        }
    }

    public void executeBlocksInInventory(RuntimeContext ctx, PlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
                continue;
            }
            String blockId = Registry.BLOCK.getId(blockItem.getBlock()).toString();
            handleBlockTrigger(ctx, player, TriggerType.BLOCKS_IN_INVENTORY, player.getBlockPos(), blockId);
        }
    }

    public void executeStandingOnBlock(RuntimeContext ctx, PlayerEntity player) {
        BlockPos pos = player.getBlockPos().down();
        handleBlockTrigger(ctx, player, TriggerType.STANDING_ON_BLOCK, pos);
    }

    public void executeInsideBlock(RuntimeContext ctx, PlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        handleBlockTrigger(ctx, player, TriggerType.INSIDE_BLOCK, pos);
    }

    public void executeBlockTrigger(RuntimeContext ctx, PlayerEntity player, TriggerType trigger, BlockPos pos) {
        handleBlockTrigger(ctx, player, trigger, pos);
    }

    public void executeBlockTrigger(RuntimeContext ctx, PlayerEntity player, TriggerType trigger, BlockPos pos, String blockId) {
        handleBlockTrigger(ctx, player, trigger, pos, blockId);
    }

    private void runRules(RuntimeContext ctx, PlayerEntity player, TriggerType trigger, String itemId, ItemStack stack, BlockPos origin) {
        for (CommandRule rule : index.commandRules(trigger, itemId)) {
            if (!matchesItem(rule.itemKey(), stack)) {
                continue;
            }
            if (!matchesSetBonus(player, trigger, rule.setBonusItems())) {
                continue;
            }
            if (!roll(rule.probability(), ctx)) {
                continue;
            }
            ExecContext exec = new ExecContext(
                ctx.world(),
                origin,
                player,
                ctx.random(),
                rule.sourceLoc(),
                ctx.warnReporter()
            );
            commandRunner.run(rule.command(), exec);
        }

        for (EffectRule rule : index.effectRules(trigger, itemId)) {
            if (!matchesItem(rule.itemKey(), stack)) {
                continue;
            }
            if (!matchesSetBonus(player, trigger, rule.setBonusItems())) {
                continue;
            }
            if (!roll(rule.probability(), ctx)) {
                continue;
            }
            net.minecraft.util.Identifier effectId = LegacyEffectIdMapper.resolve(rule.effectId(), ctx.warnReporter(), rule.sourceLoc());
            var effect = effectId == null ? null : net.minecraft.util.registry.Registry.STATUS_EFFECT.get(effectId);
            if (effect == null) {
                if (effectId != null) {
                    ctx.warnReporter().warn("LegacyEffect", "missing effect " + effectId, rule.sourceLoc());
                }
                continue;
            }
            boolean showParticles = !"none".equalsIgnoreCase(rule.particleType());
            boolean ambient = "faded".equalsIgnoreCase(rule.particleType());
            player.addStatusEffect(
                new net.minecraft.entity.effect.StatusEffectInstance(effect, rule.duration() * 20, rule.amplifier(), ambient, showParticles, showParticles)
            );
        }
    }

    private boolean matchesItem(ItemKey key, ItemStack stack) {
        if (key == null) {
            return false;
        }
        if (key.meta() == 32767) {
            // wildcard
        }
        if (key.nbtRaw() != null && !key.nbtRaw().isEmpty() && !"{}".equals(key.nbtRaw())) {
            NbtCompound needle = LenientNbtParser.parseOrNull(key.nbtRaw(), null, null, "LegacyNBT");
            if (needle != null) {
                NbtCompound data = stack.getOrCreateNbt();
                return NbtPredicate.matches(data, needle);
            }
        }
        return true;
    }

    private boolean matchesSetBonus(PlayerEntity player, TriggerType trigger, List<String> setBonusItems) {
        if (setBonusItems == null || setBonusItems.isEmpty()) {
            return true;
        }
        if (trigger != TriggerType.WEARING_ARMOUR) {
            return true;
        }
        for (String itemId : setBonusItems) {
            if (itemId == null || itemId.isEmpty()) {
                return false;
            }
            if (!hasArmorItem(player, itemId)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasArmorItem(PlayerEntity player, String itemId) {
        for (int i = 0; i < player.getInventory().armor.size(); i++) {
            ItemStack stack = player.getInventory().armor.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            String id = Registry.ITEM.getId(stack.getItem()).toString();
            if (itemId.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private void handleBlockTrigger(RuntimeContext ctx, PlayerEntity player, TriggerType trigger, BlockPos pos) {
        String blockId = Registry.BLOCK.getId(ctx.world().getBlockState(pos).getBlock()).toString();
        handleBlockTrigger(ctx, player, trigger, pos, blockId);
    }

    private void handleBlockTrigger(RuntimeContext ctx, PlayerEntity player, TriggerType trigger, BlockPos pos, String blockId) {
        for (CommandRule rule : index.blockCommandRules(trigger, blockId)) {
            if (!roll(rule.probability(), ctx)) {
                continue;
            }
            ExecContext exec = new ExecContext(
                ctx.world(),
                pos,
                player,
                ctx.random(),
                rule.sourceLoc(),
                ctx.warnReporter()
            );
            commandRunner.run(rule.command(), exec);
        }
        for (EffectRule rule : index.blockEffectRules(trigger, blockId)) {
            if (!roll(rule.probability(), ctx)) {
                continue;
            }
            net.minecraft.util.Identifier effectId = LegacyEffectIdMapper.resolve(rule.effectId(), ctx.warnReporter(), rule.sourceLoc());
            var effect = effectId == null ? null : net.minecraft.util.registry.Registry.STATUS_EFFECT.get(effectId);
            if (effect == null) {
                if (effectId != null) {
                    ctx.warnReporter().warn("LegacyEffect", "missing effect " + effectId, rule.sourceLoc());
                }
                continue;
            }
            boolean showParticles = !"none".equalsIgnoreCase(rule.particleType());
            boolean ambient = "faded".equalsIgnoreCase(rule.particleType());
            player.addStatusEffect(
                new net.minecraft.entity.effect.StatusEffectInstance(effect, rule.duration() * 20, rule.amplifier(), ambient, showParticles, showParticles)
            );
        }
    }

    private boolean roll(double probability, RuntimeContext ctx) {
        if (probability >= 1.0) {
            return true;
        }
        if (probability <= 0.0) {
            return false;
        }
        return ctx.random().nextDouble() <= probability;
    }
}
