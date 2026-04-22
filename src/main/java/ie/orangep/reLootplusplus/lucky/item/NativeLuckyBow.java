package ie.orangep.reLootplusplus.lucky.item;

import ie.orangep.reLootplusplus.lucky.entity.NativeLuckyProjectile;
import ie.orangep.reLootplusplus.lucky.registry.LuckyRegistrar;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/**
 * The Lucky Bow.
 *
 * <p>On release, fires a {@link NativeLuckyProjectile} instead of a normal arrow.
 */
public final class NativeLuckyBow extends BowItem {

    public NativeLuckyBow(Settings settings) {
        super(settings);
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof PlayerEntity player)) return;

        int useTicks = getMaxUseTime(stack) - remainingUseTicks;
        float pullProgress = BowItem.getPullProgress(useTicks);
        if (pullProgress < 0.1f) return;

        if (!world.isClient()) {
            NativeLuckyProjectile projectile = new NativeLuckyProjectile(world, player, 0);
            projectile.setVelocity(player, player.getPitch(), player.getYaw(), 0f, pullProgress * 3.0f, 1.0f);
            if (pullProgress == 1.0f) {
                projectile.setCritical(true);
            }
            world.spawnEntity(projectile);
        }

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS,
            1.0f, 1.0f / (world.getRandom().nextFloat() * 0.4f + 1.2f) + pullProgress * 0.5f);

        if (!player.getAbilities().creativeMode) {
            stack.damage(1, player, p -> p.sendToolBreakStatus(p.getActiveHand()));
        }
    }
}
