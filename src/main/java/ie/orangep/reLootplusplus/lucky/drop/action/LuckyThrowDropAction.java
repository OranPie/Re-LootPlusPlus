package ie.orangep.reLootplusplus.lucky.drop.action;

import ie.orangep.reLootplusplus.legacy.mapping.LegacyEntityIdFixer;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.template.LuckyTemplateVars;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;

/**
 * Executes {@code type=throw} / {@code type=throwable} Lucky drops.
 *
 * <p>Throws an item entity from the drop position with an initial velocity.
 * The velocity is read from {@code NBTTag=(Motion=[dx,dy,dz])} — the same
 * NBT path written by {@code #bowMotion} and {@code #motionFromDirection(...)}.
 *
 * <p>If the {@code ID} resolves to an entity type rather than an item type,
 * execution is delegated to {@link LuckyEntityDropAction}.
 */
public final class LuckyThrowDropAction {

    private LuckyThrowDropAction() {}

    public static void execute(LuckyDropLine drop, LuckyDropContext ctx) {
        LuckyTemplateVars.EvalContext evalCtx = new LuckyTemplateVars.EvalContext(
            ctx.world(), ctx.pos(), ctx.player(), ctx.world().getRandom()
        );

        String rawId = drop.rawId();
        if (rawId == null || rawId.isBlank()) {
            ctx.warnReporter().warn("LuckyThrowDrop", "missing ID attribute", ctx.sourceLoc());
            return;
        }
        rawId = LuckyTemplateVars.evaluate(rawId, evalCtx);

        // Try item ID first
        String normalizedItemId = LegacyEntityIdFixer.normalizeItemId(rawId, ctx.warnReporter(), ctx.sourceLoc().toString());
        if (normalizedItemId == null) normalizedItemId = rawId;
        Identifier itemId = Identifier.tryParse(normalizedItemId);
        boolean isItem = itemId != null && Registry.ITEM.containsId(itemId);

        if (isItem) {
            throwItem(drop, ctx, evalCtx, itemId);
        } else {
            // ID is an entity type — delegate to entity action
            LuckyEntityDropAction.execute(drop, ctx);
        }
    }

    private static void throwItem(LuckyDropLine drop, LuckyDropContext ctx,
                                   LuckyTemplateVars.EvalContext evalCtx, Identifier itemId) {
        int amount = parseIntAttr(drop, "amount", evalCtx, 1);
        ItemStack stack = new ItemStack(Registry.ITEM.get(itemId), Math.max(1, amount));

        // Apply item NBT from NBTTag (excluding Motion which belongs to the entity)
        NbtCompound nbtFull = LuckyAttrToNbt.resolveNbtTag(drop, evalCtx);
        if (nbtFull != null && !nbtFull.isEmpty()) {
            NbtCompound itemNbt = stack.getOrCreateNbt();
            for (String key : nbtFull.getKeys()) {
                if (!"Motion".equals(key) && !"motion".equals(key)) {
                    itemNbt.put(key, nbtFull.get(key));
                }
            }
            if (!itemNbt.isEmpty()) stack.setNbt(itemNbt);
        }

        // Compute throw position (full entity-style resolution)
        Vec3d pos = resolvePos(drop, ctx, evalCtx);

        // Extract velocity from NBTTag.Motion
        Vec3d velocity = extractMotion(nbtFull);

        ItemEntity entity = new ItemEntity(ctx.world(), pos.x, pos.y, pos.z, stack);
        entity.setVelocity(velocity);
        ctx.world().spawnEntity(entity);
    }

    /** Extracts {@code Motion=[dx,dy,dz]} from an NbtCompound as a {@link Vec3d}. */
    static Vec3d extractMotion(NbtCompound nbt) {
        if (nbt == null) return Vec3d.ZERO;
        NbtElement motionEl = nbt.get("Motion");
        if (!(motionEl instanceof NbtList motionList) || motionList.size() < 3) return Vec3d.ZERO;
        try {
            double mx = ((AbstractNbtNumber) motionList.get(0)).doubleValue();
            double my = ((AbstractNbtNumber) motionList.get(1)).doubleValue();
            double mz = ((AbstractNbtNumber) motionList.get(2)).doubleValue();
            return new Vec3d(mx, my, mz);
        } catch (ClassCastException ignored) {
            return Vec3d.ZERO;
        }
    }

    private static Vec3d resolvePos(LuckyDropLine drop, LuckyDropContext ctx,
                                     LuckyTemplateVars.EvalContext evalCtx) {
        double bx = ctx.pos().getX() + 0.5;
        double by = ctx.pos().getY() + 0.5;
        double bz = ctx.pos().getZ() + 0.5;

        String posStr = drop.getString("pos");
        if (posStr != null && !posStr.isBlank()) {
            posStr = LuckyTemplateVars.evaluate(posStr, evalCtx);
            String[] parts = posStr.trim().split("\\s+");
            if (parts.length >= 3) {
                try { bx = Double.parseDouble(parts[0]); } catch (NumberFormatException ignored) {}
                try { by = Double.parseDouble(parts[1]); } catch (NumberFormatException ignored) {}
                try { bz = Double.parseDouble(parts[2]); } catch (NumberFormatException ignored) {}
            }
        }

        String posXStr = drop.getString("posX");
        String posYStr = drop.getString("posY");
        String posZStr = drop.getString("posZ");
        if (posXStr != null) bx = LuckyTemplateVars.evalArithmetic(LuckyTemplateVars.evaluate(posXStr, evalCtx), bx);
        if (posYStr != null) by = LuckyTemplateVars.evalArithmetic(LuckyTemplateVars.evaluate(posYStr, evalCtx), by);
        if (posZStr != null) bz = LuckyTemplateVars.evalArithmetic(LuckyTemplateVars.evaluate(posZStr, evalCtx), bz);

        String posOffsetStr = drop.getString("posOffset");
        if (posOffsetStr != null && !posOffsetStr.isBlank()) {
            posOffsetStr = LuckyTemplateVars.evaluate(posOffsetStr, evalCtx);
            double[] off = parsePosOffset(posOffsetStr);
            bx += off[0]; by += off[1]; bz += off[2];
        }

        String posOffsetYStr = drop.getString("posOffsetY");
        if (posOffsetYStr != null && !posOffsetYStr.isBlank()) {
            posOffsetYStr = LuckyTemplateVars.evaluate(posOffsetYStr, evalCtx);
            by += LuckyTemplateVars.evalArithmetic(posOffsetYStr, 0);
        }

        return new Vec3d(bx, by, bz);
    }

    private static double[] parsePosOffset(String s) {
        s = s.trim().replaceAll("[(){}]", "");
        String[] parts = s.contains(",") ? s.split(",") : s.split("\\s+");
        double dx = 0, dy = 0, dz = 0;
        try { if (parts.length > 0) dx = Double.parseDouble(parts[0].trim()); } catch (NumberFormatException ignored) {}
        try { if (parts.length > 1) dy = Double.parseDouble(parts[1].trim()); } catch (NumberFormatException ignored) {}
        try { if (parts.length > 2) dz = Double.parseDouble(parts[2].trim()); } catch (NumberFormatException ignored) {}
        return new double[]{dx, dy, dz};
    }

    private static int parseIntAttr(LuckyDropLine drop, String key, LuckyTemplateVars.EvalContext evalCtx, int fallback) {
        String s = drop.getString(key);
        if (s == null) return fallback;
        s = LuckyTemplateVars.evaluate(s, evalCtx);
        try { return (int) Math.round(LuckyTemplateVars.evalArithmetic(s, fallback)); }
        catch (Exception ignored) { return fallback; }
    }
}
