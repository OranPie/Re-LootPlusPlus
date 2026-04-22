package ie.orangep.reLootplusplus.lucky.item;

import ie.orangep.reLootplusplus.lucky.entity.NativeThrownLuckyPotion;
import ie.orangep.reLootplusplus.lucky.registry.LuckyRegistrar;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * The Lucky Potion item.
 *
 * <p>When used (right-clicked), throws a {@link NativeThrownLuckyPotion}.
 */
public final class NativeLuckyPotion extends Item {

    public NativeLuckyPotion(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        world.playSound(null, user.getX(), user.getY(), user.getZ(),
            SoundEvents.ENTITY_SPLASH_POTION_THROW, SoundCategory.PLAYERS,
            0.5f, 0.4f / (world.getRandom().nextFloat() * 0.4f + 0.8f));

        if (!world.isClient()) {
            NativeThrownLuckyPotion potion = new NativeThrownLuckyPotion(world, user, 0);
            potion.setItem(stack);
            potion.setVelocity(user, user.getPitch(), user.getYaw(), -20.0f, 0.5f, 1.0f);
            world.spawnEntity(potion);
        }

        user.incrementStat(Stats.USED.getOrCreateStat(this));
        if (!user.getAbilities().creativeMode) {
            stack.decrement(1);
        }
        return TypedActionResult.success(stack, world.isClient());
    }
}
