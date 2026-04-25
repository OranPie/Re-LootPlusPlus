package ie.orangep.reLootplusplus.lucky.drop.action;

import ie.orangep.reLootplusplus.lucky.drop.LuckyDropContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.template.LuckyTemplateVars;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;

/**
 * Executes {@code type=fill} Lucky drops.
 *
 * <p>Fills a rectangular region starting at the block position with the given block.
 * Supported attributes:
 * <ul>
 *   <li>{@code ID} — block id to fill with (default: air)</li>
 *   <li>{@code size=(w,h,d)} — dimensions of fill region</li>
 *   <li>{@code pos2=(x,y,z)} — alternative: absolute end corner</li>
 * </ul>
 * Maximum of 10,000 blocks are placed to prevent lag.
 */
public final class LuckyFillDropAction {

    private static final int MAX_FILL = 10_000;

    private LuckyFillDropAction() {}

    public static void execute(LuckyDropLine drop, LuckyDropContext ctx) {
        LuckyTemplateVars.EvalContext evalCtx = new LuckyTemplateVars.EvalContext(
            ctx.world(), ctx.pos(), ctx.player(), ctx.world().getRandom()
        );

        String blockId = drop.rawId();
        if (blockId == null || blockId.isBlank()) blockId = "air";
        blockId = LuckyTemplateVars.evaluate(blockId, evalCtx).trim();

        Block block = resolveBlock(blockId, ctx);
        BlockState state = block.getDefaultState();

        BlockPos start = computeStart(drop, ctx, evalCtx);
        BlockPos end = computeEnd(drop, start, evalCtx);

        int count = 0;
        for (int x = Math.min(start.getX(), end.getX()); x <= Math.max(start.getX(), end.getX()); x++) {
            for (int y = Math.min(start.getY(), end.getY()); y <= Math.max(start.getY(), end.getY()); y++) {
                for (int z = Math.min(start.getZ(), end.getZ()); z <= Math.max(start.getZ(), end.getZ()); z++) {
                    ctx.world().setBlockState(new BlockPos(x, y, z), state, Block.NOTIFY_LISTENERS);
                    if (++count >= MAX_FILL) return;
                }
            }
        }
    }

    private static Block resolveBlock(String blockId, LuckyDropContext ctx) {
        Identifier id = Identifier.tryParse(blockId);
        if (id == null) id = Identifier.tryParse("minecraft:" + blockId.toLowerCase());
        if (id != null && Registry.BLOCK.containsId(id)) return Registry.BLOCK.get(id);
        ctx.warnReporter().warn("LuckyFillDrop", "unknown block: " + blockId, ctx.sourceLoc());
        return Blocks.AIR;
    }

    private static BlockPos computeStart(LuckyDropLine drop, LuckyDropContext ctx,
                                          LuckyTemplateVars.EvalContext evalCtx) {
        BlockPos base = ctx.pos();
        String posOffsetStr = drop.getString("posOffset");
        if (posOffsetStr != null) {
            posOffsetStr = LuckyTemplateVars.evaluate(posOffsetStr, evalCtx);
            double[] off = parseTuple(posOffsetStr);
            base = base.add((int) off[0], (int) off[1], (int) off[2]);
        }
        return base;
    }

    private static BlockPos computeEnd(LuckyDropLine drop, BlockPos start,
                                        LuckyTemplateVars.EvalContext evalCtx) {
        // size=(w,h,d)
        String sizeStr = drop.getString("size");
        if (sizeStr != null && !sizeStr.isBlank()) {
            sizeStr = LuckyTemplateVars.evaluate(sizeStr, evalCtx);
            double[] s = parseTuple(sizeStr);
            int w = Math.max(1, (int) s[0]);
            int h = Math.max(1, (int) s[1]);
            int d = Math.max(1, (int) s[2]);
            return start.add(w - 1, h - 1, d - 1);
        }
        // pos2=(x,y,z)
        String pos2Str = drop.getString("pos2");
        if (pos2Str != null && !pos2Str.isBlank()) {
            pos2Str = LuckyTemplateVars.evaluate(pos2Str, evalCtx);
            double[] p = parseTuple(pos2Str);
            return new BlockPos((int) p[0], (int) p[1], (int) p[2]);
        }
        return start;
    }

    private static double[] parseTuple(String s) {
        if (s == null) return new double[]{0, 0, 0};
        s = s.trim().replaceAll("[(){}\\[\\]]", "");
        String[] parts = s.contains(",") ? s.split(",") : s.split("\\s+");
        double[] result = {0, 0, 0};
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            try { result[i] = Double.parseDouble(parts[i].trim()); } catch (NumberFormatException ignored) {}
        }
        return result;
    }
}
