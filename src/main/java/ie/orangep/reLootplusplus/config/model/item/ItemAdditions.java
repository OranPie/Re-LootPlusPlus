package ie.orangep.reLootplusplus.config.model.item;

import java.util.ArrayList;
import java.util.List;

public final class ItemAdditions {
    private final List<GenericItemDef> genericItems = new ArrayList<>();
    private final List<MaterialDef> materials = new ArrayList<>();
    private final List<SwordDef> swords = new ArrayList<>();
    private final List<ToolDef> tools = new ArrayList<>();
    private final List<ArmorDef> armors = new ArrayList<>();
    private final List<FoodDef> foods = new ArrayList<>();
    private final List<BowDef> bows = new ArrayList<>();
    private final List<GunDef> guns = new ArrayList<>();
    private final List<MultitoolDef> multitools = new ArrayList<>();

    public void addGeneric(GenericItemDef def) {
        genericItems.add(def);
    }

    public void addMaterial(MaterialDef def) {
        materials.add(def);
    }

    public void addSword(SwordDef def) {
        swords.add(def);
    }

    public void addTool(ToolDef def) {
        tools.add(def);
    }

    public void addArmor(ArmorDef def) {
        armors.add(def);
    }

    public void addFood(FoodDef def) {
        foods.add(def);
    }

    public void addBow(BowDef def) {
        bows.add(def);
    }

    public void addGun(GunDef def) {
        guns.add(def);
    }

    public void addMultitool(MultitoolDef def) {
        multitools.add(def);
    }

    public List<GenericItemDef> genericItems() {
        return List.copyOf(genericItems);
    }

    public List<MaterialDef> materials() {
        return List.copyOf(materials);
    }

    public List<SwordDef> swords() {
        return List.copyOf(swords);
    }

    public List<ToolDef> tools() {
        return List.copyOf(tools);
    }

    public List<ArmorDef> armors() {
        return List.copyOf(armors);
    }

    public List<FoodDef> foods() {
        return List.copyOf(foods);
    }

    public List<BowDef> bows() {
        return List.copyOf(bows);
    }

    public List<GunDef> guns() {
        return List.copyOf(guns);
    }

    public List<MultitoolDef> multitools() {
        return List.copyOf(multitools);
    }

    public MaterialDef findMaterial(String itemId, int meta) {
        if (itemId == null) {
            return null;
        }
        MaterialDef wildcard = null;
        for (MaterialDef def : materials) {
            if (!itemId.equals(def.itemId())) {
                continue;
            }
            if (def.meta() == meta) {
                return def;
            }
            if (def.meta() == 32767) {
                wildcard = def;
            }
        }
        return wildcard;
    }
}
