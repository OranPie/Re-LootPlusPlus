package ie.orangep.reLootplusplus.lucky.structure;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import ie.orangep.reLootplusplus.legacy.mapping.LegacyBlockIdMapper;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads MCEdit .schematic files (gzip-compressed NBT). */
public final class SchematicReader {

    private SchematicReader() {}

    public static ParsedStructure read(InputStream stream, LegacyWarnReporter reporter, String packId) throws IOException {
        NbtCompound root = NbtIo.readCompressed(stream);
        int width = root.getShort("Width") & 0xFFFF;
        int height = root.getShort("Height") & 0xFFFF;
        int length = root.getShort("Length") & 0xFFFF;
        byte[] blockIds = root.getByteArray("Blocks");
        byte[] data = root.getByteArray("Data");

        Map<String, NbtCompound> tileEntityMap = new HashMap<>();
        NbtList teList = root.getList("TileEntities", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < teList.size(); i++) {
            NbtCompound te = teList.getCompound(i);
            int tx = te.getInt("x");
            int ty = te.getInt("y");
            int tz = te.getInt("z");
            String legacyId = te.getString("id");
            String modernId = LegacyBlockIdMapper.mapTileEntityId(legacyId);
            if (!modernId.equals(legacyId)) {
                NbtCompound fixed = te.copy();
                fixed.putString("id", modernId);
                te = fixed;
            }
            tileEntityMap.put(tx + "," + ty + "," + tz, te);
        }

        List<StructureBlock> blocks = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int idx = (y * length + z) * width + x;
                    int blockId = (idx < blockIds.length) ? (blockIds[idx] & 0xFF) : 0;
                    int meta = (idx < data.length) ? (data[idx] & 0xF) : 0;

                    if (blockId == 0) continue;

                    LegacyBlockIdMapper.Result mapped;
                    try {
                        SourceLoc loc = new SourceLoc(packId, packId, ".schematic", y * length * width + z * width + x, "");
                        mapped = LegacyBlockIdMapper.map(blockId, meta, reporter, loc);
                    } catch (Exception e) {
                        reporter.warn("BlockId", "error mapping block id " + blockId + " at " + x + "," + y + "," + z + ": " + e.getMessage(),
                            new SourceLoc(packId, packId, ".schematic", 0, ""));
                        continue;
                    }

                    if ("minecraft:air".equals(mapped.blockId())) continue;

                    NbtCompound te = tileEntityMap.get(x + "," + y + "," + z);
                    blocks.add(new StructureBlock(x, y, z, mapped.blockId(), mapped.properties(), te));
                }
            }
        }
        return new ParsedStructure(width, height, length, Collections.unmodifiableList(blocks));
    }
}
