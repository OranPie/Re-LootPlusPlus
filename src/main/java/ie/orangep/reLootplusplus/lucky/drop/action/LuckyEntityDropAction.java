package ie.orangep.reLootplusplus.lucky.drop.action;

import ie.orangep.reLootplusplus.legacy.mapping.LegacyEntityIdFixer;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropContext;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.template.LuckyTemplateVars;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;

/**
 * Executes {@code type=entity} Lucky drops.
 *
 * <p>Fixes legacy entity IDs, loads entity with NBT, applies position overrides
 * ({@code posX}, {@code posY}, {@code posZ}, {@code posOffset}), and spawns
 * {@code amount} copies (re-evaluating template vars per entity for natural randomisation).
 */
public final class LuckyEntityDropAction {

    private LuckyEntityDropAction() {}

    /**
     * Entity-shorthand dispatch used by the evaluator's default case.
     *
     * <p>In Lucky Block 1.8.9 an unknown {@code type=X} means "spawn entity whose
     * type is X" rather than a type=entity drop. Examples:
     * <ul>
     *   <li>{@code type=LuckyProjectile} → entity id = LuckyProjectile
     *   <li>{@code type=falling_block,ID=sponge} → entity id = falling_block, block = sponge
     *   <li>{@code type=armorstand} → entity id = armorstand
     * </ul>
     *
     * @param drop     the drop line (may have no explicit {@code ID=} attribute)
     * @param ctx      runtime context
     * @param typeName the raw type string from the drop (used as entity-id fallback)
     */
    public static void executeAsShorthand(LuckyDropLine drop, LuckyDropContext ctx, String typeName) {
        LuckyTemplateVars.EvalContext evalCtx = new LuckyTemplateVars.EvalContext(
            ctx.world(), ctx.pos(), ctx.player(), ctx.world().getRandom()
        );

        // Evaluate amount
        int amount = parseIntAttr(drop, "amount", evalCtx, 1);
        if (amount < 1) amount = 1;

        for (int i = 0; i < amount; i++) {
            LuckyTemplateVars.EvalContext perCtx = new LuckyTemplateVars.EvalContext(
                ctx.world(), ctx.pos(), ctx.player(), ctx.world().getRandom()
            );
            spawnOneShorthand(drop, ctx, perCtx, typeName);
        }
    }

    private static void spawnOneShorthand(LuckyDropLine drop, LuckyDropContext ctx,
                                           LuckyTemplateVars.EvalContext evalCtx, String typeName) {
        // Entity ID is the type name (e.g. "LuckyProjectile", "falling_block", "armorstand")
        // unless we're dealing with something where rawId() explicitly overrides it.
        String entityId = typeName;

        // Build base entity NBT
        NbtCompound tag = new NbtCompound();
        NbtCompound nbtFromDrop = LuckyAttrToNbt.resolveNbtTag(drop, evalCtx);
        if (nbtFromDrop != null) {
            for (String key : nbtFromDrop.getKeys()) tag.put(key, nbtFromDrop.get(key));
        }

        // For falling_block: ID= is the block type, inject as Block= so fixFallingBlockNbt converts it
        String normalizedEntityId = LegacyEntityIdFixer.normalizeEntityId(entityId, ctx.warnReporter(), ctx.sourceLoc().toString());
        if (normalizedEntityId == null || normalizedEntityId.isBlank()) {
            ctx.warnReporter().warn("LuckyEntityDrop",
                "entity shorthand id blank after normalization: " + typeName, ctx.sourceLoc());
            return;
        }
        boolean isFallingBlock = "minecraft:falling_block".equalsIgnoreCase(normalizedEntityId);
        if (isFallingBlock) {
            String blockId = drop.rawId();
            if (blockId != null && !blockId.isBlank()) {
                blockId = LuckyTemplateVars.evaluate(blockId, evalCtx);
                // Inject as Block= so fixEntityId's fixFallingBlockNbt picks it up
                if (!tag.contains("BlockState") && !tag.contains("Block")) {
                    tag.putString("Block", blockId);
                }
            }
        }

        tag.putString("id", normalizedEntityId);
        LegacyEntityIdFixer.fixEntityId(tag, ctx.warnReporter(), ctx.sourceLoc().toString());

        Vec3d spawnPos = computeSpawnPos(drop, ctx, evalCtx);

        Entity entity = net.minecraft.entity.EntityType.loadEntityWithPassengers(
            tag, ctx.world(), e -> {
                e.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, e.getYaw(), e.getPitch());
                return e;
            }
        );

        if (entity == null) {
            Identifier id = Identifier.tryParse(normalizedEntityId);
            if (id == null || !Registry.ENTITY_TYPE.containsId(id)) {
                ctx.warnReporter().warn("LuckyEntityDrop",
                    "unknown entity shorthand '" + typeName + "'", ctx.sourceLoc());
                return;
            }
            entity = Registry.ENTITY_TYPE.get(id).create(ctx.world());
            if (entity == null) {
                ctx.warnReporter().warn("LuckyEntityDrop",
                    "could not create entity shorthand '" + typeName + "'", ctx.sourceLoc());
                return;
            }
            entity.readNbt(tag);
            entity.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, entity.getYaw(), entity.getPitch());
        }

        ctx.world().spawnEntity(entity);
        applyPostSpawn(entity, drop, evalCtx);
    }

    public static void execute(LuckyDropLine drop, LuckyDropContext ctx) {
        // Evaluate amount (may use #rand)
        LuckyTemplateVars.EvalContext evalCtxOnce = new LuckyTemplateVars.EvalContext(
            ctx.world(), ctx.pos(), ctx.player(), ctx.world().getRandom()
        );
        int amount = parseIntAttr(drop, "amount", evalCtxOnce, 1);
        if (amount < 1) amount = 1;

        for (int i = 0; i < amount; i++) {
            // Re-evaluate template vars per entity — important for #circleOffset, #rand
            LuckyTemplateVars.EvalContext evalCtx = new LuckyTemplateVars.EvalContext(
                ctx.world(), ctx.pos(), ctx.player(), ctx.world().getRandom()
            );
            spawnOne(drop, ctx, evalCtx);
        }
    }

    private static void spawnOne(LuckyDropLine drop, LuckyDropContext ctx, LuckyTemplateVars.EvalContext evalCtx) {
        String rawId = drop.rawId();
        if (rawId == null || rawId.isBlank()) {
            ctx.warnReporter().warn("LuckyEntityDrop", "missing entity id", ctx.sourceLoc());
            return;
        }
        rawId = LuckyTemplateVars.evaluate(rawId, evalCtx);

        // Fix legacy entity ID
        String normalizedId = LegacyEntityIdFixer.normalizeEntityId(rawId, ctx.warnReporter(), ctx.sourceLoc().toString());
        if (normalizedId == null || normalizedId.isBlank()) {
            ctx.warnReporter().warn("LuckyEntityDrop", "entity id blank after normalization: " + rawId, ctx.sourceLoc());
            return;
        }

        // Build NBT — supports both DictAttr and StringAttr NBTTag values
        NbtCompound tag = new NbtCompound();
        NbtCompound nbtFromDrop = LuckyAttrToNbt.resolveNbtTag(drop, evalCtx);
        if (nbtFromDrop != null) {
            for (String key : nbtFromDrop.getKeys()) tag.put(key, nbtFromDrop.get(key));
        }
        tag.putString("id", normalizedId);

        // Fix legacy entity IDs inside NBT (Passengers, Riding, FallingSand Block→BlockState, etc.)
        LegacyEntityIdFixer.fixEntityId(tag, ctx.warnReporter(), ctx.sourceLoc().toString());

        // Compute spawn position
        Vec3d spawnPos = computeSpawnPos(drop, ctx, evalCtx);

        // Use loadEntityWithPassengers for proper passenger handling
        Entity entity = net.minecraft.entity.EntityType.loadEntityWithPassengers(
            tag, ctx.world(), e -> {
                e.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, e.getYaw(), e.getPitch());
                return e;
            }
        );

        if (entity == null) {
            // Fallback: create via registry
            Identifier id = Identifier.tryParse(normalizedId);
            if (id == null || !Registry.ENTITY_TYPE.containsId(id)) {
                ctx.warnReporter().warn("LuckyEntityDrop", "unknown entity " + normalizedId, ctx.sourceLoc());
                return;
            }
            entity = Registry.ENTITY_TYPE.get(id).create(ctx.world());
            if (entity == null) {
                ctx.warnReporter().warn("LuckyEntityDrop", "could not create entity " + normalizedId, ctx.sourceLoc());
                return;
            }
            entity.readNbt(tag);
            entity.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, entity.getYaw(), entity.getPitch());
        }

        ctx.world().spawnEntity(entity);

        // Apply post-spawn attributes
        applyPostSpawn(entity, drop, evalCtx);
    }

    /**
     * Applies post-spawn entity attributes: {@code onFire}, {@code fire} (fire ticks).
     */
    private static void applyPostSpawn(Entity entity, LuckyDropLine drop, LuckyTemplateVars.EvalContext evalCtx) {
        // onFire=true  OR  fire=<ticks>
        String onFireStr = drop.getString("onFire");
        String fireTicksStr = drop.getString("fire");
        if ("true".equalsIgnoreCase(onFireStr) || "1".equals(onFireStr)) {
            entity.setOnFireFor(30);
        } else if (fireTicksStr != null && !fireTicksStr.isBlank()) {
            fireTicksStr = LuckyTemplateVars.evaluate(fireTicksStr, evalCtx);
            try {
                int ticks = (int) LuckyTemplateVars.evalArithmetic(fireTicksStr, 0);
                if (ticks > 0) entity.setOnFireFor(Math.max(1, ticks / 20));
            } catch (Exception ignored) {}
        }
    }

    private static Vec3d computeSpawnPos(LuckyDropLine drop, LuckyDropContext ctx,
                                          LuckyTemplateVars.EvalContext evalCtx) {
        double baseX = ctx.pos().getX() + 0.5;
        double baseY = ctx.pos().getY();
        double baseZ = ctx.pos().getZ() + 0.5;

        // pos= absolute override (e.g., pos=#bowPos → "x y z")
        String posStr = drop.getString("pos");
        if (posStr != null && !posStr.isBlank()) {
            posStr = LuckyTemplateVars.evaluate(posStr, evalCtx);
            String[] parts = posStr.trim().split("\\s+");
            if (parts.length >= 3) {
                try { baseX = Double.parseDouble(parts[0]); } catch (NumberFormatException ignored) {}
                try { baseY = Double.parseDouble(parts[1]); } catch (NumberFormatException ignored) {}
                try { baseZ = Double.parseDouble(parts[2]); } catch (NumberFormatException ignored) {}
            }
        }

        // posX / posY / posZ — direct coordinate overrides (can contain arithmetic like #bPosY+7)
        String posXStr = drop.getString("posX");
        String posYStr = drop.getString("posY");
        String posZStr = drop.getString("posZ");
        if (posXStr != null) baseX = LuckyTemplateVars.evalArithmetic(LuckyTemplateVars.evaluate(posXStr, evalCtx), baseX);
        if (posYStr != null) baseY = LuckyTemplateVars.evalArithmetic(LuckyTemplateVars.evaluate(posYStr, evalCtx), baseY);
        if (posZStr != null) baseZ = LuckyTemplateVars.evalArithmetic(LuckyTemplateVars.evaluate(posZStr, evalCtx), baseZ);

        // posOffset — adds an offset vector, e.g. "#circleOffset(5,45)" → "1.23 0 4.56"
        String posOffsetStr = drop.getString("posOffset");
        if (posOffsetStr != null && !posOffsetStr.isBlank()) {
            posOffsetStr = LuckyTemplateVars.evaluate(posOffsetStr, evalCtx);
            double[] offset = parsePosOffset(posOffsetStr);
            baseX += offset[0];
            baseY += offset[1];
            baseZ += offset[2];
        }

        // posOffsetY — additional Y offset, evaluated per-entity (e.g. posOffsetY=#rand(5,400))
        String posOffsetYStr = drop.getString("posOffsetY");
        if (posOffsetYStr != null && !posOffsetYStr.isBlank()) {
            posOffsetYStr = LuckyTemplateVars.evaluate(posOffsetYStr, evalCtx);
            baseY += LuckyTemplateVars.evalArithmetic(posOffsetYStr, 0);
        }

        return new Vec3d(baseX, baseY, baseZ);
    }

    /** Parses an offset string like "1.23 0 4.56" or "(1,0,4)" into [dx, dy, dz]. */
    private static double[] parsePosOffset(String s) {
        if (s == null || s.isBlank()) return new double[]{0, 0, 0};
        s = s.trim().replaceAll("[(){}]", "");
        // Try space-separated
        String[] parts = s.contains(",") ? s.split(",") : s.split("\\s+");
        double dx = 0, dy = 0, dz = 0;
        try { if (parts.length > 0) dx = Double.parseDouble(parts[0].trim()); } catch (NumberFormatException ignored) {}
        try { if (parts.length > 1) dy = Double.parseDouble(parts[1].trim()); } catch (NumberFormatException ignored) {}
        try { if (parts.length > 2) dz = Double.parseDouble(parts[2].trim()); } catch (NumberFormatException ignored) {}
        return new double[]{dx, dy, dz};
    }

    private static int parseIntAttr(LuckyDropLine drop, String key, LuckyTemplateVars.EvalContext evalCtx, int fallback) {
        String s = drop.getString(key);
        if (s == null) return fallback;
        s = LuckyTemplateVars.evaluate(s, evalCtx);
        try { return (int) Math.round(LuckyTemplateVars.evalArithmetic(s, fallback)); }
        catch (Exception ignored) { return fallback; }
    }
}

