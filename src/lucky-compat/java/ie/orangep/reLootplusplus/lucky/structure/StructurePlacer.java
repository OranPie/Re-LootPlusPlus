package ie.orangep.reLootplusplus.lucky.structure;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Places a {@link ParsedStructure} in the world.
 *
 * <p>Uses a two-pass strategy to prevent attached blocks (levers, torches, buttons, etc.)
 * from being placed before their support surface exists:
 * <ol>
 *   <li>Pass 1 — solid / structural blocks — placed without neighbor notifications so that
 *       mass-placement does not trigger premature neighbor validation.</li>
 *   <li>Pass 2 — attached blocks — placed after all structural blocks are in place so every
 *       support surface already exists when the attached block is set.</li>
 * </ol>
 */
public final class StructurePlacer {

    /**
     * Block IDs (path portion only, no namespace prefix) that require an adjacent solid
     * surface to stay in place.  These are deferred to pass 2.
     */
    private static final Set<String> ATTACHED_EXACT = Set.of(
        "lever",
        "torch", "wall_torch",
        "redstone_torch", "redstone_wall_torch",
        "ladder",
        "vine",
        "cocoa",
        "tripwire", "tripwire_hook"
    );

    private StructurePlacer() {}

    public static void place(ParsedStructure structure, ServerWorld world, BlockPos breakPos,
                               int centerX, int centerY, int centerZ,
                               String blockMode, BlockRotation rotation) {
        int w = structure.width();
        int l = structure.length();

        // When rotated 90°/270° the footprint swaps: effective width = l, effective length = w.
        boolean swapWL = (rotation == BlockRotation.CLOCKWISE_90 || rotation == BlockRotation.COUNTERCLOCKWISE_90);
        int effW = swapWL ? l : w;
        int effL = swapWL ? w : l;

        int offX = (centerX != 0) ? centerX : (effW / 2);
        int offY = centerY;
        int offZ = (centerZ != 0) ? centerZ : (effL / 2);

        int startX = breakPos.getX() - offX;
        int startY = breakPos.getY() - offY;
        int startZ = breakPos.getZ() - offZ;

        boolean overlayMode = "overlay".equalsIgnoreCase(blockMode);

        // Partition blocks into solid (pass 1) and attached (pass 2).
        List<PlacementEntry> pass1 = new ArrayList<>();
        List<PlacementEntry> pass2 = new ArrayList<>();

        for (StructureBlock sb : structure.blocks()) {
            int rx = sb.relX(), rz = sb.relZ();
            if (rotation == BlockRotation.CLOCKWISE_90) {
                int tmp = rx;
                rx = l - 1 - sb.relZ();
                rz = tmp;
            } else if (rotation == BlockRotation.CLOCKWISE_180) {
                rx = w - 1 - sb.relX();
                rz = l - 1 - sb.relZ();
            } else if (rotation == BlockRotation.COUNTERCLOCKWISE_90) {
                int tmp = rx;
                rx = sb.relZ();
                rz = w - 1 - tmp;
            }

            BlockPos worldPos = new BlockPos(startX + rx, startY + sb.relY(), startZ + rz);
            if (overlayMode && !world.getBlockState(worldPos).isAir()) continue;

            Identifier id = Identifier.tryParse(sb.blockId());
            if (id == null || !Registry.BLOCK.containsId(id)) continue;

            PlacementEntry entry = new PlacementEntry(sb, worldPos, id);
            if (isAttached(id.getPath())) {
                pass2.add(entry);
            } else {
                pass1.add(entry);
            }
        }

        // Pass 1: solid blocks — suppress neighbor notifications so mass-placement does not
        // break already-placed pass-1 blocks via cascading neighbor updates.
        for (PlacementEntry e : pass1) {
            placeEntry(e, world, rotation, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        }

        // Pass 2: attached blocks — support surfaces are guaranteed to exist.
        for (PlacementEntry e : pass2) {
            placeEntry(e, world, rotation, Block.NOTIFY_ALL | Block.FORCE_STATE);
        }

        // Legacy schematics store panes/fences as bare block IDs. In modern versions their
        // north/east/south/west connection state is explicit, so recalculate after the full
        // footprint exists; otherwise traps place with disconnected iron bars/fences.
        for (PlacementEntry e : pass1) {
            refreshHorizontalConnections(e.pos(), world);
        }
        for (PlacementEntry e : pass2) {
            refreshHorizontalConnections(e.pos(), world);
        }
    }

    private static void placeEntry(PlacementEntry e, ServerWorld world, BlockRotation rotation, int flags) {
        Block block = Registry.BLOCK.get(e.id());
        BlockState state = block.getDefaultState();
        state = applyProperties(state, e.sb().properties(), rotation);
        world.setBlockState(e.pos(), state, flags);

        if (e.sb().tileEntityNbt() != null) {
            BlockEntity be = world.getBlockEntity(e.pos());
            if (be != null) {
                NbtCompound existing = be.createNbt();
                NbtCompound te = e.sb().tileEntityNbt();
                for (String key : te.getKeys()) {
                    if (!key.equals("x") && !key.equals("y") && !key.equals("z")) {
                        existing.put(key, te.get(key));
                    }
                }
                be.readNbt(existing);
                be.markDirty();
            }
        }
    }

    /**
     * Returns true if a block path (no namespace) represents an attached block
     * that needs an adjacent solid surface.
     */
    private static boolean isAttached(String path) {
        if (ATTACHED_EXACT.contains(path)) return true;
        if (path.endsWith("_button")) return true;
        if (path.endsWith("_wall_sign")) return true;
        if (path.endsWith("_wall_hanging_sign")) return true;
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState applyProperties(BlockState state, Map<String, String> props, BlockRotation rotation) {
        for (Map.Entry<String, String> e : props.entrySet()) {
            String name = e.getKey();
            String value = e.getValue();
            if ("facing".equals(name)) {
                value = rotateFacing(value, rotation);
            }
            for (Property<?> prop : state.getBlock().getStateManager().getProperties()) {
                if (prop.getName().equals(name)) {
                    Optional<?> parsed = ((Property) prop).parse(value);
                    if (parsed.isPresent()) {
                        state = state.with((Property) prop, (Comparable) parsed.get());
                    }
                    break;
                }
            }
        }
        return state;
    }

    private static String rotateFacing(String facing, BlockRotation rotation) {
        if (rotation == BlockRotation.NONE) return facing;
        String[] order = {"north", "east", "south", "west"};
        for (int i = 0; i < 4; i++) {
            if (order[i].equals(facing)) {
                int steps = switch (rotation) {
                    case CLOCKWISE_90 -> 1;
                    case CLOCKWISE_180 -> 2;
                    case COUNTERCLOCKWISE_90 -> 3;
                    default -> 0;
                };
                return order[(i + steps) % 4];
            }
        }
        return facing;
    }

    private static void refreshHorizontalConnections(BlockPos pos, ServerWorld world) {
        BlockState state = world.getBlockState(pos);
        if (!hasHorizontalConnectionProperties(state)) {
            return;
        }
        BlockState updated = state;
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(dir);
            updated = updated.getStateForNeighborUpdate(
                dir,
                world.getBlockState(neighborPos),
                world,
                pos,
                neighborPos
            );
        }
        if (updated != state) {
            world.setBlockState(pos, updated, Block.NOTIFY_ALL | Block.FORCE_STATE);
        }
    }

    private static boolean hasHorizontalConnectionProperties(BlockState state) {
        boolean north = false, east = false, south = false, west = false;
        for (Property<?> prop : state.getBlock().getStateManager().getProperties()) {
            switch (prop.getName()) {
                case "north" -> north = true;
                case "east" -> east = true;
                case "south" -> south = true;
                case "west" -> west = true;
                default -> { }
            }
        }
        return north && east && south && west;
    }

    private record PlacementEntry(StructureBlock sb, BlockPos pos, Identifier id) {}
}
