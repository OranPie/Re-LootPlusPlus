package ie.orangep.reLootplusplus.config.model.item;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

import java.util.List;

public final class FoodDef {
    private final String itemId;
    private final String displayName;
    private final boolean shiny;
    private final int hunger;
    private final float saturation;
    private final boolean wolvesEat;
    private final boolean alwaysEdible;
    private final int timeToEat;
    private final List<FoodEffectSpec> effects;
    private final SourceLoc sourceLoc;

    public FoodDef(
        String itemId,
        String displayName,
        boolean shiny,
        int hunger,
        float saturation,
        boolean wolvesEat,
        boolean alwaysEdible,
        int timeToEat,
        List<FoodEffectSpec> effects,
        SourceLoc sourceLoc
    ) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.shiny = shiny;
        this.hunger = hunger;
        this.saturation = saturation;
        this.wolvesEat = wolvesEat;
        this.alwaysEdible = alwaysEdible;
        this.timeToEat = timeToEat;
        this.effects = effects == null ? List.of() : List.copyOf(effects);
        this.sourceLoc = sourceLoc;
    }

    public String itemId() {
        return itemId;
    }

    public String displayName() {
        return displayName;
    }

    public boolean shiny() {
        return shiny;
    }

    public int hunger() {
        return hunger;
    }

    public float saturation() {
        return saturation;
    }

    public boolean wolvesEat() {
        return wolvesEat;
    }

    public boolean alwaysEdible() {
        return alwaysEdible;
    }

    public int timeToEat() {
        return timeToEat;
    }

    public List<FoodEffectSpec> effects() {
        return effects;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
