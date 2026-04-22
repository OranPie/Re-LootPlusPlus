package ie.orangep.reLootplusplus.content.entity;

import ie.orangep.reLootplusplus.command.LegacyCommandRunner;
import ie.orangep.reLootplusplus.command.exec.ExecContext;
import ie.orangep.reLootplusplus.config.model.drop.DropEntry;
import ie.orangep.reLootplusplus.config.model.drop.DropEntryCommand;
import ie.orangep.reLootplusplus.config.model.drop.DropEntryEntity;
import ie.orangep.reLootplusplus.config.model.drop.DropEntryItem;
import ie.orangep.reLootplusplus.config.model.drop.DropGroup;
import ie.orangep.reLootplusplus.config.model.drop.DropRoller;
import ie.orangep.reLootplusplus.config.model.rule.ThrownDef;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.legacy.nbt.LenientNbtParser;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

public class LootThrownItemEntity extends ThrownItemEntity {
    private ThrownDef def;
    private LegacyCommandRunner commandRunner;
    private LegacyWarnReporter warnReporter;

    public LootThrownItemEntity(
        EntityType<? extends ThrownItemEntity> entityType,
        World world,
        ThrownDef def,
        LegacyCommandRunner commandRunner,
        LegacyWarnReporter warnReporter
    ) {
        super(entityType, world);
        this.def = def;
        this.commandRunner = commandRunner;
        this.warnReporter = warnReporter;
    }

    public LootThrownItemEntity(EntityType<? extends ThrownItemEntity> entityType, World world) {
        super(entityType, world);
        this.def = null;
        this.commandRunner = null;
        this.warnReporter = null;
    }

    public LootThrownItemEntity(
        EntityType<? extends ThrownItemEntity> entityType,
        World world,
        LivingEntity owner,
        ThrownDef def,
        LegacyCommandRunner commandRunner,
        LegacyWarnReporter warnReporter
    ) {
        super(entityType, owner, world);
        this.def = def;
        this.commandRunner = commandRunner;
        this.warnReporter = warnReporter;
    }

    /**
     * When deserialized (or created via {@code /summon}), restore the thrown def from the
     * {@code ItemThrown} NBT string key using the RuntimeState ThrownRegistry.
     */
    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (this.def == null && nbt.contains("ItemThrown", net.minecraft.nbt.NbtElement.STRING_TYPE)) {
            String itemId = nbt.getString("ItemThrown");
            var registry = RuntimeState.thrownRegistry();
            if (registry != null) {
                this.def = registry.get(itemId);
            }
        }
        if (this.commandRunner == null) {
            this.commandRunner = RuntimeState.commandRunner();
        }
        if (this.warnReporter == null) {
            this.warnReporter = RuntimeState.warnReporter();
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (this.def != null) {
            nbt.putString("ItemThrown", this.def.itemId());
        }
        return nbt;
    }

    @Override
    protected ItemStack getItem() {
        ItemStack tracked = super.getItem();
        if (!tracked.isEmpty()) {
            return tracked;
        }
        if (def == null) {
            return ItemStack.EMPTY;
        }
        net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(def.itemId());
        if (id == null || !Registry.ITEM.containsId(id)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(Registry.ITEM.get(id));
    }

    @Override
    protected Item getDefaultItem() {
        if (def == null) {
            return net.minecraft.item.Items.AIR;
        }
        net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(def.itemId());
        if (id == null || !Registry.ITEM.containsId(id)) {
            return net.minecraft.item.Items.AIR;
        }
        return Registry.ITEM.get(id);
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (getWorld().isClient()) {
            return;
        }
        if (def == null || commandRunner == null) {
            discard();
            return;
        }
        ServerWorld world = (ServerWorld) getWorld();
        DropGroup group = DropRoller.rollGroup(def.dropGroups(), world.getRandom());
        if (group != null) {
            for (DropEntry entry : group.entries()) {
                executeDrop(entry, world);
            }
        }
        discard();
    }

    private void executeDrop(DropEntry entry, ServerWorld world) {
        if (entry instanceof DropEntryItem itemDrop) {
            int count = itemDrop.minCount();
            if (itemDrop.maxCount() > itemDrop.minCount()) {
                count = itemDrop.minCount() + world.getRandom().nextInt(itemDrop.maxCount() - itemDrop.minCount() + 1);
            }
            net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(itemDrop.itemId());
            if (id == null || !Registry.ITEM.containsId(id)) {
                return;
            }
            ItemStack stack = new ItemStack(Registry.ITEM.get(id), count);
            if (itemDrop.nbtRaw() != null && !itemDrop.nbtRaw().isEmpty()) {
                NbtCompound tag = LenientNbtParser.parseOrEmpty(itemDrop.nbtRaw(), warnReporter, def.sourceLoc(), "LegacyNBT");
                if (tag != null) {
                    stack.setNbt(tag);
                }
            }
            world.spawnEntity(new net.minecraft.entity.ItemEntity(world, getX(), getY(), getZ(), stack));
        } else if (entry instanceof DropEntryEntity entityDrop) {
            net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(entityDrop.entityId());
            if (id != null && Registry.ENTITY_TYPE.containsId(id)) {
                EntityType<?> type = Registry.ENTITY_TYPE.get(id);
                var entity = type.create(world);
                if (entity != null) {
                    NbtCompound tag = LenientNbtParser.parseOrEmpty(entityDrop.nbtRaw(), warnReporter, def.sourceLoc(), "LegacyNBT");
                    if (tag == null) {
                        tag = new NbtCompound();
                    }
                    tag.putString("id", entityDrop.entityId());
                    entity.readNbt(tag);
                    entity.refreshPositionAndAngles(getX(), getY(), getZ(), entity.getYaw(), entity.getPitch());
                    world.spawnEntity(entity);
                }
            }
        } else if (entry instanceof DropEntryCommand cmd) {
            ExecContext exec = new ExecContext(world, getBlockPos(), this, world.getRandom(), def.sourceLoc(), warnReporter);
            commandRunner.run(cmd.command(), exec);
        }
    }
}
