package ie.orangep.reLootplusplus.lucky.drop.action;

import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.loader.LuckyAddonLoader;
import ie.orangep.reLootplusplus.lucky.loader.LuckyStructureEntry;
import ie.orangep.reLootplusplus.lucky.structure.LuckyStructReader;
import ie.orangep.reLootplusplus.lucky.structure.NbtStructureReader;
import ie.orangep.reLootplusplus.lucky.structure.ParsedStructure;
import ie.orangep.reLootplusplus.lucky.structure.SchematicReader;
import ie.orangep.reLootplusplus.lucky.structure.StructureFileLoader;
import ie.orangep.reLootplusplus.lucky.structure.StructurePlacer;
import ie.orangep.reLootplusplus.lucky.template.LuckyTemplateVars;
import ie.orangep.reLootplusplus.pack.AddonPack;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Handles {@code type=structure} Lucky drop lines.
 *
 * <p>Reads the structure file from the addon pack, parses it according to its format
 * (.schematic, .luckystruct, or .nbt), and places the blocks in the world at the drop position.
 */
public final class LuckyStructureDropAction {

    private LuckyStructureDropAction() {}

    public static void execute(LuckyDropLine drop, LuckyDropContext ctx) {
        String structId = drop.getString("ID");
        if (structId == null || structId.isBlank()) {
            ctx.warnReporter().warn("LuckyStructure", "structure drop missing ID attribute", ctx.sourceLoc());
            return;
        }

        LuckyTemplateVars.EvalContext evalCtx = new LuckyTemplateVars.EvalContext(
            ctx.world(), ctx.pos(), ctx.player(), ctx.world().getRandom()
        );
        structId = LuckyTemplateVars.evaluate(structId, evalCtx);

        LuckyStructureEntry entry = LuckyAddonLoader.getStructureById(structId);
        AddonPack pack = LuckyAddonLoader.getPackForStructure(structId);

        if (entry == null) {
            ctx.warnReporter().warn("LuckyStructure",
                "structure '" + structId + "' not found in structures.txt", ctx.sourceLoc());
            return;
        }
        if (pack == null) {
            ctx.warnReporter().warn("LuckyStructure",
                "structure '" + structId + "' has no pack reference", ctx.sourceLoc());
            return;
        }

        byte[] fileBytes = StructureFileLoader.loadStructureFile(
            pack, entry.file(), ctx.warnReporter(), ctx.sourceLoc());
        if (fileBytes == null) {
            ctx.warnReporter().warn("LuckyStructure",
                "structure file '" + entry.file() + "' not found in pack '" + pack.id() + "'",
                ctx.sourceLoc());
            return;
        }

        ParsedStructure structure;
        try {
            InputStream stream = new ByteArrayInputStream(fileBytes);
            String fileName = entry.file().toLowerCase();
            if (fileName.endsWith(".schematic")) {
                structure = SchematicReader.read(stream, ctx.warnReporter(), pack.id());
            } else if (fileName.endsWith(".luckystruct")) {
                structure = LuckyStructReader.read(stream, ctx.warnReporter(), pack.id());
            } else if (fileName.endsWith(".nbt")) {
                structure = NbtStructureReader.read(stream, ctx.warnReporter(), pack.id());
            } else {
                ctx.warnReporter().warn("LuckyStructure",
                    "unknown structure format: " + entry.file(), ctx.sourceLoc());
                return;
            }
        } catch (Exception e) {
            ctx.warnReporter().warn("LuckyStructure",
                "error parsing structure '" + structId + "': " + e.getMessage(), ctx.sourceLoc());
            return;
        }

        BlockPos placePos = resolvePos(drop, ctx, evalCtx);
        BlockRotation rotation = resolveRotation(drop, ctx, evalCtx);

        Log.trace("LuckyDrop", "Structure place: id={} file={} pos={} rotation={}", structId, entry.file(), placePos, rotation);
        StructurePlacer.place(structure, ctx.world(), placePos,
            entry.centerX(), entry.centerY(), entry.centerZ(),
            entry.blockMode(), rotation);

        // Place overlay structure if specified
        String overlayId = entry.overlayStruct();
        if (overlayId != null && !overlayId.isBlank()) {
            placeOverlay(overlayId, placePos, rotation, ctx, evalCtx);
        }
    }

    private static void placeOverlay(String overlayId, BlockPos placePos, BlockRotation rotation,
                                      LuckyDropContext ctx, LuckyTemplateVars.EvalContext evalCtx) {
        LuckyStructureEntry overlayEntry = LuckyAddonLoader.getStructureById(overlayId);
        AddonPack overlayPack = LuckyAddonLoader.getPackForStructure(overlayId);
        if (overlayEntry == null || overlayPack == null) return;

        byte[] overlayBytes = StructureFileLoader.loadStructureFile(
            overlayPack, overlayEntry.file(), ctx.warnReporter(), ctx.sourceLoc());
        if (overlayBytes == null) return;

        try {
            InputStream overlayStream = new ByteArrayInputStream(overlayBytes);
            ParsedStructure overlayStruct;
            String fn = overlayEntry.file().toLowerCase();
            if (fn.endsWith(".schematic")) {
                overlayStruct = SchematicReader.read(overlayStream, ctx.warnReporter(), overlayPack.id());
            } else if (fn.endsWith(".luckystruct")) {
                overlayStruct = LuckyStructReader.read(overlayStream, ctx.warnReporter(), overlayPack.id());
            } else if (fn.endsWith(".nbt")) {
                overlayStruct = NbtStructureReader.read(overlayStream, ctx.warnReporter(), overlayPack.id());
            } else {
                return;
            }
            StructurePlacer.place(overlayStruct, ctx.world(), placePos,
                overlayEntry.centerX(), overlayEntry.centerY(), overlayEntry.centerZ(),
                "overlay", rotation);
        } catch (Exception e) {
            ctx.warnReporter().warn("LuckyStructure",
                "overlay structure '" + overlayId + "' error: " + e.getMessage(), ctx.sourceLoc());
        }
    }

    private static BlockPos resolvePos(LuckyDropLine drop, LuckyDropContext ctx,
                                        LuckyTemplateVars.EvalContext evalCtx) {
        BlockPos base = ctx.pos();
        String posStr = drop.getString("pos");
        String posXStr = drop.getString("posX");
        String posYStr = drop.getString("posY");
        String posZStr = drop.getString("posZ");
        String posOffsetStr = drop.getString("posOffset");

        double bx = base.getX() + 0.5, by = base.getY(), bz = base.getZ() + 0.5;

        if (posStr != null && !posStr.isBlank()) {
            posStr = LuckyTemplateVars.evaluate(posStr, evalCtx);
            String[] parts = posStr.trim().split("[,\\s]+");
            if (parts.length >= 3) {
                try { bx = Double.parseDouble(parts[0]); } catch (NumberFormatException ignored) {}
                try { by = Double.parseDouble(parts[1]); } catch (NumberFormatException ignored) {}
                try { bz = Double.parseDouble(parts[2]); } catch (NumberFormatException ignored) {}
            }
        }
        if (posXStr != null) {
            bx = LuckyTemplateVars.evalArithmetic(LuckyTemplateVars.evaluate(posXStr, evalCtx), bx);
        }
        if (posYStr != null) {
            by = LuckyTemplateVars.evalArithmetic(LuckyTemplateVars.evaluate(posYStr, evalCtx), by);
        }
        if (posZStr != null) {
            bz = LuckyTemplateVars.evalArithmetic(LuckyTemplateVars.evaluate(posZStr, evalCtx), bz);
        }
        if (posOffsetStr != null && !posOffsetStr.isBlank()) {
            posOffsetStr = LuckyTemplateVars.evaluate(posOffsetStr, evalCtx);
            String[] parts = posOffsetStr.replaceAll("[(){}\\[\\]]", "").split("[,\\s]+");
            if (parts.length >= 3) {
                try { bx += Double.parseDouble(parts[0].trim()); } catch (NumberFormatException ignored) {}
                try { by += Double.parseDouble(parts[1].trim()); } catch (NumberFormatException ignored) {}
                try { bz += Double.parseDouble(parts[2].trim()); } catch (NumberFormatException ignored) {}
            }
        }

        return new BlockPos((int) Math.floor(bx), (int) Math.floor(by), (int) Math.floor(bz));
    }

    private static BlockRotation resolveRotation(LuckyDropLine drop, LuckyDropContext ctx,
                                                   LuckyTemplateVars.EvalContext evalCtx) {
        String rotStr = drop.getString("rotation");
        if (rotStr == null || rotStr.isBlank()) return BlockRotation.NONE;
        rotStr = LuckyTemplateVars.evaluate(rotStr, evalCtx);
        try {
            int deg = (int) Math.round(Double.parseDouble(rotStr.trim())) % 360;
            if (deg < 0) deg += 360;
            return switch (deg) {
                case 90 -> BlockRotation.CLOCKWISE_90;
                case 180 -> BlockRotation.CLOCKWISE_180;
                case 270 -> BlockRotation.COUNTERCLOCKWISE_90;
                default -> BlockRotation.NONE;
            };
        } catch (NumberFormatException e) {
            return BlockRotation.NONE;
        }
    }
}
