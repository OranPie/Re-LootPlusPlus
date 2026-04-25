package ie.orangep.reLootplusplus.bootstrap;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.pack.AddonPack;
import net.minecraft.resource.ResourcePack;
import net.minecraft.server.command.ServerCommandSource;
import com.mojang.brigadier.CommandDispatcher;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Interface bridge between the core mod and the optional LuckyBlock compatibility module.
 * This interface lives in the main source set and contains no lucky-package types.
 */
public interface LuckyBootstrap {

    /** Summarises a single addon pack's Lucky data for UI display. */
    record LuckyPackView(
        int dropCount,
        int bowDropCount,
        int swordDropCount,
        int potionDropCount,
        int structureCount,
        int naturalGenCount,
        boolean hasPluginInit,
        @Nullable String propertiesSummary
    ) {}

    /** View of a single parsed drop line — primitives/strings only. */
    record DropLineView(
        int index,
        String type,
        String label,
        int luckWeight,
        float chance,
        boolean isGroup,
        int groupSize,
        @Nullable String displayString
    ) {}

    /** View of a single structure entry. */
    record StructureView(
        String id,
        String file,
        int centerX,
        int centerY,
        int centerZ
    ) {}

    /** Called during bootstrap Phase 3 — parse addon drops.txt, luck_crafting.txt. */
    void onPacksLoaded(List<AddonPack> packs, LegacyWarnReporter warnReporter);

    /** Called during bootstrap Phase 4 — register Lucky blocks/items/world-gen. */
    void registerContent(boolean naturalGenEnabled);

    /** Register admin commands (lppdrop …). */
    void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, boolean dedicated);

    /** Returns Lucky data view for the given pack id, or null if not a Lucky pack. */
    @Nullable LuckyPackView getPackView(String packId);

    /** Returns parsed drop-line views for the given pack id (empty list if none). */
    List<DropLineView> getDropViews(String packId);

    /** Returns structure views for the given pack id (empty list if none). */
    List<StructureView> getStructureViews(String packId);

    /** Returns a synthetic resource pack for addon Lucky blocks, or null if unavailable. */
    @Nullable ResourcePack createAddonBlockResourcePack();

    /** No-op implementation used when luckyCompat module is absent. */
    LuckyBootstrap NOOP = new LuckyBootstrap() {
        @Override public void onPacksLoaded(List<AddonPack> packs, LegacyWarnReporter w) {}
        @Override public void registerContent(boolean naturalGenEnabled) {}
        @Override public void registerCommands(CommandDispatcher<ServerCommandSource> d, boolean ded) {}
        @Override public @Nullable LuckyPackView getPackView(String packId) { return null; }
        @Override public List<DropLineView> getDropViews(String packId) { return List.of(); }
        @Override public List<StructureView> getStructureViews(String packId) { return List.of(); }
        @Override public @Nullable ResourcePack createAddonBlockResourcePack() { return null; }
    };
}
