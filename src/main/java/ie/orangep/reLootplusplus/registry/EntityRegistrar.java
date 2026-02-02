package ie.orangep.reLootplusplus.registry;

import ie.orangep.reLootplusplus.content.entity.LootThrownItemEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public final class EntityRegistrar {
    public static final Identifier THROWN_ID = new Identifier("re-lootplusplus", "loot_thrown");
    public static final EntityType<LootThrownItemEntity> THROWN_ENTITY = Registry.register(
        Registry.ENTITY_TYPE,
        THROWN_ID,
        EntityType.Builder.<LootThrownItemEntity>create(LootThrownItemEntity::new, SpawnGroup.MISC)
            .setDimensions(0.25f, 0.25f)
            .trackingTickInterval(10)
            .build(THROWN_ID.toString())
    );

    private EntityRegistrar() {
    }
}
