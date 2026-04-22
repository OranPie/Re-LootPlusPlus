package ie.orangep.reLootplusplus.lucky.drop.action;

import ie.orangep.reLootplusplus.legacy.mapping.LegacyEntityIdFixer;
import ie.orangep.reLootplusplus.legacy.nbt.LenientNbtParser;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.template.LuckyTemplateVars;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;

/**
 * Executes {@code type=block} Lucky drops.
 *
 * <p>Places a block at a position relative to the drop origin. Optionally applies
 * a tile entity NBT tag.
 */
public final class LuckyBlockDropAction {

    private LuckyBlockDropAction() {}

    public static void execute(LuckyDropLine drop, LuckyDropContext ctx) {
        LuckyTemplateVars.EvalContext evalCtx = new LuckyTemplateVars.EvalContext(
            ctx.world(), ctx.pos(), ctx.player(), ctx.world().getRandom()
        );

        String rawId = drop.rawId();
        if (rawId == null || rawId.isBlank()) {
            ctx.warnReporter().warn("LuckyBlockDrop", "missing block id", ctx.sourceLoc());
            return;
        }
        rawId = LuckyTemplateVars.evaluate(rawId, evalCtx);

        // Normalize block ID (blocks share the same namespace conventions as items)
        String normalizedId = LegacyEntityIdFixer.normalizeItemId(rawId, ctx.warnReporter(), ctx.sourceLoc().toString());
        if (normalizedId == null) normalizedId = rawId;
        Identifier id = Identifier.tryParse(normalizedId);
        if (id == null || !Registry.BLOCK.containsId(id)) {
            ctx.warnReporter().warn("LuckyBlockDrop", "unknown block " + rawId, ctx.sourceLoc());
            return;
        }
        Block block = Registry.BLOCK.get(id);

        // Position offset
        BlockPos targetPos = resolvePos(drop, ctx, evalCtx);

        // Set block
        BlockState state = block.getDefaultState();
        ctx.world().setBlockState(targetPos, state, Block.NOTIFY_ALL);

        // Tile entity NBT
        String tileNbtRaw = drop.getString("tileEntity");
        if (tileNbtRaw == null) tileNbtRaw = drop.getString("TileEntity");
        if (tileNbtRaw != null && !tileNbtRaw.isBlank()) {
            tileNbtRaw = LuckyTemplateVars.evaluate(tileNbtRaw, evalCtx);
            NbtCompound tag = LenientNbtParser.parseOrEmpty(tileNbtRaw, ctx.warnReporter(), ctx.sourceLoc(), "LuckyNBT");
            if (tag != null && !tag.isEmpty()) {
                BlockEntity be = ctx.world().getBlockEntity(targetPos);
                if (be != null) {
                    NbtCompound beNbt = be.createNbt();
                    for (String key : tag.getKeys()) beNbt.put(key, tag.get(key));
                    be.readNbt(beNbt);
                    be.markDirty();
                }
            }
        }
    }

    private static BlockPos resolvePos(LuckyDropLine drop, LuckyDropContext ctx, LuckyTemplateVars.EvalContext evalCtx) {
        BlockPos base = ctx.pos();
        String posStr = drop.getString("pos");
        if (posStr != null && !posStr.isBlank()) {
            posStr = LuckyTemplateVars.evaluate(posStr, evalCtx);
            String[] parts = posStr.split("[,\\s]+");
            if (parts.length >= 3) {
                try {
                    int dx = Integer.parseInt(parts[0].trim());
                    int dy = Integer.parseInt(parts[1].trim());
                    int dz = Integer.parseInt(parts[2].trim());
                    return base.add(dx, dy, dz);
                } catch (NumberFormatException ignored) {}
            }
        }
        return base;
    }
}
