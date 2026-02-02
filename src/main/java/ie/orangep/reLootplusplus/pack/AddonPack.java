package ie.orangep.reLootplusplus.pack;

import java.nio.file.Path;

public final class AddonPack {
    private final String id;
    private final Path zipPath;

    public AddonPack(String id, Path zipPath) {
        this.id = id;
        this.zipPath = zipPath;
    }

    public String id() {
        return id;
    }

    public Path zipPath() {
        return zipPath;
    }
}
