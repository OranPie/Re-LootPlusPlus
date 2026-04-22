package ie.orangep.reLootplusplus.registry;

import ie.orangep.reLootplusplus.config.model.general.CreativeMenuEntry;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.legacy.nbt.LenientNbtParser;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.minecraft.client.resource.language.I18n;
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
            Log.info("Registry", "Registered creative tab {} ({})", category, group.getName());
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
            Log.warn("Legacy", "Creative menu missing item {}", entry.itemId());
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
        if (shouldApplyFallbackName(id, stack)) {
            stack.setCustomName(new net.minecraft.text.LiteralText(fallbackName(id)));
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

    private boolean shouldApplyFallbackName(Identifier id, ItemStack stack) {
        if (id == null || stack == null || stack.isEmpty()) {
            return false;
        }
        String key = stack.getTranslationKey();
        if (key == null || key.isBlank()) {
            return false;
        }
        if (I18n.hasTranslation(key)) {
            return false;
        }
        return "lucky".equals(id.getNamespace());
    }

    private String fallbackName(Identifier id) {
        String path = id.getPath();
        if (path == null || path.isBlank()) {
            return id.toString();
        }
        String[] parts = path.replace('.', ' ').replace('_', ' ').replace('-', ' ').trim().split("\\s+");
        StringBuilder out = new StringBuilder(path.length());
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.toString();
    }
}
