package ie.orangep.reLootplusplus.registry;

import ie.orangep.reLootplusplus.config.model.general.CreativeMenuEntry;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.legacy.nbt.LenientNbtParser;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CreativeMenuRegistrar {
    public void register(List<CreativeMenuEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        Map<String, List<CreativeMenuEntry>> byCategory = new HashMap<>();
        for (CreativeMenuEntry entry : entries) {
            byCategory.computeIfAbsent(entry.category(), key -> new ArrayList<>()).add(entry);
        }
        for (Map.Entry<String, List<CreativeMenuEntry>> entry : byCategory.entrySet()) {
            String category = entry.getKey();
            List<CreativeMenuEntry> items = entry.getValue();
            Identifier groupId = new Identifier("relootplusplus", slug(category));
            ItemStack icon = firstIcon(items);
            ItemGroup group = FabricItemGroupBuilder.create(groupId)
                .icon(() -> icon)
                .appendItems(stacks -> appendItems(stacks, items))
                .build();
            Log.LOGGER.info("Registered creative tab {} ({})", category, group.getName());
        }
    }

    private ItemStack firstIcon(List<CreativeMenuEntry> entries) {
        for (CreativeMenuEntry entry : entries) {
            ItemStack stack = buildStack(entry);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return new ItemStack(net.minecraft.item.Items.CHEST);
    }

    private void appendItems(List<ItemStack> stacks, List<CreativeMenuEntry> entries) {
        for (CreativeMenuEntry entry : entries) {
            ItemStack stack = buildStack(entry);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
    }

    private ItemStack buildStack(CreativeMenuEntry entry) {
        if (entry == null || entry.itemId() == null) {
            return ItemStack.EMPTY;
        }
        Identifier id = Identifier.tryParse(entry.itemId());
        if (id == null || !Registry.ITEM.containsId(id)) {
            Log.warn("[LootPP-Legacy] Creative menu missing item {}", entry.itemId());
            return ItemStack.EMPTY;
        }
        Item item = Registry.ITEM.get(id);
        ItemStack stack = new ItemStack(item);
        NbtCompound nbt = null;
        if (entry.nbtRaw() != null && !entry.nbtRaw().isBlank() && !"{}".equals(entry.nbtRaw().trim())) {
            nbt = LenientNbtParser.parseOrNull(entry.nbtRaw(), null, entry.sourceLoc(), "LegacyNBT");
        }
        if (entry.meta() > 0) {
            if (nbt == null) {
                nbt = new NbtCompound();
            }
            nbt.putInt("Damage", entry.meta());
        }
        if (nbt != null) {
            stack.setNbt(nbt);
        }
        return stack;
    }

    private String slug(String category) {
        String base = category == null ? "legacy" : category.trim().toLowerCase(Locale.ROOT);
        if (base.isEmpty()) {
            base = "legacy";
        }
        StringBuilder out = new StringBuilder(base.length());
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                out.append(c);
            } else if (c == ' ') {
                out.append('_');
            } else {
                out.append('_');
            }
        }
        return out.toString();
    }
}
