package ie.orangep.reLootplusplus.lucky.template;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates Lucky Block template variables in attribute string values.
 *
 * <p>Template vars have the form {@code #varName} or {@code #varName(arg,arg)}
 * and are resolved at drop-evaluation time (not parse time).
 */
public final class LuckyTemplateVars {

    private static final Pattern RAND_PATTERN = Pattern.compile("#rand\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\)");
    private static final Pattern RAND_LIST_PATTERN = Pattern.compile("#randList\\(([^)]+)\\)");
    private static final Pattern RAND_FLOAT_PATTERN = Pattern.compile("#randFloat\\(\\s*([0-9.+-]+)\\s*,\\s*([0-9.+-]+)\\s*\\)");
    private static final Pattern RAND_BOOL_PATTERN = Pattern.compile("#randBool");
    private static final Pattern CIRCLE_OFFSET_PATTERN = Pattern.compile("#circleOffset\\(\\s*([0-9.+-]+)\\s*(?:,\\s*([0-9.+-]+)\\s*)?\\)");
    private static final Pattern RAND_LAUNCH_MOTION_PATTERN = Pattern.compile(
        "#randLaunchMotion(?:\\(\\s*([0-9.+-]+)\\s*(?:,\\s*([0-9.+-]+)\\s*)?\\))?");
    private static final Pattern RAND_POS_NEG_PATTERN = Pattern.compile(
        "#randPosNeg\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\)");
    // #bowMotion(speed) or #bowMotion(speed,extraUpward) or bare #bowMotion
    private static final Pattern BOW_MOTION_PATTERN = Pattern.compile(
        "#bowMotion(?:\\(\\s*([0-9.]+)\\s*(?:,\\s*([0-9.]+)\\s*)?\\))?");
    // #motionFromDirection(hAngleDeg, vAngleDeg, speed) — args may be arithmetic expressions after substitution
    private static final Pattern MOTION_FROM_DIR_PATTERN = Pattern.compile(
        "#motionFromDirection\\(([^)]+)\\)");
    // Arithmetic: chains of integer or float tokens joined by + or -
    private static final Pattern ARITH_PATTERN = Pattern.compile("^\\s*(-?[0-9]+(?:\\.[0-9]+)?)\\s*([+\\-]\\s*[0-9]+(?:\\.[0-9]+)?)*\\s*$");

    private LuckyTemplateVars() {}

    /**
     * Evaluates all template variables in {@code value} using the given context.
     * Unknown template variables are left as-is (to allow Lucky Block addon compatibility).
     */
    public static String evaluate(String value, EvalContext ctx) {
        if (value == null || value.isEmpty() || !value.contains("#")) return value;

        value = evaluateRand(value, ctx.random());
        value = evaluateRandFloat(value, ctx.random());
        value = evaluateRandList(value, ctx.random());
        value = evaluateRandBool(value, ctx.random());
        value = evaluateRandPosNeg(value, ctx.random());
        value = evaluateCircleOffset(value, ctx.random());
        value = evaluateRandLaunchMotion(value, ctx.random());
        value = evaluateBowMotion(value, ctx.random(), ctx.player());
        value = evaluatePlayerVars(value, ctx);       // must run before motionFromDirection
        value = evaluateMotionFromDirection(value, ctx.random());
        value = evaluatePosVars(value, ctx);
        value = evaluateOtherVars(value, ctx);
        return value;
    }

    private static String evaluateRand(String value, Random random) {
        Matcher m = RAND_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int min = Integer.parseInt(m.group(1));
            int max = Integer.parseInt(m.group(2));
            int result = min == max ? min : min + random.nextInt(Math.abs(max - min) + 1);
            m.appendReplacement(sb, String.valueOf(result));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String evaluateRandFloat(String value, Random random) {
        Matcher m = RAND_FLOAT_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            try {
                float min = Float.parseFloat(m.group(1));
                float max = Float.parseFloat(m.group(2));
                float result = min + random.nextFloat() * (max - min);
                m.appendReplacement(sb, String.format("%.4f", result));
            } catch (NumberFormatException e) {
                m.appendReplacement(sb, m.group(0));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String evaluateRandList(String value, Random random) {
        Matcher m = RAND_LIST_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            // Original Lucky Block format uses commas as separators inside #randList(a,b,c)
            String[] items = m.group(1).split(",");
            if (items.length == 0) {
                m.appendReplacement(sb, "");
            } else {
                m.appendReplacement(sb,
                    Matcher.quoteReplacement(items[random.nextInt(items.length)].trim()));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String evaluateRandPosNeg(String value, Random random) {
        if (!value.contains("#randPosNeg")) return value;
        Matcher m = RAND_POS_NEG_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            try {
                int lo = Math.abs(Integer.parseInt(m.group(1)));
                int hi = Math.abs(Integer.parseInt(m.group(2)));
                if (lo > hi) { int tmp = lo; lo = hi; hi = tmp; }
                int magnitude = lo + (hi > lo ? random.nextInt(hi - lo + 1) : 0);
                int result = random.nextBoolean() ? magnitude : -magnitude;
                m.appendReplacement(sb, String.valueOf(result));
            } catch (NumberFormatException e) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Evaluates {@code #bowMotion}, {@code #bowMotion(speed)}, or {@code #bowMotion(speed,extraY)}
     * into a Minecraft {@code Motion} NBT array string {@code [dx d,dy d,dz d]}.
     *
     * <p>Uses the player's look direction when available; falls back to a random direction.
     */
    private static String evaluateBowMotion(String value, Random random, PlayerEntity player) {
        if (!value.contains("#bowMotion")) return value;
        Matcher m = BOW_MOTION_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            float speed = 1.0f;
            float extraY = 0.0f;
            if (m.group(1) != null) {
                try { speed = Float.parseFloat(m.group(1)); } catch (NumberFormatException ignored) {}
            }
            if (m.group(2) != null) {
                try { extraY = Float.parseFloat(m.group(2)) * 0.05f; } catch (NumberFormatException ignored) {}
            }
            String motion;
            if (player != null) {
                Vec3d look = player.getRotationVec(1.0f);
                double dx = look.x * speed;
                double dy = look.y * speed + extraY;
                double dz = look.z * speed;
                motion = String.format("[%.4fd,%.4fd,%.4fd]", dx, dy, dz);
            } else {
                double angle = random.nextDouble() * 2 * Math.PI;
                double vAngle = Math.toRadians(20.0);
                double horiz = Math.cos(vAngle);
                double dx = speed * horiz * Math.cos(angle);
                double dy = speed * Math.sin(vAngle) + extraY;
                double dz = speed * horiz * Math.sin(angle);
                motion = String.format("[%.4fd,%.4fd,%.4fd]", dx, dy, dz);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(motion));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Evaluates {@code #motionFromDirection(hAngleDeg, vAngleDeg, speed)} into a Motion NBT array.
     * Args may be arithmetic expressions (e.g. {@code #pYaw+#rand(-10,10)} after prior substitution).
     */
    private static String evaluateMotionFromDirection(String value, Random random) {
        if (!value.contains("#motionFromDirection")) return value;
        Matcher m = MOTION_FROM_DIR_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            try {
                String[] args = m.group(1).split(",", 3);
                float hAngleDeg = (float) evalArithmetic(args[0].trim(), 0.0);
                float vAngleDeg = args.length > 1 ? (float) evalArithmetic(args[1].trim(), 0.0) : 0f;
                float speed = args.length > 2 ? (float) evalArithmetic(args[2].trim(), 1.0) : 1.0f;
                double hAngleRad = Math.toRadians(hAngleDeg);
                double vAngleRad = Math.toRadians(vAngleDeg);
                double horiz = speed * Math.cos(vAngleRad);
                double dx = horiz * Math.sin(hAngleRad);
                double dy = speed * Math.sin(vAngleRad);
                double dz = horiz * Math.cos(hAngleRad);
                m.appendReplacement(sb,
                    Matcher.quoteReplacement(String.format("[%.4fd,%.4fd,%.4fd]", dx, dy, dz)));
            } catch (NumberFormatException e) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String evaluateRandBool(String value, Random random) {
        Matcher m = RAND_BOOL_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, random.nextBoolean() ? "true" : "false");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String evaluateCircleOffset(String value, Random random) {
        if (!value.contains("#circleOffset")) return value;
        Matcher m = CIRCLE_OFFSET_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            float minR, maxR;
            try { minR = Float.parseFloat(m.group(1)); } catch (NumberFormatException e) { minR = 5.0f; }
            if (m.group(2) != null) {
                // Two-arg form: #circleOffset(minRadius, maxRadius)
                try { maxR = Float.parseFloat(m.group(2)); } catch (NumberFormatException e) { maxR = minR; }
            } else {
                maxR = minR; // One-arg form: exact radius
            }
            float radius = minR >= maxR ? minR : minR + random.nextFloat() * (maxR - minR);
            double angle = random.nextDouble() * 2 * Math.PI;
            double dx = radius * Math.cos(angle);
            double dz = radius * Math.sin(angle);
            m.appendReplacement(sb, String.format("%.4f 0 %.4f", dx, dz));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String evaluateRandLaunchMotion(String value, Random random) {
        if (!value.contains("#randLaunchMotion")) return value;
        Matcher m = RAND_LAUNCH_MOTION_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            // Arg1 = horizontal speed, Arg2 = upward angle in degrees (default 45°)
            float hSpeed = 1.0f;
            float angleDeg = 45.0f;
            if (m.group(1) != null) {
                try { hSpeed = Float.parseFloat(m.group(1)); } catch (NumberFormatException ignored) {}
            }
            if (m.group(2) != null) {
                try { angleDeg = Float.parseFloat(m.group(2)); } catch (NumberFormatException ignored) {}
            }
            double angleRad = Math.toRadians(angleDeg);
            double horizComp = hSpeed * Math.cos(angleRad);
            double vertComp  = hSpeed * Math.sin(angleRad);
            double dir = random.nextDouble() * 2 * Math.PI;
            double dx = horizComp * Math.cos(dir);
            double dz = horizComp * Math.sin(dir);
            double dy = vertComp;
            String motion = String.format("[%.4fd,%.4fd,%.4fd]", dx, dy, dz);
            m.appendReplacement(sb, Matcher.quoteReplacement(motion));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String evaluatePlayerVars(String value, EvalContext ctx) {
        if (!value.contains("#p")) return value;
        if (value.contains("#pDirect")) {
            value = value.replace("#pDirect", evaluatePDirect(ctx));
        }
        if (ctx.player() != null) {
            value = value.replace("#pYaw", String.format("%.4f", ctx.player().getYaw()));
            value = value.replace("#pPitch", String.format("%.4f", ctx.player().getPitch()));
        } else {
            value = value.replace("#pYaw", "0.0000");
            value = value.replace("#pPitch", "0.0000");
        }
        String playerName = ctx.player() != null ? ctx.player().getGameProfile().getName() : "";
        value = value.replace("#playerName", playerName);
        value = value.replace("#pName", playerName);
        value = value.replace("#player", playerName);
        return value;
    }

    private static String evaluatePDirect(EvalContext ctx) {
        if (ctx.player() == null) return "0";
        float yaw = ctx.player().getYaw();
        // Normalize to 0-360 (0=South, 90=West, 180=North, 270=East in Minecraft)
        yaw = ((yaw % 360) + 360) % 360;
        // Snap to nearest 90 degrees
        int snapped = (int)(Math.round(yaw / 90.0) % 4) * 90;
        // Convert to rotation angle where North=0, East=90, South=180, West=270
        // Player yaw: 0=South→rotation180, 90=West→rotation270, 180=North→rotation0, 270=East→rotation90
        int rotation = (snapped + 180) % 360;
        return String.valueOf(rotation);
    }

    private static String evaluatePosVars(String value, EvalContext ctx) {
        if (!value.contains("#pos") && !value.contains("#bPos") && !value.contains("#bExact")
                && !value.contains("#pExact") && !value.contains("#bowPos") && !value.contains("#dist")) {
            return value;
        }
        BlockPos pos = ctx.pos();
        if (pos != null) {
            // Exact (float) block center positions — replace before shorter #bPos/#pos variants
            if (value.contains("#bExactPos")) {
                value = value.replace("#bExactPosX", String.format("%.1f", pos.getX() + 0.5));
                value = value.replace("#bExactPosY", String.format("%.1f", pos.getY() + 0.5));
                value = value.replace("#bExactPosZ", String.format("%.1f", pos.getZ() + 0.5));
                value = value.replace("#bExactPos",
                    String.format("%.1f %.1f %.1f", pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            }
            // Block position vars — #bPosX / #posX etc. are synonymous
            value = value.replace("#bPosX", String.valueOf(pos.getX()));
            value = value.replace("#bPosY", String.valueOf(pos.getY()));
            value = value.replace("#bPosZ", String.valueOf(pos.getZ()));
            value = value.replace("#bPos", pos.getX() + " " + pos.getY() + " " + pos.getZ());
            value = value.replace("#posX", String.valueOf(pos.getX()));
            value = value.replace("#posY", String.valueOf(pos.getY()));
            value = value.replace("#posZ", String.valueOf(pos.getZ()));
            value = value.replace("#pos", pos.getX() + " " + pos.getY() + " " + pos.getZ());
        }
        if (value.contains("#distanceToPlayer")) {
            double dist = 0.0;
            if (ctx.player() != null && pos != null) {
                dist = ctx.player().getPos().distanceTo(
                    net.minecraft.util.math.Vec3d.ofCenter(pos)
                );
            }
            value = value.replace("#distanceToPlayer", String.format("%.2f", dist));
        }
        // #pExactPos* — player exact float position (replace before #pPos)
        if (value.contains("#pExactPos")) {
            if (ctx.player() != null) {
                Vec3d pPos = ctx.player().getPos();
                value = value.replace("#pExactPosX", String.format("%.2f", pPos.x));
                value = value.replace("#pExactPosY", String.format("%.2f", pPos.y));
                value = value.replace("#pExactPosZ", String.format("%.2f", pPos.z));
                value = value.replace("#pExactPos",
                    String.format("%.2f %.2f %.2f", pPos.x, pPos.y, pPos.z));
            } else {
                value = value.replace("#pExactPosX", "0.0")
                             .replace("#pExactPosY", "64.0")
                             .replace("#pExactPosZ", "0.0");
                value = value.replace("#pExactPos", "0.0 64.0 0.0");
            }
        }
        if (ctx.player() != null) {
            BlockPos pPos = ctx.player().getBlockPos();
            value = value.replace("#pPosX", String.valueOf(pPos.getX()));
            value = value.replace("#pPosY", String.valueOf(pPos.getY()));
            value = value.replace("#pPosZ", String.valueOf(pPos.getZ()));
            value = value.replace("#pPos", pPos.getX() + " " + pPos.getY() + " " + pPos.getZ());

            if (value.contains("#pUUIDArray")) {
                UUID uuid = ctx.player().getUuid();
                long msb = uuid.getMostSignificantBits();
                long lsb = uuid.getLeastSignificantBits();
                int a = (int)(msb >> 32);
                int b = (int)(msb & 0xFFFFFFFFL);
                int c = (int)(lsb >> 32);
                int d = (int)(lsb & 0xFFFFFFFFL);
                value = value.replace("#pUUIDArray", "[I;" + a + "," + b + "," + c + "," + d + "]");
            }
        } else {
            value = value.replace("#pPosX", "0").replace("#pPosY", "0").replace("#pPosZ", "0");
            value = value.replace("#pPos", "0 0 0");
            value = value.replace("#pUUIDArray", "[I;0,0,0,0]");
        }
        // #bowPos — bow launch position (player eye level, or block centre + 1.5 if no player)
        if (value.contains("#bowPos")) {
            String bowPosStr;
            if (ctx.player() != null) {
                Vec3d eyePos = ctx.player().getEyePos();
                bowPosStr = String.format("%.2f %.2f %.2f", eyePos.x, eyePos.y, eyePos.z);
            } else if (pos != null) {
                bowPosStr = String.format("%.1f %.1f %.1f",
                    pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
            } else {
                bowPosStr = "0 64 0";
            }
            value = value.replace("#bowPos", bowPosStr);
        }
        return value;
    }

    private static String evaluateOtherVars(String value, EvalContext ctx) {
        // #nothing — used as a drop that does nothing
        value = value.replace("#nothing", "nothing");

        // Armor enchantment list vars — provide sensible defaults
        if (value.contains("#luckyBootsEnchantments")) {
            value = value.replace("#luckyBootsEnchantments",
                "[(id=0,lvl=4),(id=5,lvl=3)]");
        }
        if (value.contains("#luckyLeggingsEnchantments")) {
            value = value.replace("#luckyLeggingsEnchantments",
                "[(id=0,lvl=4),(id=3,lvl=3)]");
        }
        if (value.contains("#luckyChestplateEnchantments")) {
            value = value.replace("#luckyChestplateEnchantments",
                "[(id=0,lvl=4),(id=3,lvl=3)]");
        }
        if (value.contains("#luckyHelmetEnchantments")) {
            value = value.replace("#luckyHelmetEnchantments",
                "[(id=0,lvl=4),(id=5,lvl=3)]");
        }
        if (value.contains("#luckyArmorEnchantments")) {
            value = value.replace("#luckyArmorEnchantments",
                "[(id=0,lvl=4),(id=3,lvl=3)]");
        }

        // Convenience enchantment list vars — provide sensible defaults
        if (value.contains("#luckySwordEnchantments")) {
            value = value.replace("#luckySwordEnchantments",
                "[(id=16,lvl=5),(id=21,lvl=3),(id=34,lvl=3)]");
        }
        if (value.contains("#luckyFishingRodEnchantments")) {
            value = value.replace("#luckyFishingRodEnchantments",
                "[(id=61,lvl=3),(id=62,lvl=3)]");
        }
        if (value.contains("#luckyBowEnchantments")) {
            value = value.replace("#luckyBowEnchantments",
                "[(id=48,lvl=5),(id=50,lvl=5),(id=51,lvl=5)]");
        }

        // Potion effect vars — substitute with an empty NBT list
        if (value.contains("#unluckyPotionEffects")) {
            value = value.replace("#unluckyPotionEffects", "[]");
        }
        if (value.contains("#luckyPotionEffects")) {
            value = value.replace("#luckyPotionEffects", "[]");
        }

        // #chest(contents=[...]) — inline chest definition; substitute with empty compound
        // Full implementation would build chest block entity NBT; for now skip the content safely
        if (value.contains("#chest(")) {
            value = value.replaceAll("#chest\\([^)]*\\)", "{}");
        }

        // #chestLootTable("path") — evaluate inline; use empty string if unavailable
        // Full implementation would use LootManager; here we substitute with an empty list marker
        if (value.contains("#chestLootTable")) {
            value = value.replaceAll("#chestLootTable\\([^)]*\\)", "[]");
        }

        // #json("text") — JSON text; leave as-is for NBT processing
        // Unknown # vars are left intact for graceful degradation

        return value;
    }

    // -------------------------------------------------------------------------
    // Context
    // -------------------------------------------------------------------------

    public record EvalContext(ServerWorld world, BlockPos pos, PlayerEntity player, Random random) {}

    // -------------------------------------------------------------------------
    // Arithmetic helper
    // -------------------------------------------------------------------------

    /**
     * Evaluates a simple arithmetic expression string (integer/float addition and subtraction).
     * Examples: {@code "72+7"} → 79, {@code "100-5"} → 95, {@code "42"} → 42.
     *
     * @param expr     the expression string (already template-substituted)
     * @param fallback value to return if parsing fails
     * @return evaluated result, or {@code fallback} on parse error
     */
    public static double evalArithmetic(String expr, double fallback) {
        if (expr == null || expr.isBlank()) return fallback;
        String s = expr.trim();
        // Tokenise on + and - while preserving signs for first token
        // We handle: "72+7", "100-5", "-10+3", "3.5+0.5"
        try {
            double result = 0;
            int i = 0;
            int sign = 1;
            int len = s.length();
            // Leading sign
            if (i < len && s.charAt(i) == '+') { sign = 1; i++; }
            else if (i < len && s.charAt(i) == '-') { sign = -1; i++; }
            int numStart = i;
            while (i < len) {
                char c = s.charAt(i);
                if ((c == '+' || c == '-') && i > numStart) {
                    result += sign * Double.parseDouble(s.substring(numStart, i).trim());
                    sign = (c == '+') ? 1 : -1;
                    i++;
                    numStart = i;
                } else {
                    i++;
                }
            }
            if (numStart < len) {
                result += sign * Double.parseDouble(s.substring(numStart).trim());
            }
            return result;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
