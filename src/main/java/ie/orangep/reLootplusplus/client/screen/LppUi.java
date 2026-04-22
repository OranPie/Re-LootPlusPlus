package ie.orangep.reLootplusplus.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared drawing utilities and constants for all Re-LootPlusPlus in-game screens.
 *
 * <p>Extends {@link DrawableHelper} so it can access {@code fillGradient} via the
 * package-private singleton {@link #INST}.
 */
public final class LppUi extends DrawableHelper {

    /** Singleton for calling protected-instance methods from static helpers. */
    private static final LppUi INST = new LppUi();
    private LppUi() {}

    // ── Layout constants ───────────────────────────────────────────────────────
    public static final int HDR_H    = 44;
    public static final int FTR_H    = 32;
    public static final int STATS_H  = 22;
    public static final int TAB_H    = 26;
    public static final int ROW_H    = 22;

    // ── Color palette ──────────────────────────────────────────────────────────
    public static final int HDR_TOP  = 0xFF1A1A2E;
    public static final int HDR_BOT  = 0xFF16213E;
    public static final int FTR_BG   = 0xDD0F0F1A;
    public static final int STATS_BG = 0xCC111827;
    public static final int SEP      = 0xFF3A4A6A;
    public static final int HOVER_BG = 0x22FFFFFF;
    public static final int SEL_BG   = 0x33AABBCC;
    public static final int STRIPE   = 0x10FFFFFF;

    public static final int C_TITLE  = 0xFFFFAA;
    public static final int C_SUB    = 0x8899CC;
    public static final int C_DIM    = 0x556677;
    public static final int C_BODY   = 0xDDDDDD;
    public static final int C_ATTR_K = 0x88BBCC;
    public static final int C_NUM    = 0xAADDFF;

    // ── Type / kind colors ─────────────────────────────────────────────────────

    /** Returns the display color for a Lucky drop type. */
    public static int typeColor(String type) {
        if (type == null) return 0xAAAAAA;
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "item"       -> 0x55AAFF;
            case "entity"     -> 0x55FF88;
            case "block"      -> 0xFFAA33;
            case "command"    -> 0xFFFF55;
            case "structure"  -> 0xCC55FF;
            case "effect"     -> 0xFF7777;
            case "fill"       -> 0x77CCFF;
            case "message"    -> 0xFFCC77;
            case "explosion"  -> 0xFF4444;
            case "sound"      -> 0xAA77FF;
            case "particle"   -> 0x77FFCC;
            case "time"       -> 0xCCBB55;
            case "difficulty" -> 0xFF8844;
            case "nothing"    -> 0x445566;
            case "group"      -> 0xFFCC44;
            default           -> 0xAAAAAA;
        };
    }

    /** Returns the display color for a config reference kind. */
    public static int kindColor(String kind) {
        if (kind == null) return 0x8899AA;
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case "item"      -> 0x55AAFF;
            case "entity"    -> 0xFF9944;
            case "block"     -> 0x77CC44;
            case "command"   -> 0xFFDD44;
            case "structure" -> 0xDD77FF;
            case "effect"    -> 0xFF55AA;
            case "fill"      -> 0x44CCBB;
            default          -> 0x8899AA;
        };
    }

    // ── Standard header/footer ─────────────────────────────────────────────────

    /**
     * Draws the standard dark navy header gradient, separator, and centered text.
     *
     * @param subtitle optional second line; if null, title is vertically centered
     */
    public static void drawHeader(MatrixStack ms, int w, Text title,
                                  @Nullable Text subtitle, TextRenderer tr) {
        INST.fillGradient(ms, 0, 0, w, HDR_H, HDR_TOP, HDR_BOT);
        fill(ms, 0, HDR_H - 1, w, HDR_H, SEP);
        int titleY = subtitle != null ? 8 : (HDR_H - tr.fontHeight) / 2;
        tr.drawWithShadow(ms, title, (w - tr.getWidth(title)) * 0.5f, titleY, C_TITLE);
        if (subtitle != null) {
            tr.drawWithShadow(ms, subtitle, (w - tr.getWidth(subtitle)) * 0.5f, 24, C_SUB);
        }
    }

    /**
     * Draws the standard footer: separator + dark background + optional right-aligned status text.
     */
    public static void drawFooter(MatrixStack ms, int w, int h,
                                  @Nullable Text statusText, TextRenderer tr) {
        int y = h - FTR_H;
        fill(ms, 0, y, w, y + 1, SEP);
        fill(ms, 0, y + 1, w, h, FTR_BG);
        if (statusText != null) {
            int sw = tr.getWidth(statusText);
            tr.drawWithShadow(ms, statusText, w - sw - 10, y + (FTR_H - tr.fontHeight) * 0.5f, C_DIM);
        }
    }

    // ── Badge pill ─────────────────────────────────────────────────────────────

    /**
     * Draws a colored badge pill (3px left stripe + tinted bg + text).
     *
     * @return the badge width in pixels
     */
    public static int drawBadge(MatrixStack ms, int x, int y, int h,
                                String text, int color, TextRenderer tr) {
        int textW  = tr.getWidth(text);
        int badgeW = textW + 10;
        int bg     = (color & 0x00FFFFFF) | 0x55000000;
        fill(ms, x, y + 1, x + badgeW, y + h - 1, bg);
        fill(ms, x, y + 1, x + 3, y + h - 1, color | 0xFF000000);
        tr.drawWithShadow(ms, new LiteralText(text),
            x + 5, y + (h - tr.fontHeight) * 0.5f, color | 0xFF000000);
        return badgeW;
    }

    // ── Separators / stripes ───────────────────────────────────────────────────

    /** Draws a 1px horizontal separator in the standard {@link #SEP} color. */
    public static void sepH(MatrixStack ms, int y, int w) {
        fill(ms, 0, y, w, y + 1, SEP);
    }

    /** Draws a 1px horizontal separator in the given color. */
    public static void sepH(MatrixStack ms, int y, int w, int color) {
        fill(ms, 0, y, w, y + 1, color);
    }

    /** Draws a 3px colored left stripe for a list row entry. */
    public static void stripe(MatrixStack ms, int x, int y, int h, int color) {
        fill(ms, x, y + 1, x + 3, y + h - 1, color | 0xFF000000);
    }

    // ── Text clipping ──────────────────────────────────────────────────────────

    /** Clips {@code text} to {@code maxW} pixels, appending '…' if truncated. */
    public static String clip(String text, int maxW, TextRenderer tr) {
        if (text == null) return "";
        if (tr.getWidth(text) <= maxW) return text;
        String suf   = "…";
        int    avail = maxW - tr.getWidth(suf);
        if (avail <= 0) return suf;
        int end = text.length();
        while (end > 0 && tr.getWidth(text.substring(0, end)) > avail) end--;
        return text.substring(0, end) + suf;
    }

    // ── Stats chip bar ─────────────────────────────────────────────────────────

    /**
     * Draws the type-distribution chip bar and returns chip bounds for click detection.
     *
     * @param typeCounts  ordered map of type → count
     * @param activeFilter currently active filter type, or {@code null} for "all"
     * @param outTypes    output list filled with the type string for each chip (parallel to returned bounds)
     * @return list of [x1, x2] per chip
     */
    public static List<int[]> drawStatsBar(MatrixStack ms, int barY, int barH, int w,
                                           Map<String, Integer> typeCounts,
                                           @Nullable String activeFilter,
                                           List<String> outTypes,
                                           TextRenderer tr) {
        fill(ms, 0, barY, w, barY + barH, STATS_BG);
        fill(ms, 0, barY + barH - 1, w, barY + barH, SEP);

        int chipPad = 5;
        int chipH   = 13;
        int gap     = 4;
        int chipY   = barY + (barH - chipH) / 2;

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(typeCounts.entrySet());
        List<Integer> widths = new ArrayList<>();
        int totalW = 0;
        for (Map.Entry<String, Integer> e : entries) {
            String label = chipLabel(e.getKey(), e.getValue());
            int cw = tr.getWidth(label) + chipPad * 2;
            widths.add(cw);
            totalW += cw + gap;
        }
        if (!entries.isEmpty()) totalW -= gap;

        List<int[]> bounds = new ArrayList<>();
        outTypes.clear();
        int cx = Math.max(chipPad, (w - totalW) / 2);

        for (int i = 0; i < entries.size(); i++) {
            String  type    = entries.get(i).getKey();
            int     color   = typeColor(type);
            int     cw      = widths.get(i);
            String  label   = chipLabel(type, entries.get(i).getValue());
            boolean active  = type.equals(activeFilter);

            int bg = active
                ? (color & 0x00FFFFFF) | 0x99000000
                : (color & 0x00FFFFFF) | 0x44000000;
            fill(ms, cx, chipY, cx + cw, chipY + chipH, bg);
            fill(ms, cx, chipY, cx + 1, chipY + chipH, color | 0xFF000000);
            fill(ms, cx + cw - 1, chipY, cx + cw, chipY + chipH, color | 0xFF000000);
            if (active) {
                fill(ms, cx, chipY, cx + cw, chipY + 1, color | 0xFF000000);
                fill(ms, cx, chipY + chipH - 1, cx + cw, chipY + chipH, color | 0xFF000000);
            }
            tr.drawWithShadow(ms, new LiteralText(label),
                cx + chipPad, chipY + (chipH - tr.fontHeight) * 0.5f, color | 0xFF000000);

            bounds.add(new int[]{cx, cx + cw, chipY, chipY + chipH});
            outTypes.add(type);
            cx += cw + gap;
        }
        return bounds;
    }

    // ── Tab bar ────────────────────────────────────────────────────────────────

    /**
     * Draws a horizontal tab bar below y.
     *
     * @return list of [x1, x2] tab click bounds
     */
    public static List<int[]> drawTabBar(MatrixStack ms, int y, int w,
                                         List<String> labels, int activeTab,
                                         TextRenderer tr) {
        fill(ms, 0, y, w, y + TAB_H, 0xFF0E1420);
        fill(ms, 0, y + TAB_H - 1, w, y + TAB_H, SEP);

        int tabPadX = 10;
        List<Integer> tabWidths = new ArrayList<>();
        int totalW = 0;
        for (String label : labels) {
            int tw = tr.getWidth(label) + tabPadX * 2;
            tabWidths.add(tw);
            totalW += tw;
        }

        List<int[]> bounds = new ArrayList<>();
        int tx = (w - totalW) / 2;
        for (int i = 0; i < labels.size(); i++) {
            int tw = tabWidths.get(i);
            if (i == activeTab) {
                fill(ms, tx, y, tx + tw, y + TAB_H - 1, 0x33AABBCC);
                fill(ms, tx, y + TAB_H - 2, tx + tw, y + TAB_H - 1, C_TITLE | 0xFF000000);
            }
            int textColor = (i == activeTab) ? C_TITLE : C_DIM;
            tr.drawWithShadow(ms, new LiteralText(labels.get(i)),
                tx + tabPadX, y + (TAB_H - tr.fontHeight) * 0.5f, textColor);
            bounds.add(new int[]{tx, tx + tw});
            tx += tw;
        }
        return bounds;
    }

    // ── Info row ───────────────────────────────────────────────────────────────

    /**
     * Draws a single "label: value" info row.
     *
     * @return y position after this row
     */
    public static int drawInfoRow(MatrixStack ms, int x, int y,
                                  String label, String value, int maxW,
                                  TextRenderer tr) {
        if (value == null || value.isBlank()) return y;
        String prefix  = label + ": ";
        int    prefixW = tr.getWidth(prefix);
        tr.drawWithShadow(ms, new LiteralText(prefix), x, y, C_ATTR_K);
        tr.drawWithShadow(ms, new LiteralText(clip(value, maxW - prefixW, tr)),
            x + prefixW, y, C_BODY);
        return y + tr.fontHeight + 3;
    }

    // ── Attr table (2-column key=val) ──────────────────────────────────────────

    /**
     * Draws a 2-column attribute table (key in cyan, value in white).
     *
     * @param attrs   ordered map of key → value strings
     * @param maxRows maximum number of display rows
     * @return y position after the last drawn row
     */
    public static int drawAttrTable(MatrixStack ms, int x, int y, int maxW,
                                    Map<String, String> attrs, int maxRows,
                                    TextRenderer tr) {
        if (attrs == null || attrs.isEmpty()) return y;
        List<Map.Entry<String, String>> entries = new ArrayList<>(attrs.entrySet());
        int rowH  = tr.fontHeight + 3;
        int colW  = (maxW - 6) / 2;
        int drawn = 0;

        for (int i = 0; i < entries.size() && drawn < maxRows; i += 2, drawn++) {
            drawAttrPair(ms, x, y, entries.get(i), colW, tr);
            if (i + 1 < entries.size()) {
                drawAttrPair(ms, x + colW + 6, y, entries.get(i + 1), colW, tr);
            }
            y += rowH;
        }
        return y;
    }

    private static void drawAttrPair(MatrixStack ms, int x, int y,
                                     Map.Entry<String, String> e, int colW,
                                     TextRenderer tr) {
        String k = e.getKey() + "=";
        int    kw = tr.getWidth(k);
        tr.drawWithShadow(ms, new LiteralText(k), x, y, C_ATTR_K);
        String v = clip(e.getValue(), colW - kw, tr);
        tr.drawWithShadow(ms, new LiteralText(v), x + kw, y, C_BODY);
    }

    // ── Luck / weight bar ─────────────────────────────────────────────────────

    /**
     * Draws a luck-weight progress bar (20×5 px).
     * Luck range: -100..100; fill proportion = (lw + 100) / 200.
     */
    public static void drawLuckBar(MatrixStack ms, int x, int y, int lw) {
        int barW  = 22;
        int barH  = 5;
        float fill = Math.max(0f, Math.min(1f, (lw + 100) / 200f));
        int filledW = Math.round(fill * (barW - 2));
        // Track
        fill(ms, x, y, x + barW, y + barH, 0xFF222233);
        // Fill
        int barColor = lw >= 0 ? 0xFF55AA55 : 0xFFAA5555;
        fill(ms, x + 1, y + 1, x + 1 + filledW, y + barH - 1, barColor);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private static String chipLabel(String type, int count) {
        String abbr = type.length() <= 4 ? type.toUpperCase(Locale.ROOT)
            : type.substring(0, 4).toUpperCase(Locale.ROOT);
        return abbr + ":" + count;
    }

    // ── Convenience fill (re-expose for package use) ───────────────────────────

    /** Public wrapper around {@link DrawableHelper#fill}. */
    public static void fillRect(MatrixStack ms, int x1, int y1, int x2, int y2, int color) {
        fill(ms, x1, y1, x2, y2, color);
    }

    /** Public wrapper around {@link DrawableHelper#fillGradient} (vertical). */
    public static void fillGrad(MatrixStack ms, int x1, int y1, int x2, int y2,
                                int colorTop, int colorBot) {
        INST.fillGradient(ms, x1, y1, x2, y2, colorTop, colorBot);
    }

    /**
     * Draws a pre-loaded addon texture (via {@link ie.orangep.reLootplusplus.client.AddonTextureLoader})
     * scaled to {@code drawW × drawH}.  Call {@code RenderSystem.enableBlend()} before
     * if the PNG has transparency.
     *
     * @param texId  registered Identifier returned by {@code AddonTextureLoader.getOrLoad()}
     * @param imgW   native pixel width of the source image
     * @param imgH   native pixel height of the source image
     */
    public static void drawSprite(MatrixStack ms, Identifier texId,
                                  int x, int y, int drawW, int drawH,
                                  int imgW, int imgH) {
        if (texId == null || imgW <= 0 || imgH <= 0) return;
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texId);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        ms.push();
        ms.translate(x, y, 0);
        ms.scale((float) drawW / imgW, (float) drawH / imgH, 1f);
        drawTexture(ms, 0, 0, 0, 0, imgW, imgH, imgW, imgH);
        ms.pop();
    }
}

