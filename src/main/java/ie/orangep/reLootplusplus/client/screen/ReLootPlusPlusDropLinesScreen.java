package ie.orangep.reLootplusplus.client.screen;

import ie.orangep.reLootplusplus.lucky.attr.LuckyAttr;
import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

import java.util.*;
import java.util.stream.Collectors;

/**
 * In-game screen showing the parsed Lucky Drop lines for a single addon pack.
 *
 * <p>Layout (top to bottom):
 * <ul>
 *   <li><b>Header</b> ({@link LppUi#HDR_H} px): title + pack name + filter info</li>
 *   <li><b>Stats bar</b> ({@link LppUi#STATS_H} px): interactive type-distribution chips</li>
 *   <li><b>Drop list</b> (fills remaining space above detail panel)</li>
 *   <li><b>Detail panel</b> (DETAIL_H px): structured 2-col attr view of selected drop</li>
 *   <li><b>Footer</b> ({@link LppUi#FTR_H} px): back button + status text</li>
 * </ul>
 */
public final class ReLootPlusPlusDropLinesScreen extends Screen {

    private static final int DETAIL_H = 90;

    private final Screen parent;
    private final String packId;
    private final List<LuckyDropLine> allDrops;

    // Live filter
    private String activeFilter  = null;
    private List<LuckyDropLine> filteredDrops;

    // Selection
    private LuckyDropLine selectedDrop  = null;
    private int            selectedIndex = -1;

    // From stats bar render (for click detection)
    private List<int[]>   chipBounds = List.of();
    private List<String>  chipTypes  = new ArrayList<>();

    private final Map<String, Integer> typeCounts = new LinkedHashMap<>();
    private DropListWidget listWidget;

    public ReLootPlusPlusDropLinesScreen(Screen parent, String packId, List<LuckyDropLine> drops) {
        super(new LiteralText(""));
        this.parent   = parent;
        this.packId   = packId;
        this.allDrops = drops;
        buildTypeCounts();
        filteredDrops = new ArrayList<>(allDrops);
    }

    private void buildTypeCounts() {
        for (LuckyDropLine d : allDrops) {
            String t = d.isGroup() ? "group" : (d.type() != null ? d.type() : "?");
            typeCounts.merge(t, 1, Integer::sum);
        }
    }

    // ── lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        int statsBottom = LppUi.HDR_H + LppUi.STATS_H;
        int listBottom  = this.height - DETAIL_H - LppUi.FTR_H;
        listWidget = new DropListWidget(this.client, this.width, this.height,
            statsBottom, listBottom, LppUi.ROW_H);
        listWidget.setDrops(filteredDrops);
        this.addSelectableChild(listWidget);

        this.addDrawableChild(new ButtonWidget(8, this.height - LppUi.FTR_H + 6, 60, 20,
            new TranslatableText("menu.relootplusplus.back"),
            b -> this.client.setScreen(parent)));
    }

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float delta) {
        this.renderBackground(ms);

        // ── Header ────────────────────────────────────────────────────────────
        Text title    = new LiteralText("✦ Lucky Drops  ·  " + packId);
        Text subtitle;
        if (activeFilter != null) {
            subtitle = new LiteralText("Showing " + filteredDrops.size()
                + " of " + allDrops.size() + " drops  (filter: " + activeFilter + ")");
        } else {
            subtitle = new LiteralText(allDrops.size() + " drops loaded");
        }
        LppUi.drawHeader(ms, this.width, title, subtitle, this.textRenderer);

        // ── Stats chip bar (interactive) ──────────────────────────────────────
        List<String> outTypes = new ArrayList<>();
        chipBounds = LppUi.drawStatsBar(ms,
            LppUi.HDR_H, LppUi.STATS_H, this.width,
            typeCounts, activeFilter, outTypes, this.textRenderer);
        chipTypes  = outTypes;

        // ── List ──────────────────────────────────────────────────────────────
        listWidget.render(ms, mouseX, mouseY, delta);

        // ── Detail panel ──────────────────────────────────────────────────────
        renderDetailPanel(ms);

        // ── Footer ────────────────────────────────────────────────────────────
        String sel = selectedDrop != null
            ? "#" + (selectedIndex + 1) + " of " + filteredDrops.size()
            : filteredDrops.size() + " total  ·  ↑↓ navigate";
        LppUi.drawFooter(ms, this.width, this.height,
            new LiteralText(sel), this.textRenderer);

        super.render(ms, mouseX, mouseY, delta);
    }

    // ── Mouse handling ────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int statsY0 = LppUi.HDR_H;
        int statsY1 = LppUi.HDR_H + LppUi.STATS_H;
        if (my >= statsY0 && my < statsY1) {
            for (int i = 0; i < chipBounds.size(); i++) {
                int[] b = chipBounds.get(i);
                if (mx >= b[0] && mx < b[1]) {
                    String type = chipTypes.get(i);
                    activeFilter = type.equals(activeFilter) ? null : type;
                    applyFilter();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    // ── Keyboard handling ─────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 264 /* DOWN */ || keyCode == 265 /* UP */) {
            int delta = keyCode == 264 ? 1 : -1;
            int next  = Math.max(0, Math.min(filteredDrops.size() - 1,
                (selectedIndex < 0 ? (delta > 0 ? 0 : filteredDrops.size() - 1)
                    : selectedIndex + delta)));
            if (!filteredDrops.isEmpty()) {
                selectedIndex = next;
                selectedDrop  = filteredDrops.get(next);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Filter ────────────────────────────────────────────────────────────────

    private void applyFilter() {
        if (activeFilter == null) {
            filteredDrops = new ArrayList<>(allDrops);
        } else {
            String f = activeFilter;
            filteredDrops = allDrops.stream()
                .filter(d -> {
                    String t = d.isGroup() ? "group" : (d.type() != null ? d.type() : "?");
                    return t.equals(f);
                })
                .collect(Collectors.toList());
        }
        selectedDrop  = null;
        selectedIndex = -1;
        listWidget.setDrops(filteredDrops);
    }

    // ── Detail panel ─────────────────────────────────────────────────────────

    private void renderDetailPanel(MatrixStack ms) {
        int panelY = this.height - DETAIL_H - LppUi.FTR_H;
        LppUi.sepH(ms, panelY, this.width);
        LppUi.fillRect(ms, 0, panelY + 1, this.width, this.height - LppUi.FTR_H, 0xDD0E1420);

        if (selectedDrop == null) {
            this.textRenderer.drawWithShadow(ms,
                new LiteralText("Select a drop to inspect — double-click for full raw view"),
                10f, panelY + DETAIL_H * 0.5f - 4, 0x44AABBCC);
            return;
        }

        LuckyDropLine d = selectedDrop;
        int x  = 10;
        int y  = panelY + 5;
        int rh = this.textRenderer.fontHeight + 3;

        // ── Row 1: badge + index + label + luck bar ──────────────────────────
        String type  = d.isGroup() ? "group" : (d.type() != null ? d.type() : "?");
        int    color = LppUi.typeColor(type);
        int    bW    = LppUi.drawBadge(ms, x, y, 13, type.toUpperCase(Locale.ROOT), color, this.textRenderer);

        String idLabel = " #" + (selectedIndex + 1) + "  " + buildLabel(d);
        this.textRenderer.drawWithShadow(ms, new LiteralText(idLabel),
            x + bW + 4, y + 2, LppUi.C_BODY);

        // Luck / chance (right-aligned)
        int lw = d.luckWeight();
        String right = (lw >= 0 ? "+" : "") + lw;
        if (d.chance() < 1f) right += "  " + String.format("%.0f%%", d.chance() * 100f);
        int rw = this.textRenderer.getWidth(right);
        int bX = this.width - rw - 30;
        LppUi.drawLuckBar(ms, bX - 26, y + 4, lw);
        this.textRenderer.drawWithShadow(ms, new LiteralText(right), bX, y + 2, LppUi.C_NUM);

        y += rh + 3;

        // ── Row 2-N: structured attr table or group breakdown ────────────────
        if (!d.isGroup()) {
            Map<String, LuckyAttr> rawAttrs = d.attrs();
            if (rawAttrs != null && !rawAttrs.isEmpty()) {
                Map<String, String> table = new LinkedHashMap<>();
                for (Map.Entry<String, LuckyAttr> e : rawAttrs.entrySet()) {
                    String k = e.getKey();
                    if (k.equals("type") || k.equals("ID") || k.equals("id")) continue;
                    table.put(k, attrToDisplay(e.getValue()));
                }
                int maxW = this.width - x * 2;
                int rows = (DETAIL_H - rh * 2 - 10) / rh;
                LppUi.drawAttrTable(ms, x, y, maxW, table, Math.max(2, rows), this.textRenderer);
            } else {
                this.textRenderer.drawWithShadow(ms, new LiteralText("(no extra attributes)"),
                    x, y + 2, LppUi.C_DIM);
            }

            // NBT hint at bottom
            String nbt = buildNbtPreview(d);
            if (nbt != null) {
                int ny = panelY + DETAIL_H - rh - 4;
                this.textRenderer.drawWithShadow(ms,
                    new LiteralText(LppUi.clip(nbt, this.width - x * 2, this.textRenderer)),
                    x, ny, LppUi.C_DIM);
            }
        } else {
            // Group: sub-type distribution chips + pick mode
            List<LuckyDropLine> entries = d.groupEntries();
            Map<String, Integer> sub = new LinkedHashMap<>();
            if (entries != null) {
                for (LuckyDropLine e : entries) {
                    String t = e.isGroup() ? "group" : (e.type() != null ? e.type() : "?");
                    sub.merge(t, 1, Integer::sum);
                }
            }
            StringBuilder sb = new StringBuilder("Contains: ");
            for (Map.Entry<String, Integer> e : sub.entrySet()) {
                sb.append(e.getKey()).append("×").append(e.getValue()).append("  ");
            }
            sb.append(d.groupCount() > 0 ? "(pick " + d.groupCount() + ")" : "(all)");
            this.textRenderer.drawWithShadow(ms, new LiteralText(sb.toString()), x, y + 2, 0xFFCC44);
        }
    }

    // ── Inner list widget ─────────────────────────────────────────────────────

    private final class DropListWidget extends AlwaysSelectedEntryListWidget<DropListWidget.Entry> {
        DropListWidget(MinecraftClient client, int width, int height,
                       int top, int bottom, int itemHeight) {
            super(client, width, height, top, bottom, itemHeight);
        }

        void setDrops(List<LuckyDropLine> drops) {
            this.clearEntries();
            int idx = 0;
            for (LuckyDropLine drop : drops) this.addEntry(new Entry(idx++, drop));
        }

        @Override protected int getScrollbarPositionX() { return this.width - 6; }

        final class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
            private final int          index;
            private final LuckyDropLine drop;
            private long               lastClick;

            Entry(int index, LuckyDropLine drop) {
                this.index = index;
                this.drop  = drop;
            }

            @Override
            public void render(MatrixStack ms, int entryIdx, int y, int x,
                               int entryWidth, int entryHeight,
                               int mouseX, int mouseY, boolean hovered, float delta) {

                boolean sel = (selectedIndex == index);

                if      (sel)              LppUi.fillRect(ms, x, y, x + entryWidth, y + entryHeight, LppUi.SEL_BG);
                else if (hovered)          LppUi.fillRect(ms, x, y, x + entryWidth, y + entryHeight, LppUi.HOVER_BG);
                else if (entryIdx % 2 == 0)LppUi.fillRect(ms, x, y, x + entryWidth, y + entryHeight, LppUi.STRIPE);

                String type  = drop.isGroup() ? "group" : (drop.type() != null ? drop.type() : "?");
                int    color = LppUi.typeColor(type);

                // 3px left stripe
                LppUi.stripe(ms, x, y, entryHeight, color);

                // Type badge (fixed 40px wide)
                int badgeX = x + 6;
                int badgeW = LppUi.drawBadge(ms, badgeX, y + 3, entryHeight - 6,
                    type.substring(0, Math.min(4, type.length())).toUpperCase(Locale.ROOT),
                    color, textRenderer);

                // Index (#N)
                int numX = badgeX + badgeW + 4;
                textRenderer.drawWithShadow(ms, new LiteralText("#" + (index + 1)),
                    numX, y + (entryHeight - textRenderer.fontHeight) * 0.5f, LppUi.C_DIM);

                // Label (clipped)
                int labelX   = numX + 28;
                int luckBarX = x + entryWidth - 34;
                int rightW   = textRenderer.getWidth("±000  100%") + 4;
                int maxLabelW = luckBarX - labelX - rightW - 8;
                if (maxLabelW > 12) {
                    textRenderer.drawWithShadow(ms,
                        new LiteralText(LppUi.clip(buildLabel(drop), maxLabelW, textRenderer)),
                        labelX, y + (entryHeight - textRenderer.fontHeight) * 0.5f, LppUi.C_BODY);
                }

                // Luck bar
                int lw = drop.luckWeight();
                LppUi.drawLuckBar(ms, luckBarX, y + (entryHeight - 5) / 2, lw);

                // Right: ±lw + chance
                String rightText = buildRight(drop);
                if (drop.isGroup() && drop.groupEntries() != null) {
                    rightText = "×" + drop.groupEntries().size() + " " + rightText;
                }
                int rw = textRenderer.getWidth(rightText);
                textRenderer.drawWithShadow(ms, new LiteralText(rightText),
                    x + entryWidth - rw - 6, y + (entryHeight - textRenderer.fontHeight) * 0.5f,
                    LppUi.C_NUM);
            }

            @Override public Text getNarration() {
                return new LiteralText(buildLabel(drop) + " " + buildRight(drop));
            }

            @Override
            public boolean mouseClicked(double mx, double my, int button) {
                setSelected(this);
                selectedDrop  = drop;
                selectedIndex = index;
                long now = System.currentTimeMillis();
                if (now - lastClick < 400) {
                    // Double-click → open raw line viewer
                    String raw = drop.toDisplayString();
                    if (!raw.isBlank()) {
                        client.setScreen(new ReLootPlusPlusRawLineScreen(
                            ReLootPlusPlusDropLinesScreen.this, raw));
                    }
                }
                lastClick = now;
                return true;
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String buildLabel(LuckyDropLine drop) {
        if (drop.isGroup()) {
            int n = drop.groupEntries() != null ? drop.groupEntries().size() : 0;
            return "group  (" + n + " entries)";
        }
        String id = drop.rawId();
        if (id != null && !id.isBlank()) return id;
        String cmd = drop.getString("command");
        if (cmd != null && !cmd.isBlank())
            return "/" + (cmd.length() > 40 ? cmd.substring(0, 40) + "…" : cmd);
        return "(no id)";
    }

    private static String buildRight(LuckyDropLine drop) {
        int   lw = drop.luckWeight();
        float ch = drop.chance();
        String s = (lw >= 0 ? "+" : "") + lw;
        if (ch < 1f) s += "  " + String.format("%.0f%%", ch * 100f);
        return s;
    }

    private static String buildNbtPreview(LuckyDropLine drop) {
        if (drop.isGroup()) return null;
        String nbt = drop.getString("NBTTag");
        if (nbt == null) nbt = drop.getString("nbt");
        if (nbt != null && !nbt.isBlank()) return "NBT: " + nbt;
        String cmd = drop.getString("command");
        if (cmd != null && !cmd.isBlank()) return "cmd: " + cmd;
        return null;
    }

    private static String attrToDisplay(LuckyAttr attr) {
        if (attr instanceof LuckyAttr.StringAttr s) return s.value();
        if (attr instanceof LuckyAttr.DictAttr)    return "(…)";
        if (attr instanceof LuckyAttr.ListAttr l)  return "[" + l.items().size() + " items]";
        return "?";
    }
}
