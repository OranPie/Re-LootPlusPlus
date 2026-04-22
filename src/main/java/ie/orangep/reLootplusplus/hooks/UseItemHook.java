package ie.orangep.reLootplusplus.hooks;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.legacy.mapping.LegacyChestTypeMapper;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import ie.orangep.reLootplusplus.runtime.RuleEngine;
import ie.orangep.reLootplusplus.runtime.RuntimeContext;
import ie.orangep.reLootplusplus.runtime.trigger.TriggerType;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

public final class UseItemHook {
    private final RuleEngine ruleEngine;
    private final LegacyWarnReporter warnReporter;
    private final LegacyChestTypeMapper chestTypeMapper;

    public UseItemHook(RuleEngine ruleEngine, LegacyWarnReporter warnReporter) {
        this.ruleEngine = ruleEngine;
        this.warnReporter = warnReporter;
        this.chestTypeMapper = new LegacyChestTypeMapper(warnReporter);
    }

    public void install() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) {
                return TypedActionResult.pass(player.getStackInHand(hand));
            }
            var stack = player.getStackInHand(hand);
            TypedActionResult<ItemStack> lootChest = tryUseLootChest(player, (ServerWorld) world, hand, stack);
            if (lootChest != null) {
                return lootChest;
            }
            if (stack.isEmpty() || stack.isFood()) {
                return TypedActionResult.pass(stack);
            }
            RuntimeContext ctx = new RuntimeContext(world.getServer(), (ServerWorld) world, world.getRandom(), warnReporter);
            ruleEngine.executeForPlayer(ctx, player, TriggerType.RIGHT_CLICK);
            return TypedActionResult.pass(player.getStackInHand(hand));
        });
    }

    private TypedActionResult<ItemStack> tryUseLootChest(net.minecraft.entity.player.PlayerEntity player, ServerWorld world, Hand hand, ItemStack stack) {
        if (stack.isEmpty() || !stack.hasNbt()) {
            return null;
        }
        String chestType = stack.getNbt().getString("Type");
        if (chestType == null || chestType.isBlank()) {
            return null;
        }
        if (!stack.isOf(Blocks.CHEST.asItem())) {
            return null;
        }
        if (RuntimeState.chestLootRegistry() == null) {
            return null;
        }
        var lootTable = chestTypeMapper.resolve(chestType, null);
        if (lootTable == null) {
            Log.warn("Legacy", "LootChest unknown type {}", chestType);
            return TypedActionResult.pass(stack);
        }
        BlockHitResult hit = raycast(player);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return TypedActionResult.pass(stack);
        }
        BlockPos target = hit.getBlockPos().offset(hit.getSide());
        if (!world.getBlockState(target).isAir()) {
            return TypedActionResult.pass(stack);
        }
        BlockState state = Blocks.CHEST.getDefaultState();
        world.setBlockState(target, state);
        BlockEntity be = world.getBlockEntity(target);
        if (be instanceof ChestBlockEntity chest) {
            chest.setLootTable(lootTable, world.getRandom().nextLong());
            if (stack.hasCustomName()) {
                chest.setCustomName(stack.getName());
            }
            if (!player.getAbilities().creativeMode) {
                stack.decrement(1);
            }
            return new TypedActionResult<>(ActionResult.SUCCESS, stack);
        }
        return TypedActionResult.pass(stack);
    }

    private BlockHitResult raycast(net.minecraft.entity.player.PlayerEntity player) {
        return (BlockHitResult) player.raycast(5.0D, 0.0F, false);
    }
}
