package ie.orangep.reLootplusplus.config.model.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BlockAdditions {
    private final List<GenericBlockDef> genericBlocks = new ArrayList<>();
    private final List<PlantBlockDef> plantBlocks = new ArrayList<>();
    private final List<CropBlockDef> cropBlocks = new ArrayList<>();
    private final List<CakeBlockDef> cakeBlocks = new ArrayList<>();
    private final List<RawBlockAdditionDef> rawBlocks = new ArrayList<>();

    public void addGeneric(GenericBlockDef def) {
        genericBlocks.add(def);
    }

    public void addPlant(PlantBlockDef def) {
        plantBlocks.add(def);
    }

    public void addCrop(CropBlockDef def) {
        cropBlocks.add(def);
    }

    public void addCake(CakeBlockDef def) {
        cakeBlocks.add(def);
    }

    public void addRaw(RawBlockAdditionDef def) {
        rawBlocks.add(def);
    }

    public List<GenericBlockDef> genericBlocks() {
        return Collections.unmodifiableList(genericBlocks);
    }

    public List<PlantBlockDef> plantBlocks() {
        return Collections.unmodifiableList(plantBlocks);
    }

    public List<CropBlockDef> cropBlocks() {
        return Collections.unmodifiableList(cropBlocks);
    }

    public List<CakeBlockDef> cakeBlocks() {
        return Collections.unmodifiableList(cakeBlocks);
    }

    public List<RawBlockAdditionDef> rawBlocks() {
        return Collections.unmodifiableList(rawBlocks);
    }
}
