package ie.orangep.reLootplusplus.legacy.mapping;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;

/**
 * Patches block-entity (tile-entity) NBT from the 1.8.9 schematic format to
 * the 1.18.2 format.  Called by {@link ie.orangep.reLootplusplus.lucky.structure.SchematicReader}
 * after the TE id has already been remapped via {@link LegacyBlockIdMapper#mapTileEntityId}.
 *
 * <h3>Handled cases</h3>
 * <ul>
 *   <li><b>Mob spawner</b> — {@code EntityId}, {@code SpawnData}, {@code SpawnPotentials}</li>
 *   <li><b>Chest / trapped chest / dispenser / dropper / hopper / barrel</b> — item stacks in
 *       {@code Items} list (slot items)</li>
 *   <li><b>Sign</b> — legacy plain-text {@code Text1-4} → JSON component lines</li>
 * </ul>
 */
public final class LegacyTileEntityNbtFixer {

    private LegacyTileEntityNbtFixer() {}

    /**
     * Mutates {@code te} in-place to fix legacy NBT formats.
     *
     * @param te       the tile-entity NBT (already has a modern {@code id} key)
     * @param reporter warn reporter (may be null — caller should supply one)
     * @param loc      source location for warn messages
     */
    public static void fix(NbtCompound te, LegacyWarnReporter reporter, SourceLoc loc) {
        if (te == null) return;
        String id = te.getString("id");
        Log.trace("Legacy", "TileEntityNbt: fix id={}", id);
        switch (id) {
            case "minecraft:mob_spawner" -> fixSpawner(te, reporter, loc);
            case "minecraft:chest",
                 "minecraft:trapped_chest",
                 "minecraft:dispenser",
                 "minecraft:dropper",
                 "minecraft:hopper",
                 "minecraft:barrel",
                 "minecraft:shulker_box" -> fixInventory(te, reporter, loc);
            case "minecraft:sign" -> fixSign(te, reporter, loc);
        }
    }

    // ── Mob Spawner ───────────────────────────────────────────────────────────

    /**
     * Converts 1.8.9 spawner NBT to 1.18.2 format.
     *
     * <pre>
     * 1.8:   EntityId: "Zombie"
     *        SpawnData: { id: "Zombie", ... }
     *        SpawnPotentials: [ { Type: "Zombie", Weight: 1, Properties: {...} } ]
     *
     * 1.18:  SpawnData: { entity: { id: "minecraft:zombie", ... } }
     *        SpawnPotentials: [ { data: { entity: { id: "minecraft:zombie" } }, weight: 1 } ]
     * </pre>
     */
    private static void fixSpawner(NbtCompound te, LegacyWarnReporter reporter, SourceLoc loc) {
        String context = loc != null ? loc.packId() + ":" + loc.innerPath() : "spawner";

        // ── 1. Resolve base entity id ─────────────────────────────────────────
        // Priority: existing SpawnData > EntityId
        String baseEntityId = null;
        NbtCompound baseEntityNbt = null;

        if (te.contains("SpawnData", NbtElement.COMPOUND_TYPE)) {
            NbtCompound sd = te.getCompound("SpawnData");

            if (sd.contains("entity", NbtElement.COMPOUND_TYPE)) {
                // Already in 1.18 format — just fix the entity id inside
                NbtCompound entityNbt = sd.getCompound("entity");
                LegacyEntityIdFixer.fixEntityId(entityNbt, reporter, context + " SpawnData.entity");
                // Still fix SpawnPotentials below
                fixSpawnPotentials(te, reporter, context);
                te.remove("EntityId");
                return;
            }

            // 1.8 format: SpawnData has a direct "id" key
            if (sd.contains("id", NbtElement.STRING_TYPE)) {
                baseEntityId = sd.getString("id");
                baseEntityNbt = sd.copy();
            }
        }

        if (baseEntityId == null && te.contains("EntityId", NbtElement.STRING_TYPE)) {
            baseEntityId = te.getString("EntityId");
            baseEntityNbt = new NbtCompound();
            baseEntityNbt.putString("id", baseEntityId);
        }

        // ── 2. Build modern SpawnData ─────────────────────────────────────────
        if (baseEntityId != null && baseEntityNbt != null) {
            // Fix the entity id in the copied NBT
            Log.trace("Legacy", "Spawner: migrating SpawnData EntityId={}", baseEntityId);
            LegacyEntityIdFixer.fixEntityId(baseEntityNbt, reporter, context + " SpawnData");
            NbtCompound newSpawnData = new NbtCompound();
            newSpawnData.put("entity", baseEntityNbt);
            te.put("SpawnData", newSpawnData);
            if (reporter != null) {
                reporter.warn("LegacyNBT",
                    "spawner SpawnData migrated (EntityId='" + baseEntityId + "')",
                    loc != null ? loc : new SourceLoc("?","?","spawner",0,""));
            }
        }

        // ── 3. Remove obsolete keys ───────────────────────────────────────────
        te.remove("EntityId");

        // ── 4. Fix SpawnPotentials ────────────────────────────────────────────
        fixSpawnPotentials(te, reporter, context);
    }

    private static void fixSpawnPotentials(NbtCompound te, LegacyWarnReporter reporter, String context) {
        if (!te.contains("SpawnPotentials", NbtElement.LIST_TYPE)) return;
        NbtList oldList = te.getList("SpawnPotentials", NbtElement.COMPOUND_TYPE);
        if (oldList.isEmpty()) return;

        NbtList newList = new NbtList();
        for (int i = 0; i < oldList.size(); i++) {
            NbtCompound potential = oldList.getCompound(i);

            if (potential.contains("data", NbtElement.COMPOUND_TYPE)) {
                // Already in 1.18 format — fix entity id inside
                NbtCompound data = potential.getCompound("data");
                if (data.contains("entity", NbtElement.COMPOUND_TYPE)) {
                    LegacyEntityIdFixer.fixEntityId(data.getCompound("entity"), reporter, context + " SpawnPotentials.data.entity");
                }
                newList.add(potential);
                continue;
            }

            // 1.8 format: { Type: "Zombie", Weight: 1, Properties: {...} }
            NbtCompound newPotential = new NbtCompound();

            // weight (1.8: "Weight" int, 1.18: "weight" int)
            int weight = potential.contains("Weight") ? potential.getInt("Weight") : 1;
            newPotential.putInt("weight", weight);

            // entity
            NbtCompound entityNbt;
            if (potential.contains("Properties", NbtElement.COMPOUND_TYPE)) {
                entityNbt = potential.getCompound("Properties").copy();
            } else {
                entityNbt = new NbtCompound();
            }
            String type = potential.getString("Type");
            if (type != null && !type.isEmpty()) {
                entityNbt.putString("id", type);
            }
            LegacyEntityIdFixer.fixEntityId(entityNbt, reporter, context + " SpawnPotentials[" + i + "]");

            NbtCompound data = new NbtCompound();
            data.put("entity", entityNbt);
            newPotential.put("data", data);

            newList.add(newPotential);
        }
        te.put("SpawnPotentials", newList);
    }

    // ── Chest / Inventory ─────────────────────────────────────────────────────

    /**
     * Fixes item stacks stored in the {@code Items} list of container block entities.
     * Each slot item's {@code id} is mapped through {@link LegacyItemNbtFixer}.
     */
    private static void fixInventory(NbtCompound te, LegacyWarnReporter reporter, SourceLoc loc) {
        if (!te.contains("Items", NbtElement.LIST_TYPE)) return;
        NbtList items = te.getList("Items", NbtElement.COMPOUND_TYPE);
        String context = loc != null ? loc.packId() + ":" + loc.innerPath() : "inventory";
        for (int i = 0; i < items.size(); i++) {
            NbtCompound item = items.getCompound(i);
            LegacyItemNbtFixer.fixItemStack(item, reporter, context + " slot " + i);
        }
    }

    // ── Sign ──────────────────────────────────────────────────────────────────

    /**
     * Converts legacy plain-text sign lines ({@code Text1} … {@code Text4}) to
     * 1.18.2 JSON text component format.
     */
    private static void fixSign(NbtCompound te, LegacyWarnReporter reporter, SourceLoc loc) {
        for (int i = 1; i <= 4; i++) {
            String key = "Text" + i;
            if (!te.contains(key, NbtElement.STRING_TYPE)) continue;
            String raw = te.getString(key);
            if (raw == null || raw.isEmpty() || raw.startsWith("{")) continue;
            // Wrap plain text in JSON literal component
            String escaped = raw.replace("\\", "\\\\").replace("\"", "\\\"");
            te.putString(key, "{\"text\":\"" + escaped + "\"}");
        }
    }
}
