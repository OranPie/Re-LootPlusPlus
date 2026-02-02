package ie.orangep.reLootplusplus.diagnostic;

import java.util.Objects;

public final class WarnKey {
    private final String type;
    private final String detail;
    private final String location;

    public WarnKey(String type, String detail, String location) {
        this.type = type;
        this.detail = detail;
        this.location = location;
    }

    public static WarnKey of(String type, String detail, SourceLoc loc) {
        String where = loc == null ? "" : loc.formatShort();
        return new WarnKey(type, detail, where);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WarnKey)) {
            return false;
        }
        WarnKey warnKey = (WarnKey) o;
        return Objects.equals(type, warnKey.type)
            && Objects.equals(detail, warnKey.detail)
            && Objects.equals(location, warnKey.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, detail, location);
    }
}
