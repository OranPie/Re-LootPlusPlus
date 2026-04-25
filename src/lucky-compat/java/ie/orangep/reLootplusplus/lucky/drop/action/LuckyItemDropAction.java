package ie.orangep.reLootplusplus.lucky.drop.action;

import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.legacy.mapping.LegacyEntityIdFixer;
import ie.orangep.reLootplusplus.lucky.attr.LuckyAttr;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.template.LuckyTemplateVars;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.HashMap;
import java.util.Map;

/**
 * Executes {@code type=item} (or implicit item type) Lucky drops.
 *
 * <p>Spawns an item entity at the drop position.
 */
public final class LuckyItemDropAction {

    private LuckyItemDropAction() {}

    public static void execute(LuckyDropLine drop, LuckyDropContext ctx) {
        LuckyTemplateVars.EvalContext evalCtx = new LuckyTemplateVars.EvalContext(
            ctx.world(), ctx.pos(), ctx.player(), ctx.world().getRandom()
        );

        String rawId = drop.rawId();
        if (rawId == null || rawId.isBlank()) {
            ctx.warnReporter().warn("LuckyItemDrop", "missing item id", ctx.sourceLoc());
            return;
        }
        rawId = LuckyTemplateVars.evaluate(rawId, evalCtx);

        // Normalize legacy item ID
        String normalizedId = LegacyEntityIdFixer.normalizeItemId(rawId, ctx.warnReporter(), ctx.sourceLoc().toString());
        if (normalizedId == null) normalizedId = rawId;
        Log.trace("LuckyDrop", "Item: raw={} → id={}", rawId, normalizedId);
        Identifier id = Identifier.tryParse(normalizedId);
        if (id == null || !Registry.ITEM.containsId(id)) {
            ctx.warnReporter().warn("LuckyItemDrop", "unknown item " + rawId, ctx.sourceLoc());
            return;
        }

        // Amount
        int amount = parseAmount(drop, evalCtx);
        ItemStack stack = new ItemStack(Registry.ITEM.get(id), Math.max(1, amount));

        // NBT — handles both DictAttr and StringAttr NBTTag values
        NbtCompound nbtFromDrop = LuckyAttrToNbt.resolveNbtTag(drop, evalCtx);
        if (nbtFromDrop != null && !nbtFromDrop.isEmpty()) {
            NbtCompound existing = stack.getOrCreateNbt();
            for (String key : nbtFromDrop.getKeys()) existing.put(key, nbtFromDrop.get(key));
            stack.setNbt(existing);
        }

        // Inline enchantments via ench= (legacy)
        LuckyAttr enchAttr = drop.attrs().get("ench");
        if (enchAttr == null) enchAttr = drop.attrs().get("Ench");
        if (enchAttr instanceof LuckyAttr.ListAttr enchList) {
            applyEnchantments(stack, enchList, ctx);
        }

        // Damage/meta (legacy)
        String damageStr = drop.getString("damage");
        if (damageStr == null) damageStr = drop.getString("Damage");
        if (damageStr == null) damageStr = drop.getString("meta");
        if (damageStr != null && !damageStr.isBlank()) {
            try {
                int dmg = Integer.parseInt(damageStr.trim());
                if (dmg > 0) stack.setDamage(dmg);
            } catch (NumberFormatException ignored) {}
        }

        // Display name
        String displayName = resolveDisplayName(drop, evalCtx);
        if (displayName != null) {
            stack.setCustomName(net.minecraft.text.Text.of(displayName));
        }

        // Spawn item entity
        net.minecraft.entity.ItemEntity entity = new net.minecraft.entity.ItemEntity(
            ctx.world(),
            ctx.pos().getX() + 0.5,
            ctx.pos().getY() + 0.5,
            ctx.pos().getZ() + 0.5,
            stack
        );
        ctx.world().spawnEntity(entity);
        Log.trace("LuckyDrop", "Item spawned: {} x{} @ {}", normalizedId, stack.getCount(), ctx.pos());
    }

    private static int parseAmount(LuckyDropLine drop, LuckyTemplateVars.EvalContext evalCtx) {
        String amountStr = drop.getString("amount");
        if (amountStr == null) amountStr = drop.getString("Amount");
        if (amountStr == null) return 1;
        amountStr = LuckyTemplateVars.evaluate(amountStr, evalCtx);
        try {
            return Integer.parseInt(amountStr.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static String resolveDisplayName(LuckyDropLine drop, LuckyTemplateVars.EvalContext evalCtx) {
        // Check NBTTag display name
        LuckyAttr nbtAttr = drop.attrs().get("NBTTag");
        if (nbtAttr instanceof LuckyAttr.DictAttr nbtDict) {
            LuckyAttr displayAttr = nbtDict.get("display");
            if (displayAttr instanceof LuckyAttr.DictAttr displayDict) {
                String name = displayDict.getString("Name");
                if (name != null) return LuckyTemplateVars.evaluate(name, evalCtx);
            }
        }
        return null;
    }

    private static void applyEnchantments(ItemStack stack, LuckyAttr.ListAttr enchList, LuckyDropContext ctx) {
        for (LuckyAttr item : enchList.items()) {
            if (!(item instanceof LuckyAttr.DictAttr enchDict)) continue;
            String idStr = enchDict.getString("id");
            String lvlStr = enchDict.getString("lvl");
            if (idStr == null) continue;
            int level = 1;
            if (lvlStr != null) {
                try { level = Integer.parseInt(lvlStr.trim()); } catch (NumberFormatException ignored) {}
            }
            try {
                int enchId = Integer.parseInt(idStr.trim());
                // Legacy numeric enchantment id — skip, too complex for now
            } catch (NumberFormatException e) {
                Identifier enchIdentifier = Identifier.tryParse(idStr.trim());
                if (enchIdentifier != null && Registry.ENCHANTMENT.containsId(enchIdentifier)) {
                    Enchantment ench = Registry.ENCHANTMENT.get(enchIdentifier);
                    if (ench != null) {
                        Map<Enchantment, Integer> existing = EnchantmentHelper.get(stack);
                        existing.put(ench, level);
                        EnchantmentHelper.set(existing, stack);
                    }
                }
            }
        }
    }
}
