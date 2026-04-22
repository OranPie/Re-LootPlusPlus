package ie.orangep.reLootplusplus.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw drop-line viewer with syntax highlighting, search, and copy.
 *
 * <p>Layout:
 * <ul>
 *   <li>Navy header (44px): title + stats subtitle (N segments · M keys · K vars)</li>
 *   <li>Gutter (36px left) + content area</li>
 *   <li>Search field (24px) at bottom above the dark footer (28px)</li>
 *   <li>Footer (28px): back button left, copy button right</li>
 * </ul>
 */
public final class ReLootPlusPlusRawLineScreen extends Screen {

    private static final int GUTTER_W    = 36;
    private static final int CONTENT_X   = GUTTER_W + 4;
    private static final int SCROLLBAR_W = 6;
    private static final int SEARCH_H    = 24;
    private static final int FTR_H       = 28;

    private final Screen parent;
    private final String raw;

    private List<String>      logicalLines;
    private List<WrappedLine> wrappedLines;
    private int               scroll;
    private int               searchMatchCount;

    // pre-computed header stats
    private int statKeys;
    private int statVars;

    private TextFieldWidget searchField;
    private String          lastQuery = "";

    public ReLootPlusPlusRawLineScreen(Screen parent, String raw) {
        super(new TranslatableText("menu.relootplusplus.raw_full_title"));
        this.parent = parent;
        this.raw    = raw == null ? "" : raw;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    protected void init() {
        // Back button (footer left)
        this.addDrawableChild(new ButtonWidget(
            8, this.height - FTR_H + 4, 60, 20,
            new TranslatableText("menu.relootplusplus.back"),
            b -> this.client.setScreen(parent)));

        // Copy button (footer right)
        this.addDrawableChild(new ButtonWidget(
            this.width - 76, this.height - FTR_H + 4, 68, 20,
            new TranslatableText("menu.relootplusplus.copy"),
            b -> { if (client != null) client.keyboard.setClipboard(raw); }));

        // Search field (above footer)
        int searchY = this.height - FTR_H - SEARCH_H - 2;
        searchField = new TextFieldWidget(this.textRenderer,
            CONTENT_X, searchY + 2, this.width - CONTENT_X - SCROLLBAR_W - 16, SEARCH_H - 4,
            new TranslatableText("menu.relootplusplus.rawline.search"));
        searchField.setSuggestion("Search…");
        searchField.setChangedListener(q -> { lastQuery = q; rebuildWrapped(); });
        searchField.setMaxLength(200);
        this.addSelectableChild(searchField);
        this.addDrawableChild(searchField);

        // Build initial lines
        precomputeStats();
        updateLines();
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        String savedQuery = searchField != null ? searchField.getText() : "";
        super.resize(client, width, height);
        if (searchField != null) searchField.setText(savedQuery);
        updateLines();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchField != null && searchField.isFocused()) {
            if (keyCode == 256) { // Escape → clear search
                searchField.setText("");
                return true;
            }
        }
        if (keyCode == 256) { this.client.setScreen(parent); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Render ─────────────────────────────────────────────────────────────

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float delta) {
        this.renderBackground(ms);

        // Header
        String subtitle = logicalLines.size() + " seg · "
            + statKeys + " keys · " + statVars + " vars"
            + (searchMatchCount > 0 ? "  ·  " + searchMatchCount + " match" : "");
        LppUi.drawHeader(ms, this.width, this.title,
            new LiteralText(subtitle), this.textRenderer);

        // Gutter background
        int top    = LppUi.HDR_H;
        int searchY = this.height - FTR_H - SEARCH_H - 2;
        int bottom = searchY;

        LppUi.fillRect(ms, 0, top, GUTTER_W, bottom, 0xFF12121C);
        LppUi.fillRect(ms, GUTTER_W, top, GUTTER_W + 1, bottom, 0xFF2A3A5A);

        // Content lines
        int lineH = this.textRenderer.fontHeight + 2;
        int y     = top - scroll;
        int lastIdx = -1;
        String query = lastQuery.toLowerCase().trim();

        for (WrappedLine wl : wrappedLines) {
            if (y + lineH < top)  { y += lineH; continue; }
            if (y > bottom)       break;

            // Row stripe
            if (wl.lineIdx % 2 == 0)
                LppUi.fillRect(ms, GUTTER_W + 1, y, this.width - SCROLLBAR_W - 4, y + lineH, 0x0AFFFFFF);

            // Search match highlight
            if (!query.isBlank() && wl.logicalText.toLowerCase().contains(query))
                LppUi.fillRect(ms, GUTTER_W + 1, y, this.width - SCROLLBAR_W - 4, y + lineH, 0x33FFFF00);

            // Gutter line number
            if (wl.lineIdx != lastIdx) {
                lastIdx = wl.lineIdx;
                String num = String.valueOf(wl.lineIdx + 1);
                int nw = this.textRenderer.getWidth(num);
                this.textRenderer.draw(ms, new LiteralText(num), GUTTER_W - nw - 4, y, 0x445566);
            }

            // Content
            this.textRenderer.draw(ms, wl.text, CONTENT_X, y, 0xDDDDDD);
            y += lineH;
        }

        // Scrollbar
        int visH    = bottom - top;
        int totalH  = wrappedLines.size() * lineH;
        LppUi.fillRect(ms, this.width - SCROLLBAR_W - 4, top, this.width - 4, bottom, 0xFF1A1A2A);
        if (totalH > visH) {
            float ratio  = (float) visH / totalH;
            int   thumbH = Math.max(12, (int)(visH * ratio));
            int   maxSc  = totalH - visH;
            int   thumbY = top + (int)((float) scroll / maxSc * (visH - thumbH));
            LppUi.fillRect(ms, this.width - SCROLLBAR_W - 4, thumbY,
                this.width - 4, thumbY + thumbH, 0xFF6688BB);
        }

        // Search bar label
        this.textRenderer.drawWithShadow(ms, new LiteralText("🔍"), CONTENT_X - 14, searchY + 7, 0x5577AA);

        // Search background
        LppUi.fillRect(ms, 0, searchY, this.width, searchY + SEARCH_H, 0xDD0A0A18);
        LppUi.sepH(ms, searchY, this.width);
        LppUi.sepH(ms, searchY + SEARCH_H, this.width);

        // Footer
        LppUi.fillRect(ms, 0, this.height - FTR_H, this.width, this.height, 0xDD0F0F1A);
        LppUi.sepH(ms, this.height - FTR_H, this.width);

        // Status text in footer center
        int chars = raw.length();
        Text status = new LiteralText(chars + " chars");
        int sw = this.textRenderer.getWidth(status);
        this.textRenderer.drawWithShadow(ms, status, (this.width - sw) / 2f,
            this.height - FTR_H + 8f, LppUi.C_DIM);

        super.render(ms, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int lineH     = this.textRenderer.fontHeight + 2;
        int searchY   = this.height - FTR_H - SEARCH_H - 2;
        int visH      = searchY - LppUi.HDR_H;
        int totalH    = wrappedLines.size() * lineH;
        int maxScroll = Math.max(0, totalH - visH);
        scroll = (int) Math.max(0, Math.min(maxScroll, scroll - amount * lineH * 3));
        return true;
    }

    // ── Build ──────────────────────────────────────────────────────────────

    private void precomputeStats() {
        logicalLines = splitSegments(raw);
        statKeys = 0;
        statVars = 0;
        for (String seg : logicalLines) {
            for (int i = 0; i < seg.length(); i++) {
                char c = seg.charAt(i);
                if (c == '=') statKeys++;
                if (c == '#') statVars++;
            }
        }
    }

    private void updateLines() {
        if (logicalLines == null) precomputeStats();
        rebuildWrapped();
    }

    private void rebuildWrapped() {
        int contentW = Math.max(40, this.width - CONTENT_X - SCROLLBAR_W - 16);
        wrappedLines = new ArrayList<>();
        searchMatchCount = 0;
        String query = lastQuery.toLowerCase().trim();

        for (int i = 0; i < logicalLines.size(); i++) {
            String seg = logicalLines.get(i);
            if (!query.isBlank() && seg.toLowerCase().contains(query)) searchMatchCount++;
            List<OrderedText> wrapped = this.textRenderer.wrapLines(syntaxColor(seg), contentW);
            for (OrderedText ot : wrapped) wrappedLines.add(new WrappedLine(ot, i, seg));
        }

        // Clamp scroll
        if (this.textRenderer != null) {
            int lineH     = this.textRenderer.fontHeight + 2;
            int searchY   = this.height - FTR_H - SEARCH_H - 2;
            int visH      = searchY - LppUi.HDR_H;
            int maxScroll = Math.max(0, wrappedLines.size() * lineH - visH);
            if (scroll > maxScroll) scroll = maxScroll;
        }
    }

    // ── Syntax coloring ────────────────────────────────────────────────────

    private static List<String> splitSegments(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) { out.add(""); return out; }
        int depth = 0, start = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '(' || c == '[') depth++;
            else if (c == ')' || c == ']') depth = Math.max(0, depth - 1);
            else if (c == ';' && depth == 0) {
                out.add(raw.substring(start, i).trim());
                start = i + 1;
            }
        }
        String tail = raw.substring(start).trim();
        if (!tail.isEmpty()) out.add(tail);
        if (out.isEmpty())   out.add(raw);
        return out;
    }

    private static Text syntaxColor(String seg) {
        MutableText out = new LiteralText("");
        int i = 0;
        while (i < seg.length()) {
            char c = seg.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '@') {
                int keyEnd = i;
                while (keyEnd < seg.length()) {
                    char k = seg.charAt(keyEnd);
                    if (k == '=' || k == ',' || k == ';' || k == ')') break;
                    keyEnd++;
                }
                String key = seg.substring(i, keyEnd);
                if (keyEnd < seg.length() && seg.charAt(keyEnd) == '=') {
                    out.append(colored(key, 0x88BBCC));
                    out.append(colored("=", 0x445566));
                    i = keyEnd + 1;
                    int valEnd = i, depth = 0;
                    boolean inQ = false;
                    while (valEnd < seg.length()) {
                        char vc = seg.charAt(valEnd);
                        if (vc == '"') inQ = !inQ;
                        if (!inQ) {
                            if (vc == '(' || vc == '[') depth++;
                            else if (vc == ')' || vc == ']') { if (depth == 0) break; depth--; }
                            else if ((vc == ',' || vc == ';') && depth == 0) break;
                        }
                        valEnd++;
                    }
                    out.append(colorValue(seg.substring(i, valEnd)));
                    i = valEnd;
                } else {
                    out.append(colored(key, 0xDDDDDD));
                    i = keyEnd;
                }
                continue;
            }
            if (c == ',' || c == ';') { out.append(colored(String.valueOf(c), 0x556677)); i++; continue; }
            if (c == '(' || c == ')' || c == '[' || c == ']') { out.append(colored(String.valueOf(c), 0x7799AA)); i++; continue; }
            if (c == '"') {
                int end = seg.indexOf('"', i + 1);
                if (end < 0) end = seg.length() - 1;
                out.append(colored(seg.substring(i, end + 1), 0x88DD88));
                i = end + 1;
                continue;
            }
            if (c == '#') {
                int end = i + 1;
                while (end < seg.length() && (Character.isLetterOrDigit(seg.charAt(end))
                    || seg.charAt(end) == '_')) end++;
                if (end < seg.length() && seg.charAt(end) == '(') {
                    int close = seg.indexOf(')', end);
                    if (close >= 0) end = close + 1;
                }
                out.append(colored(seg.substring(i, end), 0xFFCC44));
                i = end;
                continue;
            }
            out.append(colored(String.valueOf(c), 0xDDDDDD));
            i++;
        }
        return out;
    }

    private static Text colorValue(String val) {
        if (val.startsWith("\"") || val.startsWith("'")) return colored(val, 0x88DD88);
        if (val.contains("#")) return syntaxColor(val);
        if (val.matches("-?[0-9]+(\\.[0-9]+)?[bBsSlLfFdD]?")) return colored(val, 0xAADDFF);
        if (val.equalsIgnoreCase("true") || val.equalsIgnoreCase("false")) return colored(val, 0xFFAA55);
        return colored(val, 0xDDDDDD);
    }

    private static MutableText colored(String text, int rgb) {
        return new LiteralText(text).setStyle(Style.EMPTY.withColor(rgb));
    }

    private record WrappedLine(OrderedText text, int lineIdx, String logicalText) {}
}
