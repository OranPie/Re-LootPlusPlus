package ie.orangep.reLootplusplus.lucky.registry;

import ie.orangep.reLootplusplus.lucky.block.NativeLuckyBlock;
import ie.orangep.reLootplusplus.lucky.block.NativeLuckyBlockEntity;
import ie.orangep.reLootplusplus.lucky.block.NativeLuckyBlockItem;
import ie.orangep.reLootplusplus.lucky.crafting.LuckyLuckModifierRecipe;
import ie.orangep.reLootplusplus.lucky.entity.NativeLuckyProjectile;
import ie.orangep.reLootplusplus.lucky.entity.NativeThrownLuckyPotion;
import ie.orangep.reLootplusplus.lucky.item.NativeLuckyBow;
import ie.orangep.reLootplusplus.lucky.item.NativeLuckyPotion;
import ie.orangep.reLootplusplus.lucky.item.NativeLuckySword;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ToolMaterials;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/**
 * Registers all native Lucky Block content under the {@code lucky:} namespace.
 *
 * <p>Called from {@code Bootstrap.java} in Phase 4 (registration phase),
 * after the dry-run check, before hook installation.
 *
 * <p><b>Important:</b> All fields are initialized via static class loading triggered
 * by the first call to {@link #register()}. They must not be accessed before that.
 */
public final class LuckyRegistrar {

    // -----------------------------------------------------------------------
    // Registry IDs
    // -----------------------------------------------------------------------

    private static final String NAMESPACE = "lucky";

    public static final Identifier LUCKY_BLOCK_ID       = new Identifier(NAMESPACE, "lucky_block");
    public static final Identifier LUCKY_SWORD_ID       = new Identifier(NAMESPACE, "lucky_sword");
    public static final Identifier LUCKY_BOW_ID         = new Identifier(NAMESPACE, "lucky_bow");
    public static final Identifier LUCKY_POTION_ID      = new Identifier(NAMESPACE, "lucky_potion");
    public static final Identifier LUCKY_PROJECTILE_ID  = new Identifier(NAMESPACE, "lucky_projectile");
    public static final Identifier LUCKY_THROWN_POTION_ID = new Identifier(NAMESPACE, "thrown_lucky_potion");

    // -----------------------------------------------------------------------
    // Registered objects (filled by register())
    // -----------------------------------------------------------------------

    public static NativeLuckyBlock LUCKY_BLOCK;
    public static NativeLuckyBlockItem LUCKY_BLOCK_ITEM;
    public static NativeLuckySword LUCKY_SWORD_ITEM;
    public static NativeLuckyBow LUCKY_BOW_ITEM;
    public static NativeLuckyPotion LUCKY_POTION_ITEM;

    public static BlockEntityType<NativeLuckyBlockEntity> LUCKY_BLOCK_ENTITY_TYPE;
    public static EntityType<NativeLuckyProjectile> LUCKY_PROJECTILE_TYPE;
    public static EntityType<NativeThrownLuckyPotion> THROWN_LUCKY_POTION_TYPE;

    private static boolean registered = false;

    private LuckyRegistrar() {}

    /**
     * Registers all Lucky Block content. Must be called exactly once during bootstrap Phase 4.
     */
    public static synchronized void register() {
        if (registered) return;
        registered = true;

        // Block
        LUCKY_BLOCK = Registry.register(
            Registry.BLOCK,
            LUCKY_BLOCK_ID,
            new NativeLuckyBlock(AbstractBlock.Settings.of(
                net.minecraft.block.Material.WOOD, MapColor.YELLOW
            ).strength(0.5f, 10.0f).sounds(BlockSoundGroup.WOOD))
        );

        // Block entity type — use a holder array to avoid self-reference in lambda
        @SuppressWarnings("unchecked")
        BlockEntityType<NativeLuckyBlockEntity>[] typeHolder = new BlockEntityType[1];
        typeHolder[0] = Registry.register(
            Registry.BLOCK_ENTITY_TYPE,
            LUCKY_BLOCK_ID,
            FabricBlockEntityTypeBuilder.<NativeLuckyBlockEntity>create(
                (pos, state) -> new NativeLuckyBlockEntity(typeHolder[0], pos, state),
                LUCKY_BLOCK
            ).build()
        );
        LUCKY_BLOCK_ENTITY_TYPE = typeHolder[0];

        // Block item
        LUCKY_BLOCK_ITEM = Registry.register(
            Registry.ITEM,
            LUCKY_BLOCK_ID,
            new NativeLuckyBlockItem(LUCKY_BLOCK, new Item.Settings().group(ItemGroup.DECORATIONS))
        );

        // Sword
        LUCKY_SWORD_ITEM = Registry.register(
            Registry.ITEM,
            LUCKY_SWORD_ID,
            new NativeLuckySword(ToolMaterials.GOLD, 3, -2.4f,
                new Item.Settings().group(ItemGroup.COMBAT))
        );

        // Bow
        LUCKY_BOW_ITEM = Registry.register(
            Registry.ITEM,
            LUCKY_BOW_ID,
            new NativeLuckyBow(new Item.Settings().maxDamage(384).group(ItemGroup.COMBAT))
        );

        // Potion
        LUCKY_POTION_ITEM = Registry.register(
            Registry.ITEM,
            LUCKY_POTION_ID,
            new NativeLuckyPotion(new Item.Settings().maxCount(16).group(ItemGroup.BREWING))
        );

        // Projectile entity (Lucky arrow)
        LUCKY_PROJECTILE_TYPE = Registry.register(
            Registry.ENTITY_TYPE,
            LUCKY_PROJECTILE_ID,
            EntityType.Builder.<NativeLuckyProjectile>create(NativeLuckyProjectile::new, SpawnGroup.MISC)
                .setDimensions(0.5f, 0.5f)
                .trackingTickInterval(20)
                .build(LUCKY_PROJECTILE_ID.toString())
        );

        // Thrown Lucky Potion entity
        THROWN_LUCKY_POTION_TYPE = Registry.register(
            Registry.ENTITY_TYPE,
            LUCKY_THROWN_POTION_ID,
            EntityType.Builder.<NativeThrownLuckyPotion>create(NativeThrownLuckyPotion::new, SpawnGroup.MISC)
                .setDimensions(0.25f, 0.25f)
                .trackingTickInterval(10)
                .build(LUCKY_THROWN_POTION_ID.toString())
        );

        // Luck modifier crafting recipe serializer
        Registry.register(Registry.RECIPE_SERIALIZER,
            LuckyLuckModifierRecipe.ID, LuckyLuckModifierRecipe.SERIALIZER);
    }
}
