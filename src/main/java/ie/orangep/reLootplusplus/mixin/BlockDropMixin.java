package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.config.model.rule.BlockDropRemoval;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.legacy.nbt.LenientNbtParser;
import ie.orangep.reLootplusplus.legacy.nbt.NbtPredicate;
import ie.orangep.reLootplusplus.runtime.BlockDropRegistry;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import net.minecraft.block.AbstractBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class BlockDropMixin {
    @Shadow public abstract net.minecraft.block.Block getBlock();

    @Inject(method = "getDroppedStacks(Lnet/minecraft/loot/context/LootContext$Builder;)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private void relootplusplus$filterDrops(LootContext.Builder builder, CallbackInfoReturnable<List<ItemStack>> cir) {
        BlockDropRegistry registry = RuntimeState.blockDropRegistry();
        if (registry == null || registry.removeRules().isEmpty()) {
            return;
        }
        List<ItemStack> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) {
            return;
        }
        String blockId = Registry.BLOCK.getId(getBlock()).toString();
        List<ItemStack> filtered = new ArrayList<>();
        for (ItemStack stack : original) {
            if (shouldRemove(stack, blockId, registry.removeRules(), RuntimeState.warnReporter())) {
                continue;
            }
            filtered.add(stack);
        }
        cir.setReturnValue(filtered);
    }

    private boolean shouldRemove(ItemStack stack, String blockId, List<BlockDropRemoval> removals, LegacyWarnReporter warnReporter) {
        for (BlockDropRemoval removal : removals) {
            if (!blockId.equals(removal.blockId())) {
                continue;
            }
            if (!itemMatches(removal.itemId(), stack)) {
                continue;
            }
            if (!nbtMatches(removal.nbtRaw(), stack, removal, warnReporter)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean itemMatches(String ruleItemId, ItemStack stack) {
        if (ruleItemId == null) {
            return false;
        }
        if ("any".equalsIgnoreCase(ruleItemId) || "all".equalsIgnoreCase(ruleItemId)) {
            return true;
        }
        Identifier id = Registry.ITEM.getId(stack.getItem());
        return ruleItemId.equals(id.toString());
    }

    private boolean nbtMatches(String raw, ItemStack stack, BlockDropRemoval removal, LegacyWarnReporter warnReporter) {
        if (raw == null || raw.isEmpty() || "{}".equals(raw)) {
            return true;
        }
        NbtCompound needle = LenientNbtParser.parseOrNull(raw, warnReporter, removal.sourceLoc(), "LegacyNBT");
        if (needle == null) {
            return false;
        }
        NbtCompound haystack = stack.getNbt();
        return NbtPredicate.matches(haystack, needle);
    }
}
