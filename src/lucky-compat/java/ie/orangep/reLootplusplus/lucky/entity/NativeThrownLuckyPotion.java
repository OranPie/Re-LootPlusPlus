package ie.orangep.reLootplusplus.lucky.entity;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropEngine;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.loader.LuckyAddonLoader;
import ie.orangep.reLootplusplus.lucky.registry.LuckyRegistrar;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Thrown Lucky Potion entity.
 *
 * <p>On impact, evaluates drops from {@code potion_drops.txt}
 * (falls back to {@code bow_drops.txt} if no potion-specific list is loaded).
 */
public class NativeThrownLuckyPotion extends ThrownItemEntity {

    private int luck = 0;

    public NativeThrownLuckyPotion(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
    }

    public NativeThrownLuckyPotion(World world, LivingEntity owner, int luck) {
        super(LuckyRegistrar.THROWN_LUCKY_POTION_TYPE, owner, world);
        this.luck = luck;
    }

    @Override
    protected Item getDefaultItem() {
        return LuckyRegistrar.LUCKY_POTION_ITEM;
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!world.isClient() && world instanceof ServerWorld serverWorld) {
            BlockPos pos = getBlockPos();
            List<LuckyDropLine> drops = LuckyAddonLoader.getMergedPotionDrops();
            SourceLoc loc = new SourceLoc("lucky", "lucky_potion_impact", pos.toShortString(), 0, "");
            LuckyDropContext ctx = new LuckyDropContext(
                serverWorld, pos, null, luck,
                RuntimeState.warnReporter(), loc
            );
            LuckyDropEngine.evaluate(ctx, drops);
            this.discard();
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Luck", luck);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.luck = nbt.contains("Luck") ? nbt.getInt("Luck") : 0;
    }
}
