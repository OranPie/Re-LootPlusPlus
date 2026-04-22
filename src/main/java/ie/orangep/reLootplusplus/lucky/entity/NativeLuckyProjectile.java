package ie.orangep.reLootplusplus.lucky.entity;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropEngine;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropParser;
import ie.orangep.reLootplusplus.lucky.loader.LuckyAddonLoader;
import ie.orangep.reLootplusplus.lucky.registry.LuckyRegistrar;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * The Lucky Projectile (arrow fired from Lucky Bow or {@code type=luckyprojectile} drop).
 *
 * <p>On landing, evaluates drops from {@code bow_drops.txt}, or inline {@code impact} drops
 * if those were provided when spawning.
 */
public class NativeLuckyProjectile extends ArrowEntity {

    private int luck = 0;
    /** Optional inline impact drop lines. When non-empty, these are evaluated on landing instead of bow_drops. */
    private List<String> impactDropLines = List.of();

    public NativeLuckyProjectile(EntityType<? extends ArrowEntity> type, World world) {
        super(type, world);
    }

    public NativeLuckyProjectile(World world, LivingEntity shooter, int luck) {
        super(LuckyRegistrar.LUCKY_PROJECTILE_TYPE, world);
        this.setOwner(shooter);
        this.luck = luck;
    }

    /** Sets inline impact drop lines to evaluate on collision (overrides bow_drops.txt). */
    public void setImpactDropLines(List<String> lines) {
        this.impactDropLines = lines != null ? new ArrayList<>(lines) : List.of();
    }

    @Override
    protected void onCollision(net.minecraft.util.hit.HitResult hitResult) {
        super.onCollision(hitResult);
        if (!world.isClient() && world instanceof ServerWorld serverWorld) {
            BlockPos pos = getBlockPos();
            SourceLoc loc = new SourceLoc("lucky", "lucky_projectile_hit", pos.toShortString(), 0, "");
            LuckyDropContext ctx = new LuckyDropContext(
                serverWorld, pos,
                getOwner() instanceof net.minecraft.entity.player.PlayerEntity p ? p : null,
                luck,
                RuntimeState.warnReporter(), loc
            );

            if (!impactDropLines.isEmpty()) {
                LuckyDropParser parser = new LuckyDropParser(RuntimeState.warnReporter(), loc);
                List<LuckyDropLine> drops = parser.parseLines(impactDropLines);
                LuckyDropEngine.evaluate(ctx, drops);
            } else {
                List<LuckyDropLine> drops = LuckyAddonLoader.getMergedBowDrops();
                if (drops == null || drops.isEmpty()) {
                    drops = LuckyAddonLoader.getMergedDrops();
                }
                LuckyDropEngine.evaluate(ctx, drops);
            }
            this.discard();
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Luck", luck);
        if (!impactDropLines.isEmpty()) {
            NbtList list = new NbtList();
            for (String line : impactDropLines) {
                list.add(NbtString.of(line));
            }
            nbt.put("ImpactDrops", list);
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.luck = nbt.contains("Luck") ? nbt.getInt("Luck") : 0;
        if (nbt.contains("ImpactDrops")) {
            NbtList list = nbt.getList("ImpactDrops", 8); // 8 = NbtString type
            List<String> lines = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                lines.add(list.getString(i));
            }
            this.impactDropLines = lines;
        }
    }

    @Override
    public ItemStack asItemStack() {
        return ItemStack.EMPTY;
    }
}
