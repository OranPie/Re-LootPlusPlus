package ie.orangep.reLootplusplus.client.screen;

import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.lucky.loader.LuckyAddonData;
import ie.orangep.reLootplusplus.lucky.loader.LuckyAddonLoader;
import ie.orangep.reLootplusplus.lucky.loader.LuckyAddonProperties;
import ie.orangep.reLootplusplus.lucky.loader.LuckyStructureEntry;
import ie.orangep.reLootplusplus.pack.AddonPack;
import ie.orangep.reLootplusplus.resourcepack.AddonResourceIndex;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.List;
import java.util.Locale;

/**
 * Pack detail screen — tabs: Overview / Items / Drops / Structures.
 */
public final class ReLootPlusPlusPackDetailScreen extends Screen {

    private static final int CONTENT_TOP = LppUi.HDR_H + LppUi.TAB_H;

    private static final int TAB_OVERVIEW   = 0;
    private static final int TAB_ITEMS      = 1;
    private static final int TAB_DROPS      = 2;
    private static final int TAB_STRUCTURES = 3;

    private final Screen parent;
    private final AddonPack pack;
    private final LuckyAddonData data;

    private int activeTab = TAB_OVERVIEW;
    private List<int[]> tabBounds = List.of();

    // Shared list widget (repopulated on tab switch)
    private GenericListWidget listWidget;
    private ButtonWidget actionButton;
    private ButtonWidget detailButton;

    // Items tab selection
    private AddonResourceIndex.ItemModelInfo selectedItem;

    public ReLootPlusPlusPackDetailScreen(Screen parent, AddonPack pack) {
        super(new TranslatableText("menu.relootplusplus.pack_detail.title", pack.id()));
        this.parent = parent;
        this.pack   = pack;
        this.data   = LuckyAddonLoader.getAddonDataList().stream()
            .filter(d -> d.packId().equals(pack.id())).findFirst().orElse(null);
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    protected void init() {
        int listBottom = this.height - LppUi.FTR_H;
        listWidget = new GenericListWidget(this.client, this.width, this.height,
            CONTENT_TOP, listBottom, LppUi.ROW_H);
        this.addSelectableChild(listWidget);

        // Back button
        this.addDrawableChild(new ButtonWidget(8, this.height - LppUi.FTR_H + 6, 60, 20,
            new TranslatableText("menu.relootplusplus.back"),
            b -> this.client.setScreen(parent)));

        // Action button (right side of footer — changes per tab)
        actionButton = new ButtonWidget(this.width - 120, this.height - LppUi.FTR_H + 6, 112, 20,
            new LiteralText(""), b -> handleAction());
        this.addDrawableChild(actionButton);

        // Item detail button (only on Items tab)
        detailButton = new ButtonWidget(this.width - 238, this.height - LppUi.FTR_H + 6, 112, 20,
            new TranslatableText("menu.relootplusplus.item_details"), b -> openItemDetail());
        detailButton.active = false;
        this.addDrawableChild(detailButton);

        switchTab(activeTab);
    }

    // ── Render ─────────────────────────────────────────────────────────────

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float delta) {
        this.renderBackground(ms);

        // Header
        String src = pack.zipPath() != null ? pack.zipPath().getFileName().toString() : pack.id();
        boolean isZip = src.endsWith(".zip");
        Text subtitle = new LiteralText(src + "  " + (isZip ? "[ZIP]" : "[DIR]"));
        LppUi.drawHeader(ms, this.width, this.title, subtitle, this.textRenderer);

        // Tab bar
        List<String> labels = tabLabels();
        tabBounds = LppUi.drawTabBar(ms, LppUi.HDR_H, this.width, labels, activeTab, this.textRenderer);

        listWidget.render(ms, mouseX, mouseY, delta);

        // Overview tab renders cards directly (no list widget)
        if (activeTab == TAB_OVERVIEW) {
            renderOverview(ms);
        }

        // Footer
        LppUi.drawFooter(ms, this.width, this.height, statusText(), this.textRenderer);

        super.render(ms, mouseX, mouseY, delta);
    }

    // ── Mouse handling ─────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int tabY0 = LppUi.HDR_H;
        int tabY1 = LppUi.HDR_H + LppUi.TAB_H;
        if (my >= tabY0 && my < tabY1) {
            for (int i = 0; i < tabBounds.size(); i++) {
                int[] b = tabBounds.get(i);
                if (mx >= b[0] && mx < b[1]) {
                    switchTab(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    // ── Tab switching ─────────────────────────────────────────────────────

    private void switchTab(int tab) {
        activeTab    = tab;
        selectedItem = null;
        listWidget.clearAll();
        detailButton.visible = false;
        actionButton.visible = false;

        switch (tab) {
            case TAB_OVERVIEW -> {
                // No list widget content; rendered directly in renderOverview
            }
            case TAB_ITEMS -> {
                List<AddonResourceIndex.ItemModelInfo> items = AddonResourceIndex.scanItemModels(pack);
                for (AddonResourceIndex.ItemModelInfo info : items) {
                    listWidget.addEntryRaw(new ItemRow(info));
                }
                detailButton.visible = true;
                detailButton.active  = false;
                actionButton.visible = true;
                actionButton.setMessage(new TranslatableText("menu.relootplusplus.item_details"));
            }
            case TAB_DROPS -> {
                List<LuckyDropLine> drops = data != null ? data.parsedDrops() : List.of();
                for (int i = 0; i < drops.size(); i++) {
                    listWidget.addEntryRaw(new DropRow(i, drops.get(i)));
                }
                actionButton.visible = true;
                int dc = drops.size();
                actionButton.setMessage(new LiteralText("✦ All Drops (" + dc + ")"));
            }
            case TAB_STRUCTURES -> {
                List<LuckyStructureEntry> structs = data != null ? data.structureEntries() : List.of();
                for (LuckyStructureEntry s : structs) {
                    listWidget.addEntryRaw(new StructureRow(s));
                }
            }
        }
    }

    // ── Overview rendering ─────────────────────────────────────────────────

    private void renderOverview(MatrixStack ms) {
        int x = 16;
        int y = CONTENT_TOP + 10;
        int maxW = this.width - x * 2;

        // Pack path
        String src  = pack.zipPath() != null ? pack.zipPath().toString() : pack.id();
        y = LppUi.drawInfoRow(ms, x, y, "Path", src, maxW, this.textRenderer);

        if (data != null) {
            y = LppUi.drawInfoRow(ms, x, y, "Drops",       String.valueOf(data.parsedDrops().size()),     maxW, this.textRenderer);
            y = LppUi.drawInfoRow(ms, x, y, "Bow drops",   String.valueOf(data.parsedBowDrops().size()),  maxW, this.textRenderer);
            y = LppUi.drawInfoRow(ms, x, y, "Structures",  String.valueOf(data.structureEntries().size()), maxW, this.textRenderer);
            y = LppUi.drawInfoRow(ms, x, y, "Natural gen", String.valueOf(data.naturalGenEntries().size()), maxW, this.textRenderer);

            // Plugin init presence
            boolean hasPlugin = data.pluginInit() != null;
            y = LppUi.drawInfoRow(ms, x, y, "plugin_init", hasPlugin ? "present" : "absent", maxW, this.textRenderer);

            // Properties
            if (data.properties() != null) {
                LuckyAddonProperties props = data.properties();
                y = LppUi.drawInfoRow(ms, x, y, "Properties",
                    "spawnRate=" + props.spawnRate() + " strChance=" + props.structureChance()
                    + " creative=" + props.doDropsOnCreativeMode(),
                    maxW, this.textRenderer);
            }
        } else {
            this.textRenderer.drawWithShadow(ms,
                new LiteralText("No drop data available for this pack."),
                x, y, LppUi.C_DIM);
        }

        // Item count from resource index
        int itemCount = AddonResourceIndex.scanItemModels(pack).size();
        LppUi.drawInfoRow(ms, x, y, "Items", String.valueOf(itemCount), maxW, this.textRenderer);
    }

    // ── Action / button helpers ────────────────────────────────────────────

    private void handleAction() {
        switch (activeTab) {
            case TAB_ITEMS -> openItemDetail();
            case TAB_DROPS -> {
                List<LuckyDropLine> drops = data != null ? data.parsedDrops() : List.of();
                this.client.setScreen(new ReLootPlusPlusDropLinesScreen(this, pack.id(), drops));
            }
        }
    }

    private void openItemDetail() {
        if (selectedItem == null) return;
        this.client.setScreen(new ReLootPlusPlusItemDetailScreen(this, pack, selectedItem));
    }

    private List<String> tabLabels() {
        int drops  = data != null ? data.parsedDrops().size() : 0;
        int items  = AddonResourceIndex.scanItemModels(pack).size();
        int structs = data != null ? data.structureEntries().size() : 0;
        return List.of(
            "Overview",
            "Items (" + items + ")",
            "Drops (" + drops + ")",
            "Structures (" + structs + ")"
        );
    }

    private Text statusText() {
        return switch (activeTab) {
            case TAB_ITEMS      -> new LiteralText("Select item for details");
            case TAB_DROPS      -> new LiteralText("Click row for details · dbl-click for raw");
            case TAB_STRUCTURES -> new LiteralText("Structure placement data");
            default             -> null;
        };
    }

    // ── Shared list widget ────────────────────────────────────────────────

    @SuppressWarnings("rawtypes")
    private final class GenericListWidget
            extends AlwaysSelectedEntryListWidget {

        @SuppressWarnings("unchecked")
        GenericListWidget(MinecraftClient client, int width, int height, int top, int bottom, int itemH) {
            super(client, width, height, top, bottom, itemH);
        }

        @Override protected int getScrollbarPositionX() { return this.width - 6; }

        @SuppressWarnings("unchecked")
        void addEntryRaw(AlwaysSelectedEntryListWidget.Entry<?> entry) {
            super.addEntry(entry);
        }

        void clearAll() { super.clearEntries(); }

        @SuppressWarnings("unchecked")
        void selectRaw(AlwaysSelectedEntryListWidget.Entry<?> entry) {
            super.setSelected(entry);
        }
    }

    // ── Row types ─────────────────────────────────────────────────────────

    private final class ItemRow
            extends AlwaysSelectedEntryListWidget.Entry<ItemRow> {
        private final AddonResourceIndex.ItemModelInfo info;
        ItemRow(AddonResourceIndex.ItemModelInfo info) { this.info = info; }

        @Override
        public void render(MatrixStack ms, int idx, int y, int x, int eW, int eH,
                           int mX, int mY, boolean hov, float dt) {
            if (hov) LppUi.fillRect(ms, x, y, x + eW, y + eH, LppUi.HOVER_BG);
            else if (idx % 2 == 0) LppUi.fillRect(ms, x, y, x + eW, y + eH, LppUi.STRIPE);

            LppUi.stripe(ms, x, y, eH, LppUi.kindColor("item"));

            // 16x16 item icon
            ItemStack stack = resolveStack(info.itemId());
            if (!stack.isEmpty()) {
                itemRenderer.renderInGui(stack, x + 6, y + (eH - 16) / 2);
            } else {
                LppUi.fillRect(ms, x + 6, y + 3, x + 22, y + eH - 3, 0x22FFFFFF);
                textRenderer.drawWithShadow(ms, new LiteralText("?"), x + 11, y + (eH - 8) / 2, LppUi.C_DIM);
            }

            textRenderer.drawWithShadow(ms, new LiteralText(info.itemId()),
                x + 26, y + 3, LppUi.C_BODY);
            if (info.texture() != null) {
                textRenderer.drawWithShadow(ms,
                    new LiteralText(LppUi.clip(info.texture(), eW - 32, textRenderer)),
                    x + 26, y + 12, LppUi.C_DIM);
            }
        }

        @Override public Text getNarration() { return new LiteralText(info.itemId()); }

        @Override public boolean mouseClicked(double mx, double my, int btn) {
            listWidget.selectRaw(this);
            selectedItem          = info;
            detailButton.active   = true;
            actionButton.active   = true;
            return true;
        }
    }

    private final class DropRow
            extends AlwaysSelectedEntryListWidget.Entry<DropRow> {
        private final int           idx;
        private final LuckyDropLine drop;
        private long                lastClick;

        DropRow(int idx, LuckyDropLine drop) { this.idx = idx; this.drop = drop; }

        @Override
        public void render(MatrixStack ms, int entryIdx, int y, int x, int eW, int eH,
                           int mX, int mY, boolean hov, float dt) {
            if (hov) LppUi.fillRect(ms, x, y, x + eW, y + eH, LppUi.HOVER_BG);
            else if (entryIdx % 2 == 0) LppUi.fillRect(ms, x, y, x + eW, y + eH, LppUi.STRIPE);

            String type  = drop.isGroup() ? "group" : (drop.type() != null ? drop.type() : "?");
            int    color = LppUi.typeColor(type);
            LppUi.stripe(ms, x, y, eH, color);

            int bW = LppUi.drawBadge(ms, x + 6, y + (eH - 13) / 2, 13,
                type.substring(0, Math.min(4, type.length())).toUpperCase(Locale.ROOT),
                color, textRenderer);

            String label = buildDropLabel(drop);
            int    lx    = x + 6 + bW + 4;
            textRenderer.drawWithShadow(ms, new LiteralText("#" + (idx + 1)), lx, y + (eH - 8) / 2, LppUi.C_DIM);
            lx += textRenderer.getWidth("#" + (idx + 1)) + 4;

            int    maxLW = eW - (lx - x) - 80;
            textRenderer.drawWithShadow(ms,
                new LiteralText(LppUi.clip(label, maxLW, textRenderer)),
                lx, y + (eH - 8) / 2, LppUi.C_BODY);

            int    lw = drop.luckWeight();
            String r  = (lw >= 0 ? "+" : "") + lw;
            if (drop.chance() < 1f) r += "  " + String.format("%.0f%%", drop.chance() * 100f);
            textRenderer.drawWithShadow(ms, new LiteralText(r),
                x + eW - textRenderer.getWidth(r) - 8, y + (eH - 8) / 2, LppUi.C_NUM);
        }

        private static String buildDropLabel(LuckyDropLine d) {
            if (d.isGroup()) return "group (" + (d.groupEntries() != null ? d.groupEntries().size() : 0) + ")";
            String id = d.rawId();
            if (id != null && !id.isBlank()) return id;
            String cmd = d.getString("command");
            if (cmd != null && !cmd.isBlank()) return "/" + (cmd.length() > 40 ? cmd.substring(0,40)+"…" : cmd);
            return "(no id)";
        }

        @Override public Text getNarration() { return new LiteralText(drop.isGroup() ? "group" : (drop.rawId() != null ? drop.rawId() : "")); }

        @Override public boolean mouseClicked(double mx, double my, int btn) {
            listWidget.selectRaw(this);
            long now = System.currentTimeMillis();
            if (now - lastClick < 400) {
                String raw = drop.toDisplayString();
                if (!raw.isBlank()) {
                    client.setScreen(new ReLootPlusPlusRawLineScreen(ReLootPlusPlusPackDetailScreen.this, raw));
                }
            }
            lastClick = now;
            return true;
        }
    }

    private final class StructureRow
            extends AlwaysSelectedEntryListWidget.Entry<StructureRow> {
        private final LuckyStructureEntry entry;
        StructureRow(LuckyStructureEntry entry) { this.entry = entry; }

        @Override
        public void render(MatrixStack ms, int idx, int y, int x, int eW, int eH,
                           int mX, int mY, boolean hov, float dt) {
            if (hov) LppUi.fillRect(ms, x, y, x + eW, y + eH, LppUi.HOVER_BG);
            else if (idx % 2 == 0) LppUi.fillRect(ms, x, y, x + eW, y + eH, LppUi.STRIPE);

            LppUi.stripe(ms, x, y, eH, LppUi.typeColor("structure"));

            textRenderer.drawWithShadow(ms, new LiteralText("#" + entry.id()), x + 8, y + 3, LppUi.C_TITLE);
            String file = LppUi.clip(entry.file(), eW - 90, textRenderer);
            textRenderer.drawWithShadow(ms, new LiteralText(file), x + 8, y + 12, LppUi.C_DIM);

            String center = "center: " + entry.centerX() + "," + entry.centerY() + "," + entry.centerZ();
            int cw = textRenderer.getWidth(center);
            textRenderer.drawWithShadow(ms, new LiteralText(center),
                x + eW - cw - 8, y + (eH - 8) / 2, LppUi.C_SUB);
        }

        @Override public Text getNarration() { return new LiteralText(entry.id() + " " + entry.file()); }
        @Override public boolean mouseClicked(double mx, double my, int btn) {
            listWidget.selectRaw(this);
            return true;
        }
    }

    // ── Static helpers ────────────────────────────────────────────────────

    private static ItemStack resolveStack(String id) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) return ItemStack.EMPTY;
        Item item = Registry.ITEM.get(parsed);
        return (item == null || item == Items.AIR) ? ItemStack.EMPTY : new ItemStack(item);
    }
}
