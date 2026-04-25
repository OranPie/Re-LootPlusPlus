package ie.orangep.reLootplusplus.lucky.item;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropEngine;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.loader.LuckyAddonLoader;
import ie.orangep.reLootplusplus.lucky.registry.LuckyRegistrar;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The Lucky Sword.
 *
 * <p>When it hits a mob, evaluates drops from {@code sword_drops.txt}
 * (falls back to {@code bow_drops.txt} if the addon has no sword-specific list).
 */
public final class NativeLuckySword extends SwordItem {

    public NativeLuckySword(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.postHit(stack, target, attacker);
        if (!attacker.world.isClient() && attacker.world instanceof ServerWorld serverWorld
            && attacker instanceof PlayerEntity player) {
            BlockPos pos = target.getBlockPos();
            SourceLoc loc = new SourceLoc("lucky", "lucky_sword_hit", pos.toShortString(), 0, "");
            LuckyDropContext ctx = new LuckyDropContext(
                serverWorld, pos, player, 0,
                RuntimeState.warnReporter(), loc
            );
            var drops = LuckyAddonLoader.getMergedSwordDrops();
            if (drops != null && !drops.isEmpty()) {
                LuckyDropEngine.evaluate(ctx, drops);
            }
        }
        return result;
    }
}
