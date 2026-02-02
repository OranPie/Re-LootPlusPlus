package ie.orangep.reLootplusplus.legacy.mapping;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public final class LegacyChestTypeMapper {
    private static final String FILE_NAME = "legacy_chest_types.json";

    private final Map<String, String> mapping;
    private final LegacyWarnReporter warnReporter;

    public LegacyChestTypeMapper(LegacyWarnReporter warnReporter) {
        this.warnReporter = warnReporter;
        this.mapping = loadMapping();
    }

    public Identifier resolve(String chestType, SourceLoc loc) {
        String target = mapping.get(chestType);
        if (target == null) {
            warnReporter.warnOnce("LegacyChestType", "unmapped chest type " + chestType, loc);
            return null;
        }
        Identifier id = Identifier.tryParse(target);
        if (id == null) {
            warnReporter.warn("LegacyChestType", "bad mapping " + chestType + " -> " + target, loc);
            return null;
        }
        return id;
    }

    private Map<String, String> loadMapping() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            return Collections.emptyMap();
        }
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, String>>() { }.getType();
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, String> data = gson.fromJson(reader, type);
            if (data == null) {
                return Collections.emptyMap();
            }
            return data;
        } catch (IOException e) {
            return Collections.emptyMap();
        }
    }
}
