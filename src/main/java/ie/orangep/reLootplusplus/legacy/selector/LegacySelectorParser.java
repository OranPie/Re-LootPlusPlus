package ie.orangep.reLootplusplus.legacy.selector;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.registry.Registry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class LegacySelectorParser {
    private static final String TYPE_PLAYER_NEAREST = "@p";
    private static final String TYPE_PLAYER_ALL = "@a";
    private static final String TYPE_PLAYER_RANDOM = "@r";
    private static final String TYPE_ENTITY_ALL = "@e";

    public List<Entity> select(String selectorString, SelectorContext ctx) {
        if (selectorString == null || selectorString.isEmpty()) {
            return List.of();
        }
        SelectorParts parts = parse(selectorString, ctx.warnReporter(), ctx.sourceLoc());
        if (parts == null) {
            return List.of();
        }

        List<Entity> candidates = getCandidates(parts.kind, ctx);
        if (candidates.isEmpty()) {
            return candidates;
        }

        BlockPos origin = resolveOrigin(parts, ctx.origin());
        Vec3d originVec = Vec3d.of(origin);

        List<Entity> filtered = new ArrayList<>(candidates);
        applyBasicFilters(filtered, parts, ctx);
        applyBoxFilter(filtered, parts, originVec);
        applyRadiusFilter(filtered, parts, originVec);
        if (!applyScoreFilters(filtered, parts, ctx)) {
            return List.of();
        }

        applySortingAndLimit(filtered, parts, originVec, ctx.random());
        return filtered;
    }

    private void applyBasicFilters(List<Entity> entities, SelectorParts parts, SelectorContext ctx) {
        if (parts.typeFilter != null) {
            entities.removeIf(entity -> !matchesType(entity, parts.typeFilter));
        }
        if (parts.nameFilter != null) {
            entities.removeIf(entity -> !matchesString(entity.getName().getString(), parts.nameFilter));
        }
        if (parts.teamFilter != null) {
            entities.removeIf(entity -> !matchesTeam(entity, parts.teamFilter));
        }
        if (parts.gameModeFilter != null) {
            entities.removeIf(entity -> !matchesGameMode(entity, parts.gameModeFilter));
        }
        if (parts.levelMin != null) {
            entities.removeIf(entity -> !matchesLevel(entity, parts.levelMin, true));
        }
        if (parts.levelMax != null) {
            entities.removeIf(entity -> !matchesLevel(entity, parts.levelMax, false));
        }
    }

    private void applyBoxFilter(List<Entity> entities, SelectorParts parts, Vec3d origin) {
        if (parts.dx == null && parts.dy == null && parts.dz == null) {
            return;
        }
        double dx = parts.dx == null ? 0 : parts.dx;
        double dy = parts.dy == null ? 0 : parts.dy;
        double dz = parts.dz == null ? 0 : parts.dz;
        double x1 = origin.x;
        double y1 = origin.y;
        double z1 = origin.z;
        double x2 = origin.x + dx;
        double y2 = origin.y + dy;
        double z2 = origin.z + dz;
        Box box = new Box(
            Math.min(x1, x2),
            Math.min(y1, y2),
            Math.min(z1, z2),
            Math.max(x1, x2),
            Math.max(y1, y2),
            Math.max(z1, z2)
        );
        entities.removeIf(entity -> !box.contains(entity.getPos()));
    }

    private void applyRadiusFilter(List<Entity> entities, SelectorParts parts, Vec3d origin) {
        if (parts.r == null && parts.rm == null) {
            return;
        }
        double max = parts.r == null ? Double.MAX_VALUE : parts.r;
        double min = parts.rm == null ? 0.0 : parts.rm;
        double maxSq = max * max;
        double minSq = min * min;
        entities.removeIf(entity -> {
            double distSq = entity.getPos().squaredDistanceTo(origin);
            return distSq < minSq || distSq > maxSq;
        });
    }

    private boolean applyScoreFilters(List<Entity> entities, SelectorParts parts, SelectorContext ctx) {
        if (parts.scoreFilters.isEmpty()) {
            return true;
        }
        Scoreboard scoreboard = ctx.world().getScoreboard();
        for (ScoreFilter filter : parts.scoreFilters.values()) {
            ScoreboardObjective objective = scoreboard.getObjective(filter.objective());
            if (objective == null) {
                ctx.warnReporter().warn("LegacyScore", "objective missing " + filter.objective(), ctx.sourceLoc());
                entities.clear();
                return false;
            }
        }

        entities.removeIf(entity -> {
            String name = entity.getName().getString();
            for (ScoreFilter filter : parts.scoreFilters.values()) {
                ScoreboardObjective objective = scoreboard.getObjective(filter.objective());
                if (objective == null) {
                    return true;
                }
                ScoreboardPlayerScore score = scoreboard.getPlayerScore(name, objective);
                int value = score.getScore();
                if (filter.min() != null && value < filter.min()) {
                    return true;
                }
                if (filter.max() != null && value > filter.max()) {
                    return true;
                }
            }
            return false;
        });
        return true;
    }

    private void applySortingAndLimit(List<Entity> entities, SelectorParts parts, Vec3d origin, Random random) {
        if (TYPE_PLAYER_NEAREST.equals(parts.kind)) {
            entities.sort(Comparator
                .comparingDouble((Entity e) -> e.getPos().squaredDistanceTo(origin))
                .thenComparingInt(Entity::getId));
        } else if (TYPE_PLAYER_RANDOM.equals(parts.kind)) {
            Collections.shuffle(entities, random);
        } else {
            entities.sort(Comparator.comparingInt(Entity::getId));
        }

        int limit = parts.c == null ? defaultLimit(parts.kind) : parts.c;
        if (limit == 0) {
            entities.clear();
            return;
        }
        if (limit > 0) {
            if (entities.size() > limit) {
                entities.subList(limit, entities.size()).clear();
            }
            return;
        }

        int count = Math.abs(limit);
        if (count >= entities.size()) {
            return;
        }
        entities.subList(0, entities.size() - count).clear();
    }

    private int defaultLimit(String kind) {
        if (TYPE_PLAYER_NEAREST.equals(kind) || TYPE_PLAYER_RANDOM.equals(kind)) {
            return 1;
        }
        return 0;
    }

    private List<Entity> getCandidates(String kind, SelectorContext ctx) {
        if (TYPE_PLAYER_ALL.equals(kind) || TYPE_PLAYER_NEAREST.equals(kind) || TYPE_PLAYER_RANDOM.equals(kind)) {
            return new ArrayList<>(ctx.world().getPlayers());
        }
        if (TYPE_ENTITY_ALL.equals(kind)) {
            return new ArrayList<>(ctx.world().getEntitiesByType(TypeFilter.instanceOf(Entity.class), entity -> true));
        }
        return List.of();
    }

    private BlockPos resolveOrigin(SelectorParts parts, BlockPos fallback) {
        int x = parts.x != null ? parts.x : fallback.getX();
        int y = parts.y != null ? parts.y : fallback.getY();
        int z = parts.z != null ? parts.z : fallback.getZ();
        return new BlockPos(x, y, z);
    }

    private boolean matchesType(Entity entity, FilterValue filter) {
        String id = Registry.ENTITY_TYPE.getId(entity.getType()).toString();
        String path = Registry.ENTITY_TYPE.getId(entity.getType()).getPath();
        boolean matches = equalsIgnoreCase(id, filter.value())
            || equalsIgnoreCase(path, filter.value())
            || equalsIgnoreCase(entity.getType().getName().getString(), filter.value());
        return filter.negated() ? !matches : matches;
    }

    private boolean matchesString(String value, FilterValue filter) {
        boolean matches = equalsIgnoreCase(value, filter.value());
        return filter.negated() ? !matches : matches;
    }

    private boolean matchesTeam(Entity entity, FilterValue filter) {
        String name = entity.getScoreboardTeam() == null ? "" : entity.getScoreboardTeam().getName();
        boolean matches = equalsIgnoreCase(name, filter.value());
        return filter.negated() ? !matches : matches;
    }

    private boolean matchesGameMode(Entity entity, FilterValue filter) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            return false;
        }
        int mode = player.interactionManager.getGameMode().getId();
        boolean matches = false;
        try {
            matches = Integer.parseInt(filter.value()) == mode;
        } catch (NumberFormatException ignored) {
        }
        return filter.negated() ? !matches : matches;
    }

    private boolean matchesLevel(Entity entity, int threshold, boolean min) {
        if (!(entity instanceof PlayerEntity player)) {
            return false;
        }
        if (min) {
            return player.experienceLevel >= threshold;
        }
        return player.experienceLevel <= threshold;
    }

    private boolean equalsIgnoreCase(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.equalsIgnoreCase(b);
    }

    private SelectorParts parse(String raw, LegacyWarnReporter warnReporter, SourceLoc loc) {
        String trimmed = raw.trim();
        String kind;
        String argsRaw = null;

        int bracketStart = trimmed.indexOf('[');
        if (bracketStart >= 0 && trimmed.endsWith("]")) {
            kind = trimmed.substring(0, bracketStart);
            argsRaw = trimmed.substring(bracketStart + 1, trimmed.length() - 1);
        } else {
            kind = trimmed;
        }

        if (!TYPE_PLAYER_NEAREST.equals(kind)
            && !TYPE_PLAYER_ALL.equals(kind)
            && !TYPE_PLAYER_RANDOM.equals(kind)
            && !TYPE_ENTITY_ALL.equals(kind)) {
            warnReporter.warn("LegacySelector", "unknown selector " + kind, loc);
            return null;
        }

        SelectorParts parts = new SelectorParts(kind);
        if (argsRaw == null || argsRaw.isEmpty()) {
            return parts;
        }

        String[] args = argsRaw.split(",");
        for (String arg : args) {
            int eq = arg.indexOf('=');
            if (eq <= 0) {
                warnReporter.warn("LegacySelector", "bad arg " + arg, loc);
                continue;
            }
            String key = arg.substring(0, eq);
            String value = arg.substring(eq + 1);
            parts.apply(key, value, warnReporter, loc);
        }
        return parts;
    }

    private static final class SelectorParts {
        private final String kind;
        private Integer x;
        private Integer y;
        private Integer z;
        private Double r;
        private Double rm;
        private Double dx;
        private Double dy;
        private Double dz;
        private Integer c;
        private FilterValue typeFilter;
        private FilterValue nameFilter;
        private FilterValue teamFilter;
        private FilterValue gameModeFilter;
        private Integer levelMax;
        private Integer levelMin;
        private final Map<String, ScoreFilter> scoreFilters = new HashMap<>();

        private SelectorParts(String kind) {
            this.kind = kind;
        }

        private void apply(String key, String value, LegacyWarnReporter warnReporter, SourceLoc loc) {
            if (key.startsWith("score_")) {
                warnReporter.warnOnce("LegacySelectorParam", "score_* used", loc);
                parseScore(key, value, warnReporter, loc);
                return;
            }

            switch (key) {
                case "x" -> x = parseInt(value, warnReporter, loc, key);
                case "y" -> y = parseInt(value, warnReporter, loc, key);
                case "z" -> z = parseInt(value, warnReporter, loc, key);
                case "r" -> {
                    warnReporter.warnOnce("LegacySelectorParam", "r used", loc);
                    r = parseDouble(value, warnReporter, loc, key);
                }
                case "rm" -> {
                    warnReporter.warnOnce("LegacySelectorParam", "rm used", loc);
                    rm = parseDouble(value, warnReporter, loc, key);
                }
                case "dx" -> dx = parseDouble(value, warnReporter, loc, key);
                case "dy" -> dy = parseDouble(value, warnReporter, loc, key);
                case "dz" -> dz = parseDouble(value, warnReporter, loc, key);
                case "c" -> {
                    warnReporter.warnOnce("LegacySelectorParam", "c used", loc);
                    c = parseInt(value, warnReporter, loc, key);
                }
                case "type" -> typeFilter = parseFilter(value, warnReporter, loc, "type");
                case "name" -> nameFilter = parseFilter(value, warnReporter, loc, "name");
                case "team" -> teamFilter = parseFilter(value, warnReporter, loc, "team");
                case "m" -> gameModeFilter = parseFilter(value, warnReporter, loc, "m");
                case "l" -> levelMax = parseInt(value, warnReporter, loc, key);
                case "lm" -> levelMin = parseInt(value, warnReporter, loc, key);
                default -> warnReporter.warn("LegacySelector", "unknown key " + key, loc);
            }
        }

        private void parseScore(String key, String value, LegacyWarnReporter warnReporter, SourceLoc loc) {
            String objective;
            boolean isMin = false;
            if (key.endsWith("_min")) {
                objective = key.substring("score_".length(), key.length() - "_min".length());
                isMin = true;
            } else {
                objective = key.substring("score_".length());
            }
            int number = parseInt(value, warnReporter, loc, key);
            ScoreFilter filter = scoreFilters.getOrDefault(objective, new ScoreFilter(objective));
            if (isMin) {
                filter.min(number);
            } else {
                filter.max(number);
            }
            scoreFilters.put(objective, filter);
        }

        private static FilterValue parseFilter(String value, LegacyWarnReporter warnReporter, SourceLoc loc, String key) {
            boolean negated = value.startsWith("!");
            if (negated) {
                warnReporter.warnOnce("LegacySelectorNegation", "negation used " + key, loc);
                value = value.substring(1);
            }
            return new FilterValue(value, negated);
        }

        private static int parseInt(String raw, LegacyWarnReporter warnReporter, SourceLoc loc, String key) {
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                warnReporter.warn("LegacySelector", "bad int " + key + "=" + raw, loc);
                return 0;
            }
        }

        private static double parseDouble(String raw, LegacyWarnReporter warnReporter, SourceLoc loc, String key) {
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                warnReporter.warn("LegacySelector", "bad double " + key + "=" + raw, loc);
                return 0;
            }
        }
    }

    private record FilterValue(String value, boolean negated) {
    }

    private static final class ScoreFilter {
        private final String objective;
        private Integer min;
        private Integer max;

        private ScoreFilter(String objective) {
            this.objective = objective;
        }

        public String objective() {
            return objective;
        }

        public Integer min() {
            return min;
        }

        public Integer max() {
            return max;
        }

        public void min(int value) {
            this.min = value;
        }

        public void max(int value) {
            this.max = value;
        }
    }
}
