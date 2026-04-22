package ie.orangep.reLootplusplus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import ie.orangep.reLootplusplus.diagnostic.Log;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AddonDisableStore {
    private static final String FILE_NAME = "relootplusplus_addons.json";

    private static final class State {
        List<String> disabled = new ArrayList<>();
    }

    private AddonDisableStore() {
    }

    public static boolean isEnabled(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        State state = loadState();
        return state.disabled.stream().noneMatch(entry -> entry.equalsIgnoreCase(id));
    }

    public static void setEnabled(String id, boolean enabled) {
        if (id == null || id.isBlank()) {
            return;
        }
        State state = loadState();
        state.disabled.removeIf(entry -> entry.equalsIgnoreCase(id));
        if (!enabled) {
            state.disabled.add(id);
        }
        saveState(state);
    }

    public static List<String> disabledList() {
        State state = loadState();
        return List.copyOf(state.disabled);
    }

    private static State loadState() {
        Path file = configFile();
        State state = new State();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        if (Files.exists(file)) {
            try {
                String raw = Files.readString(file, StandardCharsets.UTF_8);
                State loaded = gson.fromJson(raw, State.class);
                if (loaded != null && loaded.disabled != null) {
                    state.disabled = new ArrayList<>(loaded.disabled);
                }
            } catch (Exception e) {
                Log.error("Config", "Failed to read addon state {}, using defaults", file, e);
            }
            return state;
        }
        migrateFromConfig(state);
        saveState(state);
        return state;
    }

    private static void saveState(State state) {
        Path file = configFile();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(state), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.error("Config", "Failed to write addon state {}", file, e);
        }
    }

    private static void migrateFromConfig(State state) {
        ReLootPlusPlusConfig config = ReLootPlusPlusConfig.load();
        if (config.disabledAddonPacks == null || config.disabledAddonPacks.isEmpty()) {
            return;
        }
        for (String entry : config.disabledAddonPacks) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            state.disabled.add(entry.trim().toLowerCase(Locale.ROOT));
        }
    }

    private static Path configFile() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        return configDir.resolve(FILE_NAME);
    }
}
