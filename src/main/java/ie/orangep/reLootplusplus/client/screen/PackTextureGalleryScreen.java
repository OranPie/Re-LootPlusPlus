package ie.orangep.reLootplusplus.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import ie.orangep.reLootplusplus.client.AddonTextureLoader;
import ie.orangep.reLootplusplus.pack.AddonPack;
import ie.orangep.reLootplusplus.resourcepack.AddonResourceIndex;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen texture gallery browser for a single addon pack.
 *
 * <p>Layout:
 * <pre>
 *  ┌────────────────────────────────────────┐
 *  │ HEADER  (44px)                         │
 *  ├────────────────────────────────────────┤
 *  │ [All][Blocks][Items][Entity]... (22px) │  filter chips
 *  │ [🔍 Search…                  ] (22px) │  search bar
 *  ├────────────────────────────────────────┤
 *  │  Scrollable tile list (variable)       │
 *  ├────────────────────────────────────────┤
 *  │  Preview panel (90px)                  │
 *  ├────────────────────────────────────────┤
 *  │  FOOTER  (32px)                        │
 *  └────────────────────────────────────────┘
 * </pre>
 */
public final class PackTextureGalleryScreen extends Screen {

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int FILTER_H   = 22;
    private static final int SEARCH_H   = 22;
    private static final int PREVIEW_H  = 90;
    private static final int ROW_H      = 58;  // height of each list row

    private static final int THUMB_SIZE = 48;  // thumbnail pixel size inside row
    private static final int ROW_PAD_L  = 6;
    private static final int ROW_PAD_V  = 5;

    // ── State ─────────────────────────────────────────────────────────────────
    private final Screen parent;
    private final AddonPack pack;

    /** All textures in this pack (full list, never filtered). */
    private List<AddonResourceIndex.TexEntry> allTextures;

    /** Currently displayed (filtered) list. */
    private List<AddonResourceIndex.TexEntry> filtered;

    /** Active category filter — null = show all. */
    private String activeCategory = null;

    /** Selected entry for the preview panel. */
    private AddonResourceIndex.TexEntry selected;

    // ── Widgets ───────────────────────────────────────────────────────────────
    private TexListWidget listWidget;
    private TextFieldWidget searchField;

    // ── Filter chip positions (computed in init) ──────────────────────────────
    private final List<int[]> chipBounds = new ArrayList<>();  // [x0, x1, categoryOrNull]
    private final List<String> chipCategories = new ArrayList<>();

    public PackTextureGalleryScreen(Screen parent, AddonPack pack) {
        super(new TranslatableText("menu.relootplusplus.tex_gallery.title", pack.id()));
        this.parent = parent;
        this.pack   = pack;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        allTextures = AddonResourceIndex.scanAllTextures(pack);
        filtered    = new ArrayList<>(allTextures);

        int contentTop = LppUi.HDR_H + FILTER_H + SEARCH_H;
        int previewTop = this.height - LppUi.FTR_H - PREVIEW_H;
        int listBottom = previewTop;

        listWidget = new TexListWidget(this.client, this.width, this.height, contentTop, listBottom, ROW_H);
        this.addSelectableChild(listWidget);
        rebuildList();

        int sfY = LppUi.HDR_H + FILTER_H + 1;
        searchField = new TextFieldWidget(this.textRenderer, 4, sfY, this.width - 8, SEARCH_H - 2, new LiteralText(""));
        searchField.setSuggestion(new LiteralText("Search textures…").getString());
        searchField.setMaxLength(120);
        searchField.setChangedListener(q -> applyFilter());
        this.addSelectableChild(searchField);
        this.addDrawableChild(searchField);

        // Back button
        addDrawableChild(new ButtonWidget(4, this.height - LppUi.FTR_H + 6, 60, 20,
                new TranslatableText("menu.relootplusplus.back"), b -> this.client.setScreen(parent)));

        buildChipBounds();
    }

    private void buildChipBounds() {
        chipBounds.clear();
        chipCategories.clear();

        List<String> cats = new ArrayList<>();
        cats.add(null); // "All"
        for (AddonResourceIndex.TexEntry e : allTextures) {
            if (!cats.contains(e.category())) cats.add(e.category());
        }

        int x = 4;
        int y = LppUi.HDR_H + 2;
        for (String cat : cats) {
            String label = cat == null ? "All" : capitalize(cat);
            int w = this.textRenderer.getWidth(label) + 10;
            chipBounds.add(new int[]{x, x + w, y, y + FILTER_H - 4});
            chipCategories.add(cat);
            x += w + 3;
        }
    }

    @Override
    public void render(MatrixStack ms, int mX, int mY, float dt) {
        this.renderBackground(ms);
        listWidget.render(ms, mX, mY, dt);
        searchField.render(ms, mX, mY, dt);

        // Header
        LppUi.drawHeader(ms, this.width,
                new TranslatableText("menu.relootplusplus.tex_gallery.title", pack.id()),
                new LiteralText(filtered.size() + " / " + allTextures.size() + " textures"),
                this.textRenderer);

        // Filter chips
        renderFilterChips(ms, mX, mY);

        // Preview panel
        renderPreview(ms);

        // Footer
        LppUi.drawFooter(ms, this.width, this.height, null, this.textRenderer);

        // Super (renders button widgets over everything)
        super.render(ms, mX, mY, dt);
    }

    private void renderFilterChips(MatrixStack ms, int mX, int mY) {
        for (int i = 0; i < chipBounds.size(); i++) {
            int[] b = chipBounds.get(i);
            String cat = chipCategories.get(i);
            String label = cat == null ? "All" : capitalize(cat);
            boolean active = java.util.Objects.equals(cat, activeCategory);
            boolean hov    = mX >= b[0] && mX < b[1] && mY >= b[2] && mY < b[3];

            int bg  = active ? 0xCC3A5A8A : (hov ? 0x55334455 : 0x44222233);
            int fg  = active ? 0xFFFFCC44 : (hov ? 0xFFCCDDEE : 0xFF8899BB);
            LppUi.fillRect(ms, b[0], b[2], b[1], b[3], bg);
            if (active) LppUi.fillRect(ms, b[0], b[2], b[0] + 2, b[3], 0xFFFFCC44);
            this.textRenderer.drawWithShadow(ms, label, b[0] + 5, b[2] + 3, fg);
        }
    }

    private void renderPreview(MatrixStack ms) {
        int py = this.height - LppUi.FTR_H - PREVIEW_H;
        LppUi.fillRect(ms, 0, py, this.width, py + 1, LppUi.SEP);
        LppUi.fillRect(ms, 0, py + 1, this.width, this.height - LppUi.FTR_H, 0xBB0A0A1A);

        if (selected == null) {
            String hint = new TranslatableText("menu.relootplusplus.tex_gallery.select_hint").getString();
            int hw = this.textRenderer.getWidth(hint);
            this.textRenderer.drawWithShadow(ms, hint,
                    (this.width - hw) / 2f, py + (PREVIEW_H - 9) / 2f, LppUi.C_DIM);
            return;
        }

        // Load the texture
        Identifier texId = AddonTextureLoader.getOrLoad(pack, selected.innerPath());
        int[] dims = texId != null ? AddonTextureLoader.getDims(pack, selected.innerPath()) : null;

        int previewSize = PREVIEW_H - 14;
        int tx = 8;
        int ty = py + 7;

        if (texId != null && dims != null) {
            int iw = dims[0], ih = dims[1];
            int dw = previewSize, dh = previewSize;
            if (iw > ih) dh = (int)(previewSize * (float) ih / iw);
            else         dw = (int)(previewSize * (float) iw / ih);
            RenderSystem.enableBlend();
            LppUi.drawSprite(ms, texId, tx + (previewSize - dw) / 2, ty + (previewSize - dh) / 2, dw, dh, iw, ih);
            RenderSystem.disableBlend();
            // border
            LppUi.fillRect(ms, tx - 1, ty - 1, tx + previewSize + 1, ty, 0xFF3A4A6A);
            LppUi.fillRect(ms, tx - 1, ty + previewSize, tx + previewSize + 1, ty + previewSize + 1, 0xFF3A4A6A);
            LppUi.fillRect(ms, tx - 1, ty, tx, ty + previewSize, 0xFF3A4A6A);
            LppUi.fillRect(ms, tx + previewSize, ty, tx + previewSize + 1, ty + previewSize, 0xFF3A4A6A);
        } else {
            LppUi.fillRect(ms, tx, ty, tx + previewSize, ty + previewSize, 0xFF222233);
            this.textRenderer.drawWithShadow(ms, "?", tx + previewSize / 2 - 2, ty + previewSize / 2 - 4, 0xFF445566);
        }

        int ix = tx + previewSize + 10;
        int iy = ty;
        int lineH = 11;
        this.textRenderer.drawWithShadow(ms, "§e" + selected.name(), ix, iy, 0xFFFFFFFF); iy += lineH;
        this.textRenderer.draw(ms, "§7Category: §f" + selected.category(), ix, iy, 0); iy += lineH;
        this.textRenderer.draw(ms, "§7Namespace: §f" + selected.namespace(), ix, iy, 0); iy += lineH;
        if (dims != null) {
            this.textRenderer.draw(ms, "§7Size: §f" + dims[0] + " × " + dims[1], ix, iy, 0); iy += lineH;
        }
        int maxPathW = this.width - ix - 8;
        String path = LppUi.clip(selected.innerPath(), maxPathW, this.textRenderer);
        this.textRenderer.draw(ms, "§8" + path, ix, iy, 0);
    }

    // ── Mouse input ───────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Check filter chips
        if (my >= LppUi.HDR_H && my < LppUi.HDR_H + FILTER_H) {
            for (int i = 0; i < chipBounds.size(); i++) {
                int[] b = chipBounds.get(i);
                if (mx >= b[0] && mx < b[1] && my >= b[2] && my < b[3]) {
                    String cat = chipCategories.get(i);
                    activeCategory = java.util.Objects.equals(cat, activeCategory) ? null : cat;
                    applyFilter();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    // ── Keyboard ──────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 /* ESC */) { this.client.setScreen(parent); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Filter logic ──────────────────────────────────────────────────────────

    private void applyFilter() {
        String q = searchField != null ? searchField.getText().toLowerCase(Locale.ROOT) : "";
        filtered = new ArrayList<>();
        for (AddonResourceIndex.TexEntry e : allTextures) {
            if (activeCategory != null && !activeCategory.equals(e.category())) continue;
            if (!q.isEmpty() && !e.name().toLowerCase(Locale.ROOT).contains(q)
                             && !e.namespace().toLowerCase(Locale.ROOT).contains(q)) continue;
            filtered.add(e);
        }
        rebuildList();
    }

    private void rebuildList() {
        if (listWidget == null) return;
        listWidget.clearAll();
        for (AddonResourceIndex.TexEntry e : filtered) {
            listWidget.addRaw(new TexRow(e));
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ── List widget ───────────────────────────────────────────────────────────

    @SuppressWarnings("rawtypes")
    private final class TexListWidget extends AlwaysSelectedEntryListWidget<TexRow> {

        TexListWidget(MinecraftClient client, int width, int height, int top, int bottom, int itemH) {
            super(client, width, height, top, bottom, itemH);
        }

        @Override protected int getScrollbarPositionX() { return this.width - 6; }

        void clearAll()          { super.clearEntries(); }
        void addRaw(TexRow row)  { super.addEntry(row); }
    }

    // ── Row entry ─────────────────────────────────────────────────────────────

    private final class TexRow extends AlwaysSelectedEntryListWidget.Entry<TexRow> {
        private final AddonResourceIndex.TexEntry entry;

        TexRow(AddonResourceIndex.TexEntry entry) { this.entry = entry; }

        @Override
        public void render(MatrixStack ms, int idx, int y, int x, int eW, int eH,
                           int mX, int mY, boolean hov, float dt) {
            boolean sel = entry == selected;
            if (sel) LppUi.fillRect(ms, x, y, x + eW, y + eH, LppUi.SEL_BG);
            else if (hov) LppUi.fillRect(ms, x, y, x + eW, y + eH, LppUi.HOVER_BG);
            else if (idx % 2 == 0) LppUi.fillRect(ms, x, y, x + eW, y + eH, LppUi.STRIPE);

            // Left stripe color by category
            int stripeColor = categoryColor(entry.category());
            LppUi.stripe(ms, x, y, eH, stripeColor);

            // Thumbnail (48×48, preserving aspect ratio)
            int tx = x + ROW_PAD_L + 3;
            int ty = y + ROW_PAD_V;
            LppUi.fillRect(ms, tx - 1, ty - 1, tx + THUMB_SIZE + 1, ty + THUMB_SIZE + 1, 0x44334455);
            Identifier texId = AddonTextureLoader.getOrLoad(pack, entry.innerPath());
            if (texId != null) {
                int[] dims = AddonTextureLoader.getDims(pack, entry.innerPath());
                if (dims != null && dims[0] > 0 && dims[1] > 0) {
                    int iw = dims[0], ih = dims[1];
                    int dw = THUMB_SIZE, dh = THUMB_SIZE;
                    if (iw > ih) dh = (int)(THUMB_SIZE * (float) ih / iw);
                    else         dw = (int)(THUMB_SIZE * (float) iw / ih);
                    int ox = (THUMB_SIZE - dw) / 2, oy = (THUMB_SIZE - dh) / 2;
                    RenderSystem.enableBlend();
                    LppUi.drawSprite(ms, texId, tx + ox, ty + oy, dw, dh, iw, ih);
                    RenderSystem.disableBlend();
                }
            } else {
                textRenderer.draw(ms, "?", tx + THUMB_SIZE / 2f - 2, ty + THUMB_SIZE / 2f - 4, 0xFF334455);
            }

            // Info text to the right of thumbnail
            int infoX = tx + THUMB_SIZE + 8;
            int infoY = ty + 2;
            int lineH = 11;
            int maxW = eW - (infoX - x) - 8;

            textRenderer.drawWithShadow(ms, LppUi.clip(entry.name(), maxW, textRenderer),
                    infoX, infoY, sel ? 0xFFFFCC44 : 0xFFDDEEFF);
            infoY += lineH;
            textRenderer.draw(ms, "§7" + entry.category() + "  §8" + entry.namespace(),
                    infoX, infoY, 0);
            infoY += lineH;
            int[] dims = AddonTextureLoader.getDims(pack, entry.innerPath());
            if (dims != null) {
                textRenderer.draw(ms, "§8" + dims[0] + "×" + dims[1], infoX, infoY, 0);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            selected = entry;
            return true;
        }

        @Override
        public Text getNarration() {
            return new LiteralText(entry.name());
        }
    }

    // ── Category color ────────────────────────────────────────────────────────

    private static int categoryColor(String cat) {
        return switch (cat) {
            case "blocks"      -> 0xFF6699DD;
            case "items"       -> 0xFF55BB66;
            case "entity"      -> 0xFFCC8833;
            case "gui"         -> 0xFF9955CC;
            case "particle"    -> 0xFF55CCCC;
            case "environment" -> 0xFF33BBAA;
            default            -> 0xFF667788;
        };
    }
}
