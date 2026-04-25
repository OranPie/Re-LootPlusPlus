package ie.orangep.reLootplusplus.lucky.drop.action;

import ie.orangep.reLootplusplus.lucky.attr.LuckyAttr;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.entity.NativeLuckyProjectile;
import ie.orangep.reLootplusplus.lucky.registry.LuckyRegistrar;
import ie.orangep.reLootplusplus.lucky.template.LuckyTemplateVars;
import ie.orangep.reLootplusplus.diagnostic.Log;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executes {@code type=luckyprojectile} drops.
 *
 * <p>Spawns a {@link NativeLuckyProjectile} at the drop location with optional:
 * <ul>
 *   <li>{@code posOffsetY} — Y offset from block position</li>
 *   <li>{@code NBTTag.Motion} — velocity vector computed from {@code #motionFromDirection}</li>
 *   <li>{@code NBTTag.impact} — inline drop lines to execute on projectile collision</li>
 *   <li>{@code amount} — how many projectiles to spawn</li>
 * </ul>
 */
public final class LuckyProjectileDropAction {

    private LuckyProjectileDropAction() {}

    public static void execute(LuckyDropLine drop, LuckyDropContext ctx) {
        if (!(ctx.world() instanceof ServerWorld)) return;
        ServerWorld serverWorld = (ServerWorld) ctx.world();

        LuckyTemplateVars.EvalContext evalCtx = new LuckyTemplateVars.EvalContext(
            ctx.world(), ctx.pos(), ctx.player(), ctx.world().getRandom()
        );

        // Spawn position — block center + posOffsetY
        double spawnX = ctx.pos().getX() + 0.5;
        double spawnY = ctx.pos().getY();
        double spawnZ = ctx.pos().getZ() + 0.5;

        String posStr = drop.getString("pos");
        if (posStr != null && !posStr.isBlank()) {
            posStr = LuckyTemplateVars.evaluate(posStr, evalCtx);
            String[] parts = posStr.trim().split("\\s+");
            if (parts.length >= 3) {
                try { spawnX = Double.parseDouble(parts[0]); } catch (NumberFormatException ignored) {}
                try { spawnY = Double.parseDouble(parts[1]); } catch (NumberFormatException ignored) {}
                try { spawnZ = Double.parseDouble(parts[2]); } catch (NumberFormatException ignored) {}
            }
        }

        String posOffsetYStr = drop.getString("posOffsetY");
        if (posOffsetYStr != null && !posOffsetYStr.isBlank()) {
            posOffsetYStr = LuckyTemplateVars.evaluate(posOffsetYStr, evalCtx);
            spawnY += LuckyTemplateVars.evalArithmetic(posOffsetYStr, 0.0);
        }

        // Amount — how many projectiles to fire
        int amount = 1;
        String amountStr = drop.getString("amount");
        if (amountStr != null && !amountStr.isBlank()) {
            try { amount = (int) LuckyTemplateVars.evalArithmetic(
                LuckyTemplateVars.evaluate(amountStr, evalCtx), 1.0); } catch (Exception ignored) {}
        }

        // Extract NBTTag dict attr for Motion and impact
        double[] motion = null;
        List<String> impactDropLines = new ArrayList<>();

        LuckyAttr nbtAttr = getAttr(drop, "NBTTag");
        if (nbtAttr instanceof LuckyAttr.DictAttr dictAttr) {
            // Motion velocity
            LuckyAttr motionAttr = dictAttr.entries().get("Motion");
            if (motionAttr instanceof LuckyAttr.StringAttr motionStr) {
                String evaluated = LuckyTemplateVars.evaluate(motionStr.value(), evalCtx);
                motion = parseMotionVector(evaluated);
            } else if (motionAttr instanceof LuckyAttr.ListAttr motionList) {
                // Motion=[dx;dy;dz] as a list
                motion = parseMotionList(motionList, evalCtx);
            }

            // Impact drop lines — stored as a ListAttr of raw drop strings
            LuckyAttr impactAttr = dictAttr.entries().get("impact");
            if (impactAttr instanceof LuckyAttr.ListAttr impactList) {
                for (LuckyAttr item : impactList.items()) {
                    if (item instanceof LuckyAttr.StringAttr s && !s.value().isBlank()) {
                        impactDropLines.add(s.value().trim());
                    }
                }
            } else if (impactAttr instanceof LuckyAttr.StringAttr s && !s.value().isBlank()) {
                // Single impact line as a string
                impactDropLines.add(s.value().trim());
            }
        }

        final double[] finalMotion = motion;
        final List<String> finalImpact = impactDropLines;
        final double finalX = spawnX, finalY = spawnY, finalZ = spawnZ;

        for (int i = 0; i < Math.max(1, amount); i++) {
            NativeLuckyProjectile proj = new NativeLuckyProjectile(
                LuckyRegistrar.LUCKY_PROJECTILE_TYPE, serverWorld);

            if (ctx.player() != null) proj.setOwner(ctx.player());
            proj.refreshPositionAndAngles(finalX, finalY, finalZ, 0, 0);

            if (finalMotion != null && finalMotion.length == 3) {
                proj.setVelocity(new Vec3d(finalMotion[0], finalMotion[1], finalMotion[2]));
                proj.velocityModified = true;
            }

            if (!finalImpact.isEmpty()) {
                proj.setImpactDropLines(finalImpact);
            }

            // Suppress pickup
            proj.pickupType = net.minecraft.entity.projectile.PersistentProjectileEntity.PickupPermission.DISALLOWED;

            serverWorld.spawnEntity(proj);
        }

        Log.debug("LuckyDrop", String.format("[PROJ] spawned %d projectile(s) at (%.1f,%.1f,%.1f) impact=%d",
            amount, spawnX, spawnY, spawnZ, impactDropLines.size()));
    }

    private static LuckyAttr getAttr(LuckyDropLine drop, String key) {
        LuckyAttr v = drop.attrs().get(key);
        if (v != null) return v;
        for (Map.Entry<String, LuckyAttr> e : drop.attrs().entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) return e.getValue();
        }
        return null;
    }

    /** Parses a space-separated motion string like "-0.5 0.3 0.1" into [dx, dy, dz]. */
    private static double[] parseMotionVector(String s) {
        if (s == null || s.isBlank()) return null;
        // Strip surrounding brackets like [dx;dy;dz] or (dx,dy,dz)
        s = s.trim().replaceAll("[\\[\\](){}]", "");
        String[] parts = s.contains(";") ? s.split(";") : (s.contains(",") ? s.split(",") : s.split("\\s+"));
        if (parts.length < 3) return null;
        try {
            return new double[]{
                Double.parseDouble(parts[0].trim()),
                Double.parseDouble(parts[1].trim()),
                Double.parseDouble(parts[2].trim())
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Parses a ListAttr of motion components evaluated via template vars. */
    private static double[] parseMotionList(LuckyAttr.ListAttr list,
                                             LuckyTemplateVars.EvalContext evalCtx) {
        if (list.items().size() < 3) return null;
        double[] motion = new double[3];
        for (int i = 0; i < 3; i++) {
            LuckyAttr item = list.items().get(i);
            if (item instanceof LuckyAttr.StringAttr s) {
                String evaluated = LuckyTemplateVars.evaluate(s.value(), evalCtx);
                motion[i] = LuckyTemplateVars.evalArithmetic(evaluated, 0.0);
            }
        }
        return motion;
    }
}
