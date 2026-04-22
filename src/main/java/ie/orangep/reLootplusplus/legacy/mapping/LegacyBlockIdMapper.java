package ie.orangep.reLootplusplus.legacy.mapping;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class LegacyBlockIdMapper {

    public record Result(String blockId, Map<String, String> properties) {
        public static Result simple(String id) {
            return new Result(id, Map.of());
        }

        public static Result withProps(String id, String... kvPairs) {
            Map<String, String> m = new java.util.LinkedHashMap<>();
            for (int i = 0; i + 1 < kvPairs.length; i += 2) {
                m.put(kvPairs[i], kvPairs[i + 1]);
            }
            return new Result(id, Map.copyOf(m));
        }
    }

    private static final String[] DYE_COLOR = {
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    private static final Map<String, String> TILE_ENTITY_MAP = new HashMap<>();

    static {
        TILE_ENTITY_MAP.put("Chest", "minecraft:chest");
        TILE_ENTITY_MAP.put("Furnace", "minecraft:furnace");
        TILE_ENTITY_MAP.put("Beacon", "minecraft:beacon");
        TILE_ENTITY_MAP.put("EnchantTable", "minecraft:enchanting_table");
        TILE_ENTITY_MAP.put("Hopper", "minecraft:hopper");
        TILE_ENTITY_MAP.put("Dropper", "minecraft:dropper");
        TILE_ENTITY_MAP.put("Dispenser", "minecraft:dispenser");
        TILE_ENTITY_MAP.put("Cauldron", "minecraft:cauldron");
        TILE_ENTITY_MAP.put("Piston", "minecraft:piston");
        TILE_ENTITY_MAP.put("MobSpawner", "minecraft:mob_spawner");
        TILE_ENTITY_MAP.put("Sign", "minecraft:oak_sign");
        TILE_ENTITY_MAP.put("FlowerPot", "minecraft:flower_pot");
        TILE_ENTITY_MAP.put("Comparator", "minecraft:comparator");
        TILE_ENTITY_MAP.put("DaylightDetector", "minecraft:daylight_detector");
        TILE_ENTITY_MAP.put("CommandBlock", "minecraft:command_block");
        TILE_ENTITY_MAP.put("Trap", "minecraft:trapped_chest");
        TILE_ENTITY_MAP.put("Skull", "minecraft:skull");
        TILE_ENTITY_MAP.put("Banner", "minecraft:banner");
        TILE_ENTITY_MAP.put("Structure", "minecraft:structure_block");
        TILE_ENTITY_MAP.put("Music", "minecraft:note_block");
        TILE_ENTITY_MAP.put("Bed", "minecraft:bed");
        TILE_ENTITY_MAP.put("Jukebox", "minecraft:jukebox");
        TILE_ENTITY_MAP.put("BrewingStand", "minecraft:brewing_stand");
    }

    private LegacyBlockIdMapper() {}

    public static String mapTileEntityId(String legacyId) {
        if (legacyId == null) return "minecraft:chest";
        String mapped = TILE_ENTITY_MAP.get(legacyId);
        return mapped != null ? mapped : legacyId;
    }

    public static Result map(int blockId, int meta, @Nullable LegacyWarnReporter reporter) {
        return map(blockId, meta, reporter, null);
    }

    public static Result map(int blockId, int meta, @Nullable LegacyWarnReporter reporter, @Nullable SourceLoc loc) {
        return switch (blockId) {
            case 0 -> Result.simple("minecraft:air");
            case 1 -> mapStone(meta);
            case 2 -> Result.simple("minecraft:grass_block");
            case 3 -> mapDirt(meta);
            case 4 -> Result.simple("minecraft:cobblestone");
            case 5 -> mapPlanks(meta);
            case 6 -> mapSapling(meta);
            case 7 -> Result.simple("minecraft:bedrock");
            case 8, 9 -> Result.withProps("minecraft:water", "level", "0");
            case 10, 11 -> Result.withProps("minecraft:lava", "level", "0");
            case 12 -> (meta == 1) ? Result.simple("minecraft:red_sand") : Result.simple("minecraft:sand");
            case 13 -> Result.simple("minecraft:gravel");
            case 14 -> Result.simple("minecraft:gold_ore");
            case 15 -> Result.simple("minecraft:iron_ore");
            case 16 -> Result.simple("minecraft:coal_ore");
            case 17 -> mapLog(meta);
            case 18 -> mapLeaves(meta);
            case 19 -> (meta == 1) ? Result.simple("minecraft:wet_sponge") : Result.simple("minecraft:sponge");
            case 20 -> Result.simple("minecraft:glass");
            case 21 -> Result.simple("minecraft:lapis_ore");
            case 22 -> Result.simple("minecraft:lapis_block");
            case 23 -> mapDispenser(meta);
            case 24 -> mapSandstone(meta);
            case 25 -> Result.simple("minecraft:note_block");
            case 26 -> mapBed(meta);
            case 27 -> mapPoweredRail(meta);
            case 28 -> mapDetectorRail(meta);
            case 29 -> mapPiston(meta, true);
            case 30 -> Result.simple("minecraft:cobweb");
            case 31 -> mapTallGrass(meta);
            case 32 -> Result.simple("minecraft:dead_bush");
            case 33 -> mapPiston(meta, false);
            case 34 -> mapPistonHead(meta);
            case 35 -> Result.simple("minecraft:" + DYE_COLOR[meta & 15] + "_wool");
            case 37 -> Result.simple("minecraft:dandelion");
            case 38 -> mapFlower(meta);
            case 39 -> Result.simple("minecraft:brown_mushroom");
            case 40 -> Result.simple("minecraft:red_mushroom");
            case 41 -> Result.simple("minecraft:gold_block");
            case 42 -> Result.simple("minecraft:iron_block");
            case 43 -> mapDoubleSlab(meta);
            case 44 -> mapStoneSlab(meta);
            case 45 -> Result.simple("minecraft:bricks");
            case 46 -> Result.simple("minecraft:tnt");
            case 47 -> Result.simple("minecraft:bookshelf");
            case 48 -> Result.simple("minecraft:mossy_cobblestone");
            case 49 -> Result.simple("minecraft:obsidian");
            case 50 -> mapTorch(meta);
            case 51 -> Result.simple("minecraft:fire");
            case 52 -> Result.simple("minecraft:spawner");
            case 53 -> mapStairs("minecraft:oak_stairs", meta);
            case 54 -> mapChest(meta, "minecraft:chest");
            case 55 -> Result.withProps("minecraft:redstone_wire", "power", "0", "north", "none", "south", "none", "east", "none", "west", "none");
            case 56 -> Result.simple("minecraft:diamond_ore");
            case 57 -> Result.simple("minecraft:diamond_block");
            case 58 -> Result.simple("minecraft:crafting_table");
            case 59 -> Result.withProps("minecraft:wheat", "age", "7");
            case 60 -> Result.withProps("minecraft:farmland", "moisture", "0");
            case 61, 62 -> mapFurnace(meta);
            case 63 -> Result.withProps("minecraft:oak_sign", "rotation", String.valueOf(meta & 15), "waterlogged", "false");
            case 64 -> mapDoor("minecraft:oak_door", meta);
            case 65 -> mapLadder(meta);
            case 66 -> mapRail(meta);
            case 67 -> mapStairs("minecraft:cobblestone_stairs", meta);
            case 68 -> mapWallSign("minecraft:oak_wall_sign", meta);
            case 69 -> mapLever(meta);
            case 70 -> Result.simple("minecraft:stone_pressure_plate");
            case 71 -> mapDoor("minecraft:iron_door", meta);
            case 72 -> Result.simple("minecraft:oak_pressure_plate");
            case 73, 74 -> Result.simple("minecraft:redstone_ore");
            case 75, 76 -> mapRedstoneTorch(meta, blockId == 76);
            case 77 -> mapButton("minecraft:stone_button", meta);
            case 78 -> mapSnowLayer(meta);
            case 79 -> Result.simple("minecraft:ice");
            case 80 -> Result.simple("minecraft:snow_block");
            case 81 -> Result.simple("minecraft:cactus");
            case 82 -> Result.simple("minecraft:clay");
            case 83 -> Result.simple("minecraft:sugar_cane");
            case 84 -> Result.simple("minecraft:jukebox");
            case 85 -> Result.simple("minecraft:oak_fence");
            case 86 -> Result.withProps("minecraft:carved_pumpkin", "facing", "south");
            case 87 -> Result.simple("minecraft:netherrack");
            case 88 -> Result.simple("minecraft:soul_sand");
            case 89 -> Result.simple("minecraft:glowstone");
            case 90 -> mapNetherPortal(meta);
            case 91 -> mapJackOLantern(meta);
            case 92 -> Result.simple("minecraft:cake");
            case 93 -> mapRepeater(meta, false);
            case 94 -> mapRepeater(meta, true);
            case 95 -> Result.simple("minecraft:" + DYE_COLOR[meta & 15] + "_stained_glass");
            case 96 -> mapTrapdoor("minecraft:oak_trapdoor", meta);
            case 97 -> Result.simple("minecraft:infested_stone");
            case 98 -> mapStoneBricks(meta);
            case 99 -> Result.simple("minecraft:mushroom_stem");
            case 100 -> Result.simple("minecraft:red_mushroom_block");
            case 101 -> Result.simple("minecraft:iron_bars");
            case 102 -> Result.simple("minecraft:glass_pane");
            case 103 -> Result.simple("minecraft:melon");
            case 104 -> Result.simple("minecraft:pumpkin_stem");
            case 105 -> Result.simple("minecraft:melon_stem");
            case 106 -> Result.simple("minecraft:vine");
            case 107 -> mapFenceGate("minecraft:oak_fence_gate", meta);
            case 108 -> mapStairs("minecraft:brick_stairs", meta);
            case 109 -> mapStairs("minecraft:stone_brick_stairs", meta);
            case 110 -> Result.simple("minecraft:mycelium");
            case 111 -> Result.simple("minecraft:lily_pad");
            case 112 -> Result.simple("minecraft:nether_bricks");
            case 113 -> Result.simple("minecraft:nether_brick_fence");
            case 114 -> mapStairs("minecraft:nether_brick_stairs", meta);
            case 115 -> Result.simple("minecraft:nether_wart");
            case 116 -> Result.simple("minecraft:enchanting_table");
            case 117 -> Result.simple("minecraft:brewing_stand");
            case 118 -> Result.simple("minecraft:cauldron");
            case 119 -> Result.simple("minecraft:air"); // end portal = skip
            case 120 -> Result.simple("minecraft:end_portal_frame");
            case 121 -> Result.simple("minecraft:end_stone");
            case 122 -> Result.simple("minecraft:dragon_egg");
            case 123, 124 -> Result.simple("minecraft:redstone_lamp");
            case 125 -> mapDoubleWoodSlab(meta);
            case 126 -> mapWoodSlab(meta);
            case 127 -> Result.simple("minecraft:cocoa");
            case 128 -> mapStairs("minecraft:sandstone_stairs", meta);
            case 129 -> Result.simple("minecraft:emerald_ore");
            case 130 -> Result.withProps("minecraft:ender_chest", "facing", "north", "waterlogged", "false");
            case 131 -> Result.withProps("minecraft:tripwire_hook", "facing", "north", "attached", "false", "powered", "false");
            case 132 -> Result.simple("minecraft:tripwire");
            case 133 -> Result.simple("minecraft:emerald_block");
            case 134 -> mapStairs("minecraft:spruce_stairs", meta);
            case 135 -> mapStairs("minecraft:birch_stairs", meta);
            case 136 -> mapStairs("minecraft:jungle_stairs", meta);
            case 137 -> Result.simple("minecraft:command_block");
            case 138 -> Result.simple("minecraft:beacon");
            case 139 -> (meta == 1) ? Result.simple("minecraft:mossy_cobblestone_wall") : Result.simple("minecraft:cobblestone_wall");
            case 140 -> Result.simple("minecraft:flower_pot");
            case 141 -> Result.withProps("minecraft:carrots", "age", "7");
            case 142 -> Result.withProps("minecraft:potatoes", "age", "7");
            case 143 -> mapButton("minecraft:oak_button", meta);
            case 144 -> Result.simple("minecraft:skeleton_skull");
            case 145 -> mapAnvil(meta);
            case 146 -> mapChest(meta, "minecraft:trapped_chest");
            case 147 -> Result.simple("minecraft:light_weighted_pressure_plate");
            case 148 -> Result.simple("minecraft:heavy_weighted_pressure_plate");
            case 149 -> mapComparator(meta, false);
            case 150 -> mapComparator(meta, true);
            case 151 -> Result.simple("minecraft:daylight_detector");
            case 152 -> Result.simple("minecraft:redstone_block");
            case 153 -> Result.simple("minecraft:nether_quartz_ore");
            case 154 -> mapHopper(meta);
            case 155 -> mapQuartzBlock(meta);
            case 156 -> mapStairs("minecraft:quartz_stairs", meta);
            case 157 -> mapActivatorRail(meta);
            case 158 -> mapDropper(meta);
            case 159 -> Result.simple("minecraft:" + DYE_COLOR[meta & 15] + "_terracotta");
            case 160 -> Result.simple("minecraft:" + DYE_COLOR[meta & 15] + "_stained_glass_pane");
            case 161 -> mapLeaves2(meta);
            case 162 -> mapLog2(meta);
            case 163 -> mapStairs("minecraft:acacia_stairs", meta);
            case 164 -> mapStairs("minecraft:dark_oak_stairs", meta);
            case 165 -> Result.simple("minecraft:slime_block");
            case 166 -> Result.simple("minecraft:barrier");
            case 167 -> mapTrapdoor("minecraft:iron_trapdoor", meta);
            case 168 -> mapPrismarine(meta);
            case 169 -> Result.simple("minecraft:sea_lantern");
            case 170 -> mapHayBlock(meta);
            case 171 -> Result.simple("minecraft:" + DYE_COLOR[meta & 15] + "_carpet");
            case 172 -> Result.simple("minecraft:terracotta");
            case 173 -> Result.simple("minecraft:coal_block");
            case 174 -> Result.simple("minecraft:packed_ice");
            case 175 -> mapDoublePlant(meta);
            case 176 -> Result.withProps("minecraft:white_banner", "rotation", String.valueOf(meta & 15));
            case 177 -> mapWallBanner(meta);
            case 178 -> Result.simple("minecraft:daylight_detector");
            case 179 -> mapRedSandstone(meta);
            case 180 -> mapStairs("minecraft:red_sandstone_stairs", meta);
            case 181 -> mapDoubleSlab2(meta);
            case 182 -> mapRedSandstoneSlab(meta);
            case 183 -> mapFenceGate("minecraft:spruce_fence_gate", meta);
            case 184 -> mapFenceGate("minecraft:birch_fence_gate", meta);
            case 185 -> mapFenceGate("minecraft:jungle_fence_gate", meta);
            case 186 -> mapFenceGate("minecraft:dark_oak_fence_gate", meta);
            case 187 -> mapFenceGate("minecraft:acacia_fence_gate", meta);
            case 188 -> Result.simple("minecraft:spruce_fence");
            case 189 -> Result.simple("minecraft:birch_fence");
            case 190 -> Result.simple("minecraft:jungle_fence");
            case 191 -> Result.simple("minecraft:dark_oak_fence");
            case 192 -> Result.simple("minecraft:acacia_fence");
            case 193 -> mapDoor("minecraft:spruce_door", meta);
            case 194 -> mapDoor("minecraft:birch_door", meta);
            case 195 -> mapDoor("minecraft:jungle_door", meta);
            case 196 -> mapDoor("minecraft:acacia_door", meta);
            case 197 -> mapDoor("minecraft:dark_oak_door", meta);
            case 198 -> Result.simple("minecraft:end_rod");
            case 199 -> Result.simple("minecraft:chorus_plant");
            case 200 -> Result.simple("minecraft:chorus_flower");
            case 201 -> Result.simple("minecraft:purpur_block");
            case 202 -> mapPurpurPillar(meta);
            case 203 -> mapStairs("minecraft:purpur_stairs", meta);
            case 204 -> Result.simple("minecraft:purpur_block"); // double purpur slab
            case 205 -> Result.withProps("minecraft:purpur_slab", "type", (meta & 8) != 0 ? "top" : "bottom", "waterlogged", "false");
            case 206 -> Result.simple("minecraft:end_stone_bricks");
            case 207 -> Result.withProps("minecraft:beetroots", "age", "3");
            case 208 -> Result.simple("minecraft:grass_path");
            case 210 -> Result.simple("minecraft:repeating_command_block");
            case 211 -> Result.simple("minecraft:chain_command_block");
            case 212 -> Result.simple("minecraft:frosted_ice");
            case 213 -> Result.simple("minecraft:magma_block");
            case 214 -> Result.simple("minecraft:nether_wart_block");
            case 215 -> Result.simple("minecraft:red_nether_bricks");
            case 216 -> mapBoneBlock(meta);
            case 217 -> Result.simple("minecraft:structure_void");
            case 218 -> Result.simple("minecraft:observer");
            // Shulker boxes 219-234
            case 219 -> Result.simple("minecraft:white_shulker_box");
            case 220 -> Result.simple("minecraft:orange_shulker_box");
            case 221 -> Result.simple("minecraft:magenta_shulker_box");
            case 222 -> Result.simple("minecraft:light_blue_shulker_box");
            case 223 -> Result.simple("minecraft:yellow_shulker_box");
            case 224 -> Result.simple("minecraft:lime_shulker_box");
            case 225 -> Result.simple("minecraft:pink_shulker_box");
            case 226 -> Result.simple("minecraft:gray_shulker_box");
            case 227 -> Result.simple("minecraft:light_gray_shulker_box");
            case 228 -> Result.simple("minecraft:cyan_shulker_box");
            case 229 -> Result.simple("minecraft:purple_shulker_box");
            case 230 -> Result.simple("minecraft:blue_shulker_box");
            case 231 -> Result.simple("minecraft:brown_shulker_box");
            case 232 -> Result.simple("minecraft:green_shulker_box");
            case 233 -> Result.simple("minecraft:red_shulker_box");
            case 234 -> Result.simple("minecraft:black_shulker_box");
            // Glazed terracotta 235-250
            case 235 -> Result.simple("minecraft:white_glazed_terracotta");
            case 236 -> Result.simple("minecraft:orange_glazed_terracotta");
            case 237 -> Result.simple("minecraft:magenta_glazed_terracotta");
            case 238 -> Result.simple("minecraft:light_blue_glazed_terracotta");
            case 239 -> Result.simple("minecraft:yellow_glazed_terracotta");
            case 240 -> Result.simple("minecraft:lime_glazed_terracotta");
            case 241 -> Result.simple("minecraft:pink_glazed_terracotta");
            case 242 -> Result.simple("minecraft:gray_glazed_terracotta");
            case 243 -> Result.simple("minecraft:light_gray_glazed_terracotta");
            case 244 -> Result.simple("minecraft:cyan_glazed_terracotta");
            case 245 -> Result.simple("minecraft:purple_glazed_terracotta");
            case 246 -> Result.simple("minecraft:blue_glazed_terracotta");
            case 247 -> Result.simple("minecraft:brown_glazed_terracotta");
            case 248 -> Result.simple("minecraft:green_glazed_terracotta");
            case 249 -> Result.simple("minecraft:red_glazed_terracotta");
            case 250 -> Result.simple("minecraft:black_glazed_terracotta");
            // Concrete 251
            case 251 -> Result.simple("minecraft:" + DYE_COLOR[meta & 15] + "_concrete");
            // Concrete powder 252
            case 252 -> Result.simple("minecraft:" + DYE_COLOR[meta & 15] + "_concrete_powder");
            case 255 -> Result.simple("minecraft:structure_block");
            default -> {
                if (reporter != null) {
                    reporter.warnOnce("BlockId", "unknown legacy block id " + blockId, loc);
                }
                yield Result.simple("minecraft:air");
            }
        };
    }

    // --- Helper methods ---

    private static Result mapStone(int meta) {
        return switch (meta) {
            case 1 -> Result.simple("minecraft:granite");
            case 2 -> Result.simple("minecraft:polished_granite");
            case 3 -> Result.simple("minecraft:diorite");
            case 4 -> Result.simple("minecraft:polished_diorite");
            case 5 -> Result.simple("minecraft:andesite");
            case 6 -> Result.simple("minecraft:polished_andesite");
            default -> Result.simple("minecraft:stone");
        };
    }

    private static Result mapDirt(int meta) {
        return switch (meta) {
            case 1 -> Result.simple("minecraft:coarse_dirt");
            case 2 -> Result.simple("minecraft:podzol");
            default -> Result.simple("minecraft:dirt");
        };
    }

    private static Result mapPlanks(int meta) {
        String[] planks = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
        int idx = Math.min(meta & 7, planks.length - 1);
        return Result.simple("minecraft:" + planks[idx] + "_planks");
    }

    private static Result mapSapling(int meta) {
        String[] saplings = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
        int idx = Math.min(meta & 7, saplings.length - 1);
        return Result.simple("minecraft:" + saplings[idx] + "_sapling");
    }

    private static Result mapLog(int meta) {
        String[] logs = {"oak", "spruce", "birch", "jungle"};
        int woodType = meta & 3;
        int axisCode = (meta >> 2) & 3;
        String axis = axisCode == 1 ? "x" : axisCode == 2 ? "z" : "y";
        return Result.withProps("minecraft:" + logs[woodType] + "_log", "axis", axis);
    }

    private static Result mapLog2(int meta) {
        String[] logs = {"acacia", "dark_oak"};
        int woodType = meta & 1;
        int axisCode = (meta >> 2) & 3;
        String axis = axisCode == 1 ? "x" : axisCode == 2 ? "z" : "y";
        return Result.withProps("minecraft:" + logs[woodType] + "_log", "axis", axis);
    }

    private static Result mapLeaves(int meta) {
        String[] leaves = {"oak", "spruce", "birch", "jungle"};
        int leafType = meta & 3;
        boolean persistent = (meta & 4) != 0;
        return Result.withProps("minecraft:" + leaves[leafType] + "_leaves",
            "persistent", String.valueOf(persistent), "distance", "7");
    }

    private static Result mapLeaves2(int meta) {
        String[] leaves = {"acacia", "dark_oak"};
        int leafType = meta & 1;
        boolean persistent = (meta & 4) != 0;
        return Result.withProps("minecraft:" + leaves[leafType] + "_leaves",
            "persistent", String.valueOf(persistent), "distance", "7");
    }

    private static Result mapStoneBricks(int meta) {
        return switch (meta) {
            case 1 -> Result.simple("minecraft:mossy_stone_bricks");
            case 2 -> Result.simple("minecraft:cracked_stone_bricks");
            case 3 -> Result.simple("minecraft:chiseled_stone_bricks");
            default -> Result.simple("minecraft:stone_bricks");
        };
    }

    private static Result mapSandstone(int meta) {
        return switch (meta) {
            case 1 -> Result.simple("minecraft:chiseled_sandstone");
            case 2 -> Result.simple("minecraft:cut_sandstone");
            default -> Result.simple("minecraft:sandstone");
        };
    }

    private static Result mapRedSandstone(int meta) {
        return switch (meta) {
            case 1 -> Result.simple("minecraft:chiseled_red_sandstone");
            case 2 -> Result.simple("minecraft:cut_red_sandstone");
            default -> Result.simple("minecraft:red_sandstone");
        };
    }

    private static Result mapStairs(String blockId, int meta) {
        String[] facings = {"east", "west", "south", "north"};
        String facing = facings[meta & 3];
        String half = (meta & 4) != 0 ? "top" : "bottom";
        return Result.withProps(blockId, "facing", facing, "half", half, "shape", "straight");
    }

    private static Result mapStoneSlab(int meta) {
        String[] types = {
            "minecraft:stone_slab", "minecraft:sandstone_slab", "minecraft:oak_slab",
            "minecraft:cobblestone_slab", "minecraft:brick_slab", "minecraft:stone_brick_slab",
            "minecraft:nether_brick_slab", "minecraft:quartz_slab"
        };
        int typeIdx = meta & 7;
        String slabType = (meta & 8) != 0 ? "top" : "bottom";
        String blockId = typeIdx < types.length ? types[typeIdx] : "minecraft:stone_slab";
        return Result.withProps(blockId, "type", slabType, "waterlogged", "false");
    }

    private static Result mapDoubleSlab(int meta) {
        String[] types = {
            "minecraft:smooth_stone", "minecraft:sandstone", "minecraft:oak_planks",
            "minecraft:cobblestone", "minecraft:bricks", "minecraft:stone_bricks",
            "minecraft:nether_bricks", "minecraft:quartz_block"
        };
        String[] smoothTypes = {
            "minecraft:smooth_stone", "minecraft:smooth_sandstone", "minecraft:oak_planks",
            "minecraft:cobblestone", "minecraft:bricks", "minecraft:stone_bricks",
            "minecraft:nether_bricks", "minecraft:smooth_quartz_block"
        };
        int typeIdx = meta & 7;
        boolean smooth = (meta & 8) != 0;
        String[] arr = smooth ? smoothTypes : types;
        return Result.simple(typeIdx < arr.length ? arr[typeIdx] : "minecraft:stone");
    }

    private static Result mapWoodSlab(int meta) {
        String[] types = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
        int typeIdx = meta & 7;
        String slabType = (meta & 8) != 0 ? "top" : "bottom";
        String wood = typeIdx < types.length ? types[typeIdx] : "oak";
        return Result.withProps("minecraft:" + wood + "_slab", "type", slabType, "waterlogged", "false");
    }

    private static Result mapDoubleWoodSlab(int meta) {
        String[] types = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
        int typeIdx = meta & 7;
        String wood = typeIdx < types.length ? types[typeIdx] : "oak";
        return Result.simple("minecraft:" + wood + "_planks");
    }

    private static Result mapDoubleSlab2(int meta) {
        // block 181 — double red sandstone slab
        return Result.simple("minecraft:red_sandstone");
    }

    private static Result mapRedSandstoneSlab(int meta) {
        String slabType = (meta & 8) != 0 ? "top" : "bottom";
        return Result.withProps("minecraft:red_sandstone_slab", "type", slabType, "waterlogged", "false");
    }

    private static Result mapTorch(int meta) {
        if (meta == 0 || meta == 5) return Result.simple("minecraft:torch");
        String[] facings = {null, "east", "west", "south", "north"};
        if (meta >= 1 && meta <= 4) {
            return Result.withProps("minecraft:wall_torch", "facing", facings[meta]);
        }
        return Result.simple("minecraft:torch");
    }

    private static Result mapRedstoneTorch(int meta, boolean lit) {
        String blockName = lit ? "minecraft:redstone_torch" : "minecraft:redstone_torch";
        if (meta == 0 || meta == 5) return Result.simple(blockName);
        String[] facings = {null, "east", "west", "south", "north"};
        String wallBlock = lit ? "minecraft:redstone_wall_torch" : "minecraft:redstone_wall_torch";
        if (meta >= 1 && meta <= 4) {
            return Result.withProps(wallBlock, "facing", facings[meta], "lit", String.valueOf(lit));
        }
        return Result.withProps(blockName, "lit", String.valueOf(lit));
    }

    private static Result mapFurnace(int meta) {
        String[] facings = {null, null, "north", "south", "west", "east"};
        String facing = (meta >= 2 && meta <= 5) ? facings[meta] : "north";
        return Result.withProps("minecraft:furnace", "facing", facing, "lit", "false");
    }

    private static Result mapChest(int meta, String blockId) {
        String[] facings = {null, null, "north", "south", "west", "east"};
        String facing = (meta >= 2 && meta <= 5) ? facings[meta] : "north";
        return Result.withProps(blockId, "facing", facing, "type", "single", "waterlogged", "false");
    }

    private static Result mapDispenser(int meta) {
        String[] facings = {"down", "up", "north", "south", "west", "east"};
        String facing = (meta & 7) < facings.length ? facings[meta & 7] : "north";
        boolean triggered = (meta & 8) != 0;
        return Result.withProps("minecraft:dispenser", "facing", facing, "triggered", String.valueOf(triggered));
    }

    private static Result mapDropper(int meta) {
        String[] facings = {"down", "up", "north", "south", "west", "east"};
        String facing = (meta & 7) < facings.length ? facings[meta & 7] : "north";
        boolean triggered = (meta & 8) != 0;
        return Result.withProps("minecraft:dropper", "facing", facing, "triggered", String.valueOf(triggered));
    }

    private static Result mapHopper(int meta) {
        String[] facings = {"down", null, "north", "south", "west", "east"};
        int code = meta & 7;
        String facing = (code < facings.length && facings[code] != null) ? facings[code] : "down";
        return Result.withProps("minecraft:hopper", "facing", facing, "enabled", "true");
    }

    private static Result mapDoor(String blockId, int meta) {
        if ((meta & 8) != 0) {
            // Top half
            String hinge = (meta & 1) != 0 ? "right" : "left";
            return Result.withProps(blockId, "facing", "east", "half", "upper", "hinge", hinge, "open", "false", "powered", "false");
        } else {
            String[] facings = {"east", "south", "west", "north"};
            String facing = facings[meta & 3];
            boolean open = (meta & 4) != 0;
            return Result.withProps(blockId, "facing", facing, "half", "lower", "hinge", "left", "open", String.valueOf(open), "powered", "false");
        }
    }

    /**
     * 1.8.9 lever metadata → 1.18.2 blockstate properties.
     *
     * <p>Metadata encoding:
     * <ul>
     *   <li>bits 0-2 (meta & 7): orientation
     *     <ul>
     *       <li>0 = floor, pointing south-north (ceiling east alias)</li>
     *       <li>1 = east wall  (support block to the west)</li>
     *       <li>2 = west wall  (support block to the east)</li>
     *       <li>3 = south wall (support block to the north)</li>
     *       <li>4 = north wall (support block to the south)</li>
     *       <li>5 = floor, pointing east-west</li>
     *       <li>6 = ceiling, pointing east-west (alias)</li>
     *       <li>7 = ceiling, pointing south-north</li>
     *     </ul>
     *   </li>
     *   <li>bit 3 (meta & 8): powered</li>
     * </ul>
     *
     * <p>1.18.2 lever properties: face=(FLOOR|WALL|CEILING), facing=(NORTH|SOUTH|EAST|WEST), powered.
     * For face=WALL: 'facing' is the direction the lever faces away from the wall
     * (i.e. opposite of the supporting block side).
     */
    private static Result mapLever(int meta) {
        boolean powered = (meta & 8) != 0;
        String face;
        String facing;
        switch (meta & 7) {
            case 0 -> { face = "FLOOR";   facing = "SOUTH"; }
            case 1 -> { face = "WALL";    facing = "EAST"; }
            case 2 -> { face = "WALL";    facing = "WEST"; }
            case 3 -> { face = "WALL";    facing = "SOUTH"; }
            case 4 -> { face = "WALL";    facing = "NORTH"; }
            case 5 -> { face = "FLOOR";   facing = "EAST"; }
            case 6 -> { face = "CEILING"; facing = "EAST"; }
            default -> { face = "CEILING"; facing = "SOUTH"; } // 7
        }
        return Result.withProps("minecraft:lever",
            "face", face.toLowerCase(java.util.Locale.ROOT),
            "facing", facing.toLowerCase(java.util.Locale.ROOT),
            "powered", String.valueOf(powered));
    }

    /**
     * 1.8.9 button metadata → 1.18.2 blockstate properties.
     *
     * <p>Bits 0-2 (meta & 7):
     * <ul>
     *   <li>0 = floor (pointing down → face=FLOOR)</li>
     *   <li>1 = east wall</li>
     *   <li>2 = west wall</li>
     *   <li>3 = south wall</li>
     *   <li>4 = north wall</li>
     *   <li>5 = ceiling</li>
     * </ul>
     * Bit 3 (meta & 8): powered.
     */
    private static Result mapButton(String blockId, int meta) {
        boolean powered = (meta & 8) != 0;
        String face;
        String facing;
        switch (meta & 7) {
            case 0 -> { face = "floor";   facing = "south"; }
            case 1 -> { face = "wall";    facing = "east"; }
            case 2 -> { face = "wall";    facing = "west"; }
            case 3 -> { face = "wall";    facing = "south"; }
            case 4 -> { face = "wall";    facing = "north"; }
            case 5 -> { face = "ceiling"; facing = "south"; }
            default -> { face = "floor";  facing = "south"; }
        }
        return Result.withProps(blockId,
            "face", face, "facing", facing, "powered", String.valueOf(powered));
    }

    private static Result mapLadder(int meta) {
        String[] facings = {null, null, "north", "south", "west", "east"};
        String facing = (meta >= 2 && meta <= 5) ? facings[meta] : "north";
        return Result.withProps("minecraft:ladder", "facing", facing, "waterlogged", "false");
    }

    private static Result mapWallSign(String blockId, int meta) {
        String[] facings = {null, null, "north", "south", "west", "east"};
        String facing = (meta >= 2 && meta <= 5) ? facings[meta] : "north";
        return Result.withProps(blockId, "facing", facing, "waterlogged", "false");
    }

    private static Result mapRail(int meta) {
        String[] shapes = {
            "north_south", "east_west", "ascending_east", "ascending_west",
            "ascending_north", "ascending_south", "south_east", "south_west",
            "north_west", "north_east"
        };
        String shape = (meta & 15) < shapes.length ? shapes[meta & 15] : "north_south";
        return Result.withProps("minecraft:rail", "shape", shape, "waterlogged", "false");
    }

    private static Result mapPoweredRail(int meta) {
        String[] shapes = {"north_south", "east_west", "ascending_east", "ascending_west", "ascending_north", "ascending_south"};
        int shapeIdx = meta & 7;
        boolean powered = (meta & 8) != 0;
        String shape = shapeIdx < shapes.length ? shapes[shapeIdx] : "north_south";
        return Result.withProps("minecraft:powered_rail", "shape", shape, "powered", String.valueOf(powered), "waterlogged", "false");
    }

    private static Result mapDetectorRail(int meta) {
        String[] shapes = {"north_south", "east_west", "ascending_east", "ascending_west", "ascending_north", "ascending_south"};
        int shapeIdx = meta & 7;
        boolean powered = (meta & 8) != 0;
        String shape = shapeIdx < shapes.length ? shapes[shapeIdx] : "north_south";
        return Result.withProps("minecraft:detector_rail", "shape", shape, "powered", String.valueOf(powered), "waterlogged", "false");
    }

    private static Result mapActivatorRail(int meta) {
        String[] shapes = {"north_south", "east_west", "ascending_east", "ascending_west", "ascending_north", "ascending_south"};
        int shapeIdx = meta & 7;
        boolean powered = (meta & 8) != 0;
        String shape = shapeIdx < shapes.length ? shapes[shapeIdx] : "north_south";
        return Result.withProps("minecraft:activator_rail", "shape", shape, "powered", String.valueOf(powered), "waterlogged", "false");
    }

    private static Result mapRepeater(int meta, boolean powered) {
        String[] facings = {"south", "west", "north", "east"};
        String facing = facings[meta & 3];
        int delay = ((meta >> 2) & 3) + 1;
        return Result.withProps("minecraft:repeater",
            "facing", facing, "delay", String.valueOf(delay),
            "locked", "false", "powered", String.valueOf(powered));
    }

    private static Result mapComparator(int meta, boolean powered) {
        String[] facings = {"south", "west", "north", "east"};
        String facing = facings[meta & 3];
        String mode = (meta & 4) != 0 ? "subtract" : "compare";
        return Result.withProps("minecraft:comparator",
            "facing", facing, "mode", mode, "powered", String.valueOf(powered));
    }

    private static Result mapTrapdoor(String blockId, int meta) {
        String[] facings = {"south", "north", "east", "west"};
        String facing = facings[meta & 3];
        boolean open = (meta & 4) != 0;
        String half = (meta & 8) != 0 ? "top" : "bottom";
        return Result.withProps(blockId, "facing", facing, "half", half, "open", String.valueOf(open), "powered", "false", "waterlogged", "false");
    }

    private static Result mapBed(int meta) {
        String[] facings = {"south", "west", "north", "east"};
        String facing = facings[meta & 3];
        boolean occupied = (meta & 4) != 0;
        String part = (meta & 8) != 0 ? "head" : "foot";
        return Result.withProps("minecraft:white_bed",
            "facing", facing, "occupied", String.valueOf(occupied), "part", part);
    }

    private static Result mapSnowLayer(int meta) {
        int layers = (meta & 7) + 1;
        return Result.withProps("minecraft:snow", "layers", String.valueOf(layers));
    }

    private static Result mapTallGrass(int meta) {
        return switch (meta) {
            case 2 -> Result.simple("minecraft:fern");
            default -> Result.simple("minecraft:grass");
        };
    }

    private static Result mapDoublePlant(int meta) {
        String[] lower = {"sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony"};
        if ((meta & 8) != 0) {
            // upper half - we map all to sunflower upper as we can't know without context
            return Result.withProps("minecraft:sunflower", "half", "upper");
        }
        int typeIdx = meta & 7;
        String plant = typeIdx < lower.length ? lower[typeIdx] : "sunflower";
        return Result.withProps("minecraft:" + plant, "half", "lower");
    }

    private static Result mapQuartzBlock(int meta) {
        return switch (meta) {
            case 1 -> Result.simple("minecraft:chiseled_quartz_block");
            case 2 -> Result.withProps("minecraft:quartz_pillar", "axis", "y");
            case 3 -> Result.withProps("minecraft:quartz_pillar", "axis", "x");
            case 4 -> Result.withProps("minecraft:quartz_pillar", "axis", "z");
            default -> Result.simple("minecraft:quartz_block");
        };
    }

    private static Result mapHayBlock(int meta) {
        String axis = switch (meta & 12) {
            case 4 -> "x";
            case 8 -> "z";
            default -> "y";
        };
        return Result.withProps("minecraft:hay_block", "axis", axis);
    }

    private static Result mapFenceGate(String blockId, int meta) {
        String[] facings = {"south", "west", "north", "east"};
        String facing = facings[meta & 3];
        boolean open = (meta & 4) != 0;
        return Result.withProps(blockId,
            "facing", facing, "open", String.valueOf(open), "in_wall", "false", "powered", "false");
    }

    private static Result mapPiston(int meta, boolean sticky) {
        String[] facings = {"down", "up", "north", "south", "west", "east"};
        String facing = (meta & 7) < facings.length ? facings[meta & 7] : "north";
        boolean extended = (meta & 8) != 0;
        String blockId = sticky ? "minecraft:sticky_piston" : "minecraft:piston";
        return Result.withProps(blockId, "facing", facing, "extended", String.valueOf(extended));
    }

    private static Result mapPistonHead(int meta) {
        String[] facings = {"down", "up", "north", "south", "west", "east"};
        String facing = (meta & 7) < facings.length ? facings[meta & 7] : "north";
        String type = (meta & 8) != 0 ? "sticky" : "normal";
        return Result.withProps("minecraft:piston_head", "facing", facing, "type", type, "short", "false");
    }

    private static Result mapNetherPortal(int meta) {
        String axis = (meta == 2) ? "z" : "x";
        return Result.withProps("minecraft:nether_portal", "axis", axis);
    }

    private static Result mapJackOLantern(int meta) {
        String[] facings = {"south", "west", "north", "east"};
        String facing = meta < facings.length ? facings[meta] : "south";
        return Result.withProps("minecraft:jack_o_lantern", "facing", facing);
    }

    private static Result mapAnvil(int meta) {
        String block = switch (meta & 3) {
            case 1 -> "minecraft:chipped_anvil";
            case 2 -> "minecraft:damaged_anvil";
            default -> "minecraft:anvil";
        };
        return Result.simple(block);
    }

    private static Result mapPrismarine(int meta) {
        return switch (meta) {
            case 1 -> Result.simple("minecraft:prismarine_bricks");
            case 2 -> Result.simple("minecraft:dark_prismarine");
            default -> Result.simple("minecraft:prismarine");
        };
    }

    private static Result mapWallBanner(int meta) {
        String[] facings = {null, null, "north", "south", "west", "east"};
        String facing = (meta >= 2 && meta <= 5) ? facings[meta] : "north";
        return Result.withProps("minecraft:white_wall_banner", "facing", facing);
    }

    private static Result mapPurpurPillar(int meta) {
        String axis = switch (meta & 12) {
            case 4 -> "x";
            case 8 -> "z";
            default -> "y";
        };
        return Result.withProps("minecraft:purpur_pillar", "axis", axis);
    }

    private static Result mapBoneBlock(int meta) {
        String axis = switch (meta & 12) {
            case 4 -> "x";
            case 8 -> "z";
            default -> "y";
        };
        return Result.withProps("minecraft:bone_block", "axis", axis);
    }

    private static Result mapFlower(int meta) {
        String[] flowers = {
            "poppy", "blue_orchid", "allium", "azure_bluet",
            "red_tulip", "orange_tulip", "white_tulip", "pink_tulip",
            "oxeye_daisy", "cornflower", "lily_of_the_valley"
        };
        int idx = Math.min(meta & 15, flowers.length - 1);
        return Result.simple("minecraft:" + flowers[idx]);
    }
}
