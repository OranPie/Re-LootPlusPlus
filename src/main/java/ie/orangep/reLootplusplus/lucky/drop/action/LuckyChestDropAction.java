package ie.orangep.reLootplusplus.lucky.drop.action;

import ie.orangep.reLootplusplus.legacy.mapping.LegacyEntityIdFixer;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.template.LuckyTemplateVars;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;

/**
 * Executes {@code type=chest} Lucky drops.
 *
 * <p>Places a container block (default {@code minecraft:chest}) at the drop position
 * and populates its block entity using the {@code NBTTag} attribute.
 * The {@code NBTTag} should contain an {@code Items=[...]} list following the
 * standard block entity NBT format.
 *
 * <p>Position resolution is the same as entity drops:
 * {@code pos=}, {@code posX/Y/Z}, {@code posOffset}, {@code posOffsetY}.
 */
public final class LuckyChestDropAction {

    private LuckyChestDropAction() {}

    public static void execute(LuckyDropLine drop, LuckyDropContext ctx) {
        LuckyTemplateVars.EvalContext evalCtx = new LuckyTemplateVars.EvalContext(
            ctx.world(), ctx.pos(), ctx.player(), ctx.world().getRandom()
        );

        // Default block is minecraft:chest; override via ID=
        String rawId = drop.rawId();
        if (rawId == null || rawId.isBlank()) {
            rawId = "minecraft:chest";
        } else {
            rawId = LuckyTemplateVars.evaluate(rawId, evalCtx);
        }

        String normalizedId = LegacyEntityIdFixer.normalizeItemId(rawId, ctx.warnReporter(), ctx.sourceLoc().toString());
        if (normalizedId == null) normalizedId = rawId;

        Identifier blockId = Identifier.tryParse(normalizedId);
        if (blockId == null || !Registry.BLOCK.containsId(blockId)) {
            ctx.warnReporter().warn("LuckyChestDrop", "unknown block '" + rawId + "'", ctx.sourceLoc());
            return;
        }

        Block block = Registry.BLOCK.get(blockId);
        BlockPos targetPos = resolvePos(drop, ctx, evalCtx);

        ctx.world().setBlockState(targetPos, block.getDefaultState(), Block.NOTIFY_ALL);

        // Apply NBTTag as block entity NBT (Items=[...], loot table ref, etc.)
        NbtCompound nbtFromDrop = LuckyAttrToNbt.resolveNbtTag(drop, evalCtx);
        if (nbtFromDrop != null && !nbtFromDrop.isEmpty()) {
            BlockEntity be = ctx.world().getBlockEntity(targetPos);
            if (be != null) {
                NbtCompound beNbt = be.createNbt();
                for (String key : nbtFromDrop.getKeys()) {
                    beNbt.put(key, nbtFromDrop.get(key));
                }
                be.readNbt(beNbt);
                be.markDirty();
            } else {
                ctx.warnReporter().warn("LuckyChestDrop",
                    "block entity not found at " + targetPos.toShortString() + " after placing " + normalizedId,
                    ctx.sourceLoc());
            }
        }
    }

    static BlockPos resolvePos(LuckyDropLine drop, LuckyDropContext ctx, LuckyTemplateVars.EvalContext evalCtx) {
        double bx = ctx.pos().getX() + 0.5;
        double by = ctx.pos().getY();
        double bz = ctx.pos().getZ() + 0.5;

        // pos= absolute coordinate override (e.g. pos=#bPos, pos=#pPos)
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

        return new BlockPos((int) Math.floor(bx), (int) Math.floor(by), (int) Math.floor(bz));
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
}
