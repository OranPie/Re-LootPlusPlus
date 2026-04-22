package ie.orangep.reLootplusplus.client.screen;

import ie.orangep.reLootplusplus.config.AddonDisableStore;
import ie.orangep.reLootplusplus.config.ReLootPlusPlusConfig;
import ie.orangep.reLootplusplus.lucky.loader.LuckyAddonData;
import ie.orangep.reLootplusplus.lucky.loader.LuckyAddonLoader;
import ie.orangep.reLootplusplus.pack.AddonPack;
import ie.orangep.reLootplusplus.pack.PackDiscovery;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

/**
 * Main pack-manager screen — lists all discovered addon packs with enable/disable,
 * detail navigation, and a live search filter.
 */
public final class ReLootPlusPlusMenuScreen extends Screen {

    private static final int SEARCH_BAR_H = 24;
    private static final int LIST_TOP  = LppUi.HDR_H + SEARCH_BAR_H;
    private static final int LIST_BTM_OFF = LppUi.FTR_H;

    private final Screen parent;
    private List<AddonPackEntry> allEntries;
    private List<AddonPackEntry> filteredEntries;
    private AddonPackListWidget listWidget;
    private TextFieldWidget     searchField;
    private ButtonWidget toggleButton;
    private ButtonWidget detailButton;
    private ButtonWidget dropsButton;
    private ButtonWidget reloadButton;

    // Aggregate stats shown in header
    private int totalDrops;
    private int totalPacks;

    public ReLootPlusPlusMenuScreen(Screen parent) {
        super(new TranslatableText("menu.relootplusplus.manager.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        allEntries      = loadPacks();
        filteredEntries = new ArrayList<>(allEntries);
        computeStats();

        // List widget
        listWidget = new AddonPackListWidget(this.client,
            this.width, this.height, LIST_TOP, this.height - LIST_BTM_OFF, 32);
        listWidget.setEntries(filteredEntries);
        this.addSelectableChild(listWidget);

        // Search field
        searchField = new TextFieldWidget(this.textRenderer,
            this.width / 2 - 120, LppUi.HDR_H + 3, 240, 16,
            new TranslatableText("menu.relootplusplus.search"));
        searchField.setMaxLength(64);
        searchField.setSuggestion("Search packs…");
        searchField.setChangedListener(s -> applyFilter());
        this.addDrawableChild(searchField);

        // Footer buttons  ─ 4 buttons spaced evenly
        int btnW = 96;
        int gap  = 6;
        int btnY = this.height - LppUi.FTR_H + 6;
        int totalBtnW = btnW * 4 + gap * 3;
        int btnX = (this.width - totalBtnW) / 2;

        toggleButton = new ButtonWidget(btnX,                   btnY, btnW, 20,
            new TranslatableText("menu.relootplusplus.toggle"), b -> toggleSelected());
        detailButton = new ButtonWidget(btnX + (btnW + gap),    btnY, btnW, 20,
            new TranslatableText("menu.relootplusplus.details"), b -> openDetail());
        dropsButton  = new ButtonWidget(btnX + (btnW + gap) * 2, btnY, btnW, 20,
            new TranslatableText("menu.relootplusplus.drops_shortcut"), b -> openDrops());
        reloadButton = new ButtonWidget(btnX + (btnW + gap) * 3, btnY, btnW, 20,
            new TranslatableText("menu.relootplusplus.reload"), b -> reloadResources());

        this.addDrawableChild(toggleButton);
        this.addDrawableChild(detailButton);
        this.addDrawableChild(dropsButton);
        this.addDrawableChild(reloadButton);

        // Back button (top-left)
        this.addDrawableChild(new ButtonWidget(8, 8, 60, 20,
            new TranslatableText("menu.relootplusplus.back"),
            b -> this.client.setScreen(parent)));

        updateButtons();
    }

    // ── Render ─────────────────────────────────────────────────────────────────

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float delta) {
        this.renderBackground(ms);

        // Header
        long enabled = filteredEntries.stream().filter(AddonPackEntry::enabled).count();
        Text subtitle = new LiteralText(
            totalPacks + " packs  ·  " + totalDrops + " drops  ·  "
            + enabled + "/" + filteredEntries.size() + " shown");
        LppUi.drawHeader(ms, this.width, this.title, subtitle, this.textRenderer);

        // Search bar background
        LppUi.fillRect(ms, 0, LppUi.HDR_H, this.width, LIST_TOP, 0xFF111620);
        LppUi.sepH(ms, LIST_TOP - 1, this.width);

        listWidget.render(ms, mouseX, mouseY, delta);

        // Hint in footer
        Text hint = new TranslatableText("menu.relootplusplus.hint");
        int hw = this.textRenderer.getWidth(hint);
        this.textRenderer.drawWithShadow(ms, hint,
            (this.width - hw) * 0.5f, this.height - LppUi.FTR_H + 6, LppUi.C_DIM);

        LppUi.drawFooter(ms, this.width, this.height, null, this.textRenderer);

        super.render(ms, mouseX, mouseY, delta);
    }

    // ── Key handling ───────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter → open detail for selected pack
        if (keyCode == 257 /* ENTER */ && listWidget.getSelectedOrNull() != null) {
            openDetail();
            return true;
        }
        // Space → toggle selected pack
        if (keyCode == 32 /* SPACE */ && !searchField.isFocused()
                && listWidget.getSelectedOrNull() != null) {
            toggleSelected();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    private void applyFilter() {
        String q = searchField.getText().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            filteredEntries = new ArrayList<>(allEntries);
        } else {
            filteredEntries = allEntries.stream()
                .filter(e -> {
                    if (e.pack().id().toLowerCase(Locale.ROOT).contains(q)) return true;
                    Path p = e.pack().zipPath();
                    return p != null && p.toString().toLowerCase(Locale.ROOT).contains(q);
                })
                .collect(Collectors.toList());
        }
        listWidget.setEntries(filteredEntries);
        updateButtons();
    }

    private void computeStats() {
        totalPacks = allEntries.size();
        totalDrops = LuckyAddonLoader.getAddonDataList().stream()
            .mapToInt(d -> d.parsedDrops().size()).sum();
    }

    private List<AddonPackEntry> loadPacks() {
        ReLootPlusPlusConfig config = ReLootPlusPlusConfig.load();
        PackDiscovery discovery = new PackDiscovery(config);
        List<AddonPack> packs = discovery.discoverAll();
        List<AddonPackEntry> entries = new ArrayList<>();
        for (AddonPack pack : packs) {
            boolean hasAssets = hasAssets(pack.zipPath());
            int dropCount = getDropCount(pack.id());
            entries.add(new AddonPackEntry(pack, hasAssets, dropCount,
                AddonDisableStore.isEnabled(pack.id())));
        }
        return entries;
    }

    private static int getDropCount(String packId) {
        return LuckyAddonLoader.getAddonDataList().stream()
            .filter(d -> d.packId().equals(packId))
            .findFirst()
            .map(d -> d.parsedDrops().size())
            .orElse(0);
    }

    private void toggleSelected() {
        AddonPackListWidget.Entry entry = listWidget.getSelectedOrNull();
        if (entry == null) return;
        boolean enabled = !entry.data.enabled();
        AddonDisableStore.setEnabled(entry.data.pack().id(), enabled);
        entry.data.setEnabled(enabled);
        updateButtons();
    }

    private void openDetail() {
        AddonPackListWidget.Entry entry = listWidget.getSelectedOrNull();
        if (entry == null) return;
        this.client.setScreen(new ReLootPlusPlusPackDetailScreen(this, entry.data.pack()));
    }

    private void openDrops() {
        AddonPackListWidget.Entry entry = listWidget.getSelectedOrNull();
        if (entry == null) return;
        String packId = entry.data.pack().id();
        LuckyAddonData data = LuckyAddonLoader.getAddonDataList().stream()
            .filter(d -> d.packId().equals(packId)).findFirst().orElse(null);
        java.util.List<ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine> drops =
            data != null ? data.parsedDrops() : java.util.Collections.emptyList();
        this.client.setScreen(new ReLootPlusPlusDropLinesScreen(this, packId, drops));
    }

    private void reloadResources() {
        if (this.client != null) this.client.reloadResources();
    }

    private void updateButtons() {
        boolean hasSel = listWidget.getSelectedOrNull() != null;
        toggleButton.active = hasSel;
        detailButton.active = hasSel;
        dropsButton.active  = hasSel;
    }

    private static boolean hasAssets(Path zipPath) {
        if (zipPath == null || !zipPath.toString().endsWith(".zip")) return false;
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            return zip.stream().anyMatch(e -> e.getName().startsWith("assets/"));
        } catch (Exception ignored) {
            return false;
        }
    }

    // ── Data model ─────────────────────────────────────────────────────────────

    private static final class AddonPackEntry {
        private final AddonPack pack;
        private final boolean   hasAssets;
        private final int       dropCount;
        private boolean         enabled;

        AddonPackEntry(AddonPack pack, boolean hasAssets, int dropCount, boolean enabled) {
            this.pack      = pack;
            this.hasAssets = hasAssets;
            this.dropCount = dropCount;
            this.enabled   = enabled;
        }

        AddonPack pack()      { return pack;      }
        boolean   hasAssets() { return hasAssets; }
        int       dropCount() { return dropCount; }
        boolean   enabled()   { return enabled;   }
        void      setEnabled(boolean v) { this.enabled = v; }
    }

    // ── List widget ────────────────────────────────────────────────────────────

    private final class AddonPackListWidget
            extends AlwaysSelectedEntryListWidget<AddonPackListWidget.Entry> {

        AddonPackListWidget(MinecraftClient client, int width, int height,
                            int top, int bottom, int itemHeight) {
            super(client, width, height, top, bottom, itemHeight);
        }

        void setEntries(List<AddonPackEntry> entries) {
            this.clearEntries();
            for (AddonPackEntry e : entries) this.addEntry(new Entry(e));
        }

        @Override
        public Entry getSelectedOrNull() { return (Entry) super.getSelectedOrNull(); }

        @Override
        protected int getScrollbarPositionX() { return this.width - 6; }

        // ── Row ──────────────────────────────────────────────────────────────

        final class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
            private final AddonPackEntry data;
            private long lastClickTime;

            Entry(AddonPackEntry data) { this.data = data; }

            @Override
            public void render(MatrixStack ms, int index, int y, int x,
                               int entryWidth, int entryHeight,
                               int mouseX, int mouseY, boolean hovered, float tickDelta) {

                boolean enabled     = data.enabled();
                int     stripeCol   = enabled ? 0xFF44BB66 : 0xFFBB4444;
                int     statusCol   = enabled ? 0xFF66FF77 : 0xFFFF6666;
                int     dropChipCol = 0xFF5588CC;

                // Row background
                if (isSelectedEntry(this)) {
                    LppUi.fillRect(ms, x, y, x + entryWidth, y + entryHeight, LppUi.SEL_BG);
                } else if (hovered) {
                    LppUi.fillRect(ms, x, y, x + entryWidth, y + entryHeight, LppUi.HOVER_BG);
                } else if (index % 2 == 0) {
                    LppUi.fillRect(ms, x, y, x + entryWidth, y + entryHeight, LppUi.STRIPE);
                }

                // Left colored stripe
                LppUi.stripe(ms, x, y, entryHeight, stripeCol);

                // ── Row 1: pack ID + status badge (right) ────────────────────
                String packId = data.pack().id();
                textRenderer.drawWithShadow(ms, new LiteralText(packId),
                    x + 8, y + 5, 0xEEEEEE);

                // Status badge (right, row 1)
                String statusLabel = enabled
                    ? new TranslatableText("menu.relootplusplus.enabled").getString()
                    : new TranslatableText("menu.relootplusplus.disabled").getString();
                int sW  = textRenderer.getWidth(statusLabel) + 8;
                int sX  = x + entryWidth - sW - 4;
                LppUi.fillRect(ms, sX, y + 3, sX + sW, y + 14,
                    (stripeCol & 0x00FFFFFF) | 0x44000000);
                textRenderer.drawWithShadow(ms, new LiteralText(statusLabel),
                    sX + 4, y + 5, statusCol);

                // ── Row 2: source path + drop count chip + ZIP/DIR badge ─────
                Path zip  = data.pack().zipPath();
                String src = zip != null ? zip.getFileName().toString() : "";
                boolean isZip  = src.endsWith(".zip");
                int     zipCol = isZip ? 0xFF5588CC : 0xFF88AA55;
                String  typeBadge = isZip ? "ZIP" : "DIR";
                int     typeBW = textRenderer.getWidth(typeBadge) + 6;

                // Drop count chip
                String dropLabel = "✦ " + data.dropCount();
                int    dcW = textRenderer.getWidth(dropLabel) + 8;
                int    dcX = x + entryWidth - dcW - 4;
                LppUi.fillRect(ms, dcX, y + 17, dcX + dcW, y + 28,
                    (dropChipCol & 0x00FFFFFF) | 0x44000000);
                textRenderer.drawWithShadow(ms, new LiteralText(dropLabel),
                    dcX + 4, y + 19, dropChipCol);

                // ZIP/DIR badge (left of drop chip)
                int typeBX = dcX - typeBW - 4;
                LppUi.fillRect(ms, typeBX, y + 17, typeBX + typeBW, y + 28,
                    (zipCol & 0x00FFFFFF) | 0x33000000);
                textRenderer.drawWithShadow(ms, new LiteralText(typeBadge),
                    typeBX + 3, y + 19, zipCol);

                // Source path (clipped to available width)
                int maxSrcW = typeBX - (x + 8) - 4;
                if (maxSrcW > 8) {
                    textRenderer.drawWithShadow(ms,
                        new LiteralText(LppUi.clip(src, maxSrcW, textRenderer)),
                        x + 8, y + 19, LppUi.C_DIM);
                }

                // No-assets note
                if (!data.hasAssets()) {
                    String na = new TranslatableText("menu.relootplusplus.no_assets").getString();
                    int naW = textRenderer.getWidth(na) + 6;
                    int naX = sX - naW - 4;
                    if (naX > x + 8 + textRenderer.getWidth(packId) + 4) {
                        LppUi.fillRect(ms, naX, y + 3, naX + naW, y + 14, 0x33888888);
                        textRenderer.drawWithShadow(ms, new LiteralText(na), naX + 3, y + 5, 0x888888);
                    }
                }
            }

            private boolean isSelectedEntry(Entry e) {
                return AddonPackListWidget.this.getSelectedOrNull() == e;
            }

            @Override
            public boolean mouseClicked(double mx, double my, int button) {
                setSelected(this);
                updateButtons();
                long now = System.currentTimeMillis();
                if (now - lastClickTime < 400) {
                    openDetail();
                }
                lastClickTime = now;
                return true;
            }

            @Override
            public Text getNarration() {
                return new LiteralText(data.pack().id());
            }
        }
    }
}
