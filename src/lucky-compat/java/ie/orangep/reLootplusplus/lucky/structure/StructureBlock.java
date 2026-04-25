package ie.orangep.reLootplusplus.lucky.structure;

import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** One block in a parsed structure. Coords are relative to structure origin (0,0,0). */
public record StructureBlock(
    int relX, int relY, int relZ,
    String blockId,
    Map<String, String> properties,
    @Nullable NbtCompound tileEntityNbt
) {}
