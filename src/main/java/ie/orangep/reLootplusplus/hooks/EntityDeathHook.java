package ie.orangep.reLootplusplus.hooks;

import ie.orangep.reLootplusplus.command.LegacyCommandRunner;
import ie.orangep.reLootplusplus.command.exec.ExecContext;
import ie.orangep.reLootplusplus.config.model.drop.DropEntry;
import ie.orangep.reLootplusplus.config.model.drop.DropEntryCommand;
import ie.orangep.reLootplusplus.config.model.drop.DropEntryEntity;
import ie.orangep.reLootplusplus.config.model.drop.DropEntryItem;
import ie.orangep.reLootplusplus.config.model.drop.DropGroup;
import ie.orangep.reLootplusplus.config.model.drop.DropRoller;
import ie.orangep.reLootplusplus.config.model.rule.EntityDropRule;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.legacy.nbt.LenientNbtParser;
import ie.orangep.reLootplusplus.runtime.EntityDropRegistry;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public final class EntityDeathHook {
    private final EntityDropRegistry entityDropRegistry;
    private final LegacyWarnReporter warnReporter;
    private final LegacyCommandRunner commandRunner = new LegacyCommandRunner();

    public EntityDeathHook(ie.orangep.reLootplusplus.runtime.RuleEngine ruleEngine, EntityDropRegistry entityDropRegistry, LegacyWarnReporter warnReporter) {
        this.entityDropRegistry = entityDropRegistry;
        this.warnReporter = warnReporter;
    }

    public void install() {
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, attacker, killed) -> {
            applyEntityDrops(world, killed.getType(), killed.getBlockPos());
        });
    }

    private void applyEntityDrops(ServerWorld world, EntityType<?> type, net.minecraft.util.math.BlockPos pos) {
        String entityId = Registry.ENTITY_TYPE.getId(type).toString();
        for (EntityDropRule rule : entityDropRegistry.rules()) {
            if (!rule.entityId().equals(entityId)) {
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
                NbtCompound tag = LenientNbtParser.parseOrEmpty(itemDrop.nbtRaw(), warnReporter, loc, "LegacyNBT");
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
