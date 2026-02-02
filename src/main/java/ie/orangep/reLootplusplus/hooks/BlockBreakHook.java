package ie.orangep.reLootplusplus.hooks;

import ie.orangep.reLootplusplus.command.LegacyCommandRunner;
import ie.orangep.reLootplusplus.command.exec.ExecContext;
import ie.orangep.reLootplusplus.config.model.drop.DropEntry;
import ie.orangep.reLootplusplus.config.model.drop.DropEntryCommand;
import ie.orangep.reLootplusplus.config.model.drop.DropEntryEntity;
import ie.orangep.reLootplusplus.config.model.drop.DropEntryItem;
import ie.orangep.reLootplusplus.config.model.drop.DropGroup;
import ie.orangep.reLootplusplus.config.model.drop.DropRoller;
import ie.orangep.reLootplusplus.config.model.rule.BlockDropRemoval;
import ie.orangep.reLootplusplus.config.model.rule.BlockDropRule;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.legacy.nbt.LenientNbtParser;
import ie.orangep.reLootplusplus.runtime.BlockDropRegistry;
import ie.orangep.reLootplusplus.runtime.RuleEngine;
import ie.orangep.reLootplusplus.runtime.RuntimeContext;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public final class BlockBreakHook {
    private final RuleEngine ruleEngine;
    private final BlockDropRegistry blockDropRegistry;
    private final LegacyWarnReporter warnReporter;
    private final LegacyCommandRunner commandRunner = new LegacyCommandRunner();

    public BlockBreakHook(RuleEngine ruleEngine, BlockDropRegistry blockDropRegistry, LegacyWarnReporter warnReporter) {
        this.ruleEngine = ruleEngine;
        this.blockDropRegistry = blockDropRegistry;
        this.warnReporter = warnReporter;
    }

    public void install() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient()) {
                return;
            }
            ServerWorld serverWorld = (ServerWorld) world;
            RuntimeContext runtimeContext = new RuntimeContext(serverWorld.getServer(), serverWorld, serverWorld.getRandom(), warnReporter);
            ruleEngine.executeForPlayer(runtimeContext, player, ie.orangep.reLootplusplus.runtime.trigger.TriggerType.BREAKING_BLOCK, pos);
            ruleEngine.executeForPlayer(runtimeContext, player, ie.orangep.reLootplusplus.runtime.trigger.TriggerType.DIGGING_BLOCK, pos);
            String blockId = Registry.BLOCK.getId(state.getBlock()).toString();
            ruleEngine.executeBlockTrigger(runtimeContext, player, ie.orangep.reLootplusplus.runtime.trigger.TriggerType.DIGGING_BLOCK_BLOCK, pos, blockId);
            applyBlockDrops(serverWorld, pos, state);
        });
    }

    private void applyBlockDrops(ServerWorld world, net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState state) {
        String blockId = Registry.BLOCK.getId(state.getBlock()).toString();
        for (BlockDropRemoval removal : blockDropRegistry.removeRules()) {
            if (!removal.blockId().equals(blockId)) {
                continue;
            }
            // Removal logic is placeholder; actual drop removal would need loot-table interception.
            warnReporter.warnOnce("LegacyDropRemoval", "block drop removal configured for " + blockId, removal.sourceLoc());
        }

        for (BlockDropRule rule : blockDropRegistry.addRules()) {
            if (!rule.blockId().equals(blockId)) {
                continue;
            }
            DropGroup group = DropRoller.rollGroup(rule.groups(), world.getRandom());
            if (group == null) {
                continue;
            }
            for (DropEntry entry : group.entries()) {
                executeDrop(world, pos, entry, rule.sourceLoc());
            }
        }
    }

    private void executeDrop(ServerWorld world, net.minecraft.util.math.BlockPos pos, DropEntry entry, ie.orangep.reLootplusplus.diagnostic.SourceLoc loc) {
        if (entry instanceof DropEntryItem itemDrop) {
            int count = itemDrop.minCount();
            if (itemDrop.maxCount() > itemDrop.minCount()) {
                count = itemDrop.minCount() + world.getRandom().nextInt(itemDrop.maxCount() - itemDrop.minCount() + 1);
            }
            Identifier id = Identifier.tryParse(itemDrop.itemId());
            if (id == null || !Registry.ITEM.containsId(id)) {
                warnReporter.warn("LegacyItemId", "missing item " + itemDrop.itemId(), loc);
                return;
            }
            ItemStack stack = new ItemStack(Registry.ITEM.get(id), count);
            if (itemDrop.nbtRaw() != null && !itemDrop.nbtRaw().isEmpty()) {
                var tag = LenientNbtParser.parseOrEmpty(itemDrop.nbtRaw(), warnReporter, loc, "LegacyNBT");
                if (tag != null) {
                    stack.setNbt(tag);
                }
            }
            net.minecraft.entity.ItemEntity entity = new net.minecraft.entity.ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            world.spawnEntity(entity);
        } else if (entry instanceof DropEntryEntity entityDrop) {
            Identifier id = Identifier.tryParse(entityDrop.entityId());
            if (id == null || !Registry.ENTITY_TYPE.containsId(id)) {
                warnReporter.warn("LegacyEntityId", "missing entity " + entityDrop.entityId(), loc);
                return;
            }
            EntityType<?> type = Registry.ENTITY_TYPE.get(id);
            var entity = type.create(world);
            if (entity != null) {
                NbtCompound tag = LenientNbtParser.parseOrEmpty(entityDrop.nbtRaw(), warnReporter, loc, "LegacyNBT");
                if (tag == null) {
                    tag = new NbtCompound();
                }
                tag.putString("id", entityDrop.entityId());
                entity.readNbt(tag);
                entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, entity.getYaw(), entity.getPitch());
                world.spawnEntity(entity);
            }
        } else if (entry instanceof DropEntryCommand cmd) {
            ExecContext exec = new ExecContext(world, pos, null, world.getRandom(), loc, warnReporter);
            commandRunner.run(cmd.command(), exec);
        }
    }
}
