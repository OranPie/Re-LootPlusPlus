package ie.orangep.reLootplusplus.lucky.structure;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads vanilla Minecraft .nbt structure files (gzip-compressed NBT). */
public final class NbtStructureReader {

    private NbtStructureReader() {}

    @SuppressWarnings("unchecked")
    public static ParsedStructure read(InputStream stream, LegacyWarnReporter reporter, String packId) throws IOException {
        NbtCompound root = NbtIo.readCompressed(stream);

        NbtList sizeList = root.getList("size", NbtElement.INT_TYPE);
        int sizeX = sizeList.size() > 0 ? sizeList.getInt(0) : 1;
        int sizeY = sizeList.size() > 1 ? sizeList.getInt(1) : 1;
        int sizeZ = sizeList.size() > 2 ? sizeList.getInt(2) : 1;

        NbtList paletteList = root.getList("palette", NbtElement.COMPOUND_TYPE);
        String[] paletteBlockIds = new String[paletteList.size()];
        Map<String, String>[] paletteProps = new Map[paletteList.size()];
        for (int i = 0; i < paletteList.size(); i++) {
            NbtCompound entry = paletteList.getCompound(i);
            String name = entry.getString("Name");
            Map<String, String> props = new LinkedHashMap<>();
            if (entry.contains("Properties", NbtElement.COMPOUND_TYPE)) {
                NbtCompound propsNbt = entry.getCompound("Properties");
                for (String key : propsNbt.getKeys()) {
                    props.put(key, propsNbt.getString(key));
                }
            }
            paletteBlockIds[i] = name;
            paletteProps[i] = Collections.unmodifiableMap(props);
        }

        NbtList blocksList = root.getList("blocks", NbtElement.COMPOUND_TYPE);
        List<StructureBlock> blocks = new ArrayList<>();
        for (int i = 0; i < blocksList.size(); i++) {
            NbtCompound block = blocksList.getCompound(i);
            int state = block.getInt("state");
            NbtList pos = block.getList("pos", NbtElement.INT_TYPE);
            if (pos.size() < 3) continue;
            int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2);

            if (state >= paletteBlockIds.length || state < 0) continue;
            String blockId = paletteBlockIds[state];
            Map<String, String> props = paletteProps[state];

            if ("minecraft:air".equals(blockId) || "minecraft:cave_air".equals(blockId)
                    || "minecraft:void_air".equals(blockId)) continue;

            NbtCompound nbt = block.contains("nbt", NbtElement.COMPOUND_TYPE) ? block.getCompound("nbt") : null;
            blocks.add(new StructureBlock(x, y, z, blockId, props, nbt));
        }

        return new ParsedStructure(sizeX, sizeY, sizeZ, Collections.unmodifiableList(blocks));
    }
}
