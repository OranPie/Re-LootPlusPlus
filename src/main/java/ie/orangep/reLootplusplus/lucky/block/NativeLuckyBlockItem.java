package ie.orangep.reLootplusplus.lucky.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Item form of the Lucky Block.
 *
 * <p>When placed, copies {@code Luck} and {@code CustomDrops} from the item NBT
 * to the {@link NativeLuckyBlockEntity}.
 */
public final class NativeLuckyBlockItem extends BlockItem {

    public NativeLuckyBlockItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public Text getName(ItemStack stack) {
        int luck = NativeLuckyBlockEntity.getLuckFromItemStack(stack);
        if (luck >= 80)  return new TranslatableText("block.lucky.lucky_block.veryLucky");
        if (luck <= -80) return new TranslatableText("block.lucky.lucky_block.unlucky");
        return super.getName(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        int luck = NativeLuckyBlockEntity.getLuckFromItemStack(stack);

        // Luck value line: colored +N or -N
        Formatting luckColor = luck > 0 ? Formatting.GREEN : luck < 0 ? Formatting.RED : Formatting.GRAY;
        String sign = luck > 0 ? "+" : "";
        tooltip.add(new TranslatableText("item.lucky.lucky_block.luck")
            .append(new LiteralText(": " + sign + luck).formatted(luckColor)));

        // Visual luck bar: 10 segments spanning -100 to +100
        // luck=-100 → 0 filled, luck=0 → 5 filled, luck=+100 → 10 filled
        int barFilled = Math.round((luck + 100) * 10f / 200f);
        barFilled = Math.max(0, Math.min(10, barFilled));
        Formatting barColor = luck > 0 ? Formatting.GREEN : luck < 0 ? Formatting.RED : Formatting.GRAY;
        Text bar = new LiteralText("▓".repeat(barFilled)).formatted(barColor)
            .append(new LiteralText("░".repeat(10 - barFilled)).formatted(Formatting.DARK_GRAY));
        tooltip.add(bar);

        NbtCompound nbt = stack.getNbt();
        boolean hasCustomDrops = nbt != null
            && nbt.contains("CustomDrops", NbtCompound.LIST_TYPE)
            && !nbt.getList("CustomDrops", NbtCompound.STRING_TYPE).isEmpty();
        if (hasCustomDrops) {
            tooltip.add(new TranslatableText("item.lucky.lucky_block.customDrop").formatted(Formatting.GOLD));
        }
    }

    @Override
    protected boolean postPlacement(BlockPos pos, World world, PlayerEntity player,
                                    ItemStack stack, BlockState state) {
        boolean result = super.postPlacement(pos, world, player, stack, state);

        if (!world.isClient()) {
            NbtCompound nbt = stack.getNbt();
            if (nbt != null) {
                BlockEntity be = world.getBlockEntity(pos);
                if (be instanceof NativeLuckyBlockEntity luckyBe) {
                    if (nbt.contains("Luck")) {
                        luckyBe.setLuck(nbt.getInt("Luck"));
                    }
                    if (nbt.contains("CustomDrops", NbtCompound.LIST_TYPE)) {
                        NbtList list = nbt.getList("CustomDrops", NbtCompound.STRING_TYPE);
                        List<String> drops = new ArrayList<>(list.size());
                        for (int i = 0; i < list.size(); i++) {
                            drops.add(list.getString(i));
                        }
                        luckyBe.setCustomDrops(drops);
                    }
                }
            }
        }
        return result;
    }
}
