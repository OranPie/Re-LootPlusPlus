package ie.orangep.reLootplusplus.bootstrap;

import ie.orangep.reLootplusplus.command.GiveLuckyCommand;
import ie.orangep.reLootplusplus.command.LuckyDropEvalCommand;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.lucky.crafting.LuckyLuckCraftingLoader;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.loader.LuckyAddonData;
import ie.orangep.reLootplusplus.lucky.loader.LuckyAddonLoader;
import ie.orangep.reLootplusplus.lucky.loader.LuckyAddonProperties;
import ie.orangep.reLootplusplus.lucky.loader.LuckyStructureEntry;
import ie.orangep.reLootplusplus.lucky.registry.AddonLuckyRegistrar;
import ie.orangep.reLootplusplus.lucky.registry.LuckyRegistrar;
import ie.orangep.reLootplusplus.lucky.worldgen.LuckyNaturalGenRegistrar;
import ie.orangep.reLootplusplus.pack.AddonPack;
import ie.orangep.reLootplusplus.resourcepack.AddonBlockResourcePack;
import net.minecraft.resource.ResourcePack;
import net.minecraft.server.command.ServerCommandSource;
import com.mojang.brigadier.CommandDispatcher;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LuckyBlock-compatible implementation of {@link LuckyBootstrap}.
 * Registered via the {@code re-lpp:lucky} Fabric entrypoint.
 */
public final class LuckyBootstrapImpl implements LuckyBootstrap {

    @Override
    public void onPacksLoaded(List<AddonPack> packs, LegacyWarnReporter warnReporter) {
        LuckyAddonLoader.load(packs, warnReporter);
        LuckyLuckCraftingLoader.load(packs, warnReporter);
    }

    @Override
    public void registerContent(boolean naturalGenEnabled) {
        LuckyRegistrar.register();
        AddonLuckyRegistrar.register(LuckyAddonLoader.getAddonDataList());
        if (naturalGenEnabled) {
            LuckyNaturalGenRegistrar.register(LuckyAddonLoader.getAddonDataList());
        }
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, boolean dedicated) {
        LuckyDropEvalCommand.register(dispatcher::register);
        GiveLuckyCommand.register(dispatcher);
    }

    @Override
    public @Nullable LuckyPackView getPackView(String packId) {
        LuckyAddonData data = findData(packId);
        if (data == null) return null;
        String propsSummary = null;
        if (data.properties() != null) {
            LuckyAddonProperties p = data.properties();
            propsSummary = "spawnRate=" + p.spawnRate() + "  strChance=" + p.structureChance()
                + "  creative=" + p.doDropsOnCreativeMode();
        }
        return new LuckyPackView(
            data.parsedDrops().size(),
            data.parsedBowDrops().size(),
            data.parsedSwordDrops().size(),
            data.parsedPotionDrops().size(),
            data.structureEntries().size(),
            data.naturalGenEntries().size(),
            data.pluginInit() != null,
            propsSummary
        );
    }

    @Override
    public List<DropLineView> getDropViews(String packId) {
        LuckyAddonData data = findData(packId);
        if (data == null) return List.of();
        List<LuckyDropLine> drops = data.parsedDrops();
        List<DropLineView> views = new ArrayList<>();
        for (int i = 0; i < drops.size(); i++) {
            LuckyDropLine d = drops.get(i);
            String type = d.isGroup() ? "group" : (d.type() != null ? d.type() : "?");
            String label = buildLabel(d);
            views.add(new DropLineView(
                i, type, label, d.luckWeight(), d.chance(), d.isGroup(),
                d.isGroup() && d.groupEntries() != null ? d.groupEntries().size() : 0,
                d.toDisplayString()
            ));
        }
        return views;
    }

    @Override
    public List<StructureView> getStructureViews(String packId) {
        LuckyAddonData data = findData(packId);
        if (data == null) return List.of();
        return data.structureEntries().stream()
            .map(s -> new StructureView(s.id(), s.file(), s.centerX(), s.centerY(), s.centerZ()))
            .collect(Collectors.toList());
    }

    @Override
    public @Nullable ResourcePack createAddonBlockResourcePack() {
        return new AddonBlockResourcePack();
    }

    private static @Nullable LuckyAddonData findData(String packId) {
        return LuckyAddonLoader.getAddonDataList().stream()
            .filter(d -> d.packId().equals(packId)).findFirst().orElse(null);
    }

    private static String buildLabel(LuckyDropLine d) {
        if (d.isGroup()) return "group (" + (d.groupEntries() != null ? d.groupEntries().size() : 0) + ")";
        String id = d.rawId();
        if (id != null && !id.isBlank()) return id;
        String cmd = d.getString("command");
        if (cmd != null && !cmd.isBlank()) return "/" + (cmd.length() > 40 ? cmd.substring(0, 40) + "\u2026" : cmd);
        return "(no id)";
    }
}
