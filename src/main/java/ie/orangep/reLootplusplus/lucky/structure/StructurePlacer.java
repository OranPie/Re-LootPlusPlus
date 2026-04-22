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
import net.minecraft.util.registry.Registry;

import java.util.Map;
import java.util.Optional;

/** Places a {@link ParsedStructure} in the world. */
public final class StructurePlacer {

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

            Block block = Registry.BLOCK.get(id);
            BlockState state = block.getDefaultState();
            state = applyProperties(state, sb.properties(), rotation);

            world.setBlockState(worldPos, state, Block.NOTIFY_ALL | Block.FORCE_STATE);

            if (sb.tileEntityNbt() != null) {
                BlockEntity be = world.getBlockEntity(worldPos);
                if (be != null) {
                    NbtCompound existing = be.createNbt();
                    NbtCompound te = sb.tileEntityNbt();
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
}
