package ie.orangep.reLootplusplus.lucky.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NativeLuckyBlockEntity extends BlockEntity {

    private int luck = 0;
    private List<String> customDrops = null;

    public NativeLuckyBlockEntity(BlockEntityType<NativeLuckyBlockEntity> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public int getLuck() {
        return luck;
    }

    public void setLuck(int luck) {
        this.luck = Math.max(-100, Math.min(100, luck));
        markDirty();
    }

    public List<String> getCustomDrops() {
        return customDrops != null ? Collections.unmodifiableList(customDrops) : null;
    }

    public void setCustomDrops(List<String> drops) {
        this.customDrops = drops != null && !drops.isEmpty() ? new ArrayList<>(drops) : null;
        markDirty();
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.luck = nbt.contains("Luck") ? nbt.getInt("Luck") : 0;
        this.luck = Math.max(-100, Math.min(100, this.luck));
        if (nbt.contains("CustomDrops", NbtCompound.LIST_TYPE)) {
            NbtList list = nbt.getList("CustomDrops", NbtCompound.STRING_TYPE);
            this.customDrops = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                this.customDrops.add(list.getString(i));
            }
        } else {
            this.customDrops = null;
        }
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("Luck", this.luck);
        if (this.customDrops != null && !this.customDrops.isEmpty()) {
            NbtList list = new NbtList();
            for (String drop : customDrops) {
                list.add(NbtString.of(drop));
            }
            nbt.put("CustomDrops", list);
        }
    }

    // -----------------------------------------------------------------------
    // Static helpers for item-stack NBT (used by NativeLuckyBlockItem and crafting)
    // -----------------------------------------------------------------------

    /** Reads the {@code Luck} value from a lucky_block ItemStack's BlockEntityTag NBT. */
    public static int getLuckFromItemStack(ItemStack stack) {
        NbtCompound blockEntityTag = stack.getSubNbt("BlockEntityTag");
        if (blockEntityTag != null && blockEntityTag.contains("Luck")) {
            return Math.max(-100, Math.min(100, blockEntityTag.getInt("Luck")));
        }
        return 0;
    }

    /** Writes the {@code Luck} value into a lucky_block ItemStack's BlockEntityTag NBT. */
    public static void setLuckOnItemStack(ItemStack stack, int luck) {
        luck = Math.max(-100, Math.min(100, luck));
        NbtCompound blockEntityTag = stack.getOrCreateSubNbt("BlockEntityTag");
        blockEntityTag.putInt("Luck", luck);
    }
}
