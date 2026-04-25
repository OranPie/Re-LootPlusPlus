package ie.orangep.reLootplusplus.lucky.structure;

import java.util.List;

/** A structure parsed from any format (.schematic, .luckystruct, .nbt). */
public record ParsedStructure(int width, int height, int length, List<StructureBlock> blocks) {}
