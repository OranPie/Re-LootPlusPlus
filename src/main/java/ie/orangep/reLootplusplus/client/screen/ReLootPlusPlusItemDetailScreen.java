package ie.orangep.reLootplusplus.client.screen;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.legacy.nbt.LenientNbtParser;
import ie.orangep.reLootplusplus.pack.AddonPack;
import ie.orangep.reLootplusplus.resourcepack.AddonConfigIndex;
import ie.orangep.reLootplusplus.resourcepack.AddonResourceIndex;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Item reference detail screen.
 *
 * <p>Layout:
 * <ul>
 *   <li>Standard navy header (pack id subtitle)</li>
 *   <li>Item card (CARD_H px): 32×32 icon + 2-col info fields</li>
 *   <li>Reference count label</li>
 *   <li>Scrollable ref list (fills space)</li>
 *   <li>Analysis panel (ANALYSIS_H px, shown when ref selected)</li>
 *   <li>Standard footer with button bar</li>
 * </ul>
 */
public final class ReLootPlusPlusItemDetailScreen extends Screen {

    private static final int CARD_H     = 72;
    private static final int ANALYSIS_H = 64;
    private static final int LABEL_H    = 14; // ref count label height

    private final Screen parent;
    private final AddonPack pack;
    private final AddonResourceIndex.ItemModelInfo info;
    private final List<AddonConfigIndex.ItemRef> allRefs;

    private ReferenceListWidget refList;
    private AddonConfigIndex.ItemRef selectedRef;
    private ItemStack mainStack;
    private ItemStack selectedStack;
    private String    selectedNbtText;
    private int       sortMode;
    private ButtonWidget sortButton;
    private ButtonWidget copyButton;
    private ButtonWidget rawButton;

    public ReLootPlusPlusItemDetailScreen(Screen parent, AddonPack pack,
                                          AddonResourceIndex.ItemModelInfo info) {
        super(new TranslatableText("menu.relootplusplus.item_detail.title", info.itemId()));
        this.parent  = parent;
        this.pack    = pack;
        this.info    = info;
        this.allRefs = AddonConfigIndex.findItemRefs(pack, info.itemId(), 200);
        this.mainStack = resolveStack(info.itemId(), null, null);
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    protected void init() {
        int listTop    = LppUi.HDR_H + CARD_H + LABEL_H + 4;
        int listBottom = this.height - LppUi.FTR_H - (selectedRef != null ? ANALYSIS_H : 0);

        refList = new ReferenceListWidget(this.client, this.width, this.height,
            listTop, listBottom, LppUi.ROW_H + 2);
        refList.setEntries(applySort(allRefs));
        this.addSelectableChild(refList);

        // Back button
        this.addDrawableChild(new ButtonWidget(8, this.height - LppUi.FTR_H + 6, 60, 20,
            new TranslatableText("menu.relootplusplus.back"),
            b -> this.client.setScreen(parent)));

        // Sort button
        sortButton = new ButtonWidget(76, this.height - LppUi.FTR_H + 6, 80, 20,
            new TranslatableText(sortKey(sortMode)), b -> cycleSort());
        this.addDrawableChild(sortButton);

        // Copy ID button
        copyButton = new ButtonWidget(162, this.height - LppUi.FTR_H + 6, 70, 20,
            new TranslatableText("menu.relootplusplus.copy"), b -> copyId());
        this.addDrawableChild(copyButton);

        // Raw Full button
        rawButton = new ButtonWidget(238, this.height - LppUi.FTR_H + 6, 70, 20,
            new TranslatableText("menu.relootplusplus.raw_full"), b -> openRaw());
        rawButton.active = false;
        this.addDrawableChild(rawButton);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
    }

    // ── Render ─────────────────────────────────────────────────────────────

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float delta) {
        this.renderBackground(ms);

        // Header
        Text title    = new LiteralText(info.itemId());
        Text subtitle = new LiteralText("Pack: " + pack.id());
        LppUi.drawHeader(ms, this.width, title, subtitle, this.textRenderer);

        // Item card
        renderItemCard(ms, mouseX, mouseY);

        // Ref count label
        int labelY = LppUi.HDR_H + CARD_H + 2;
        this.textRenderer.drawWithShadow(ms,
            new TranslatableText("menu.relootplusplus.ref_count", allRefs.size()),
            12f, labelY, LppUi.C_DIM);
        LppUi.sepH(ms, labelY + LABEL_H, this.width);

        // Ref list
        refList.render(ms, mouseX, mouseY, delta);

        // Analysis panel (shown when ref selected)
        if (selectedRef != null) {
            renderAnalysisPanel(ms);
        }

        // Footer
        LppUi.drawFooter(ms, this.width, this.height,
            new LiteralText(allRefs.size() + " refs  ·  ↑↓ navigate"),
            this.textRenderer);

        super.render(ms, mouseX, mouseY, delta);
    }

    // ── Item card ──────────────────────────────────────────────────────────

    private void renderItemCard(MatrixStack ms, int mouseX, int mouseY) {
        int cardY = LppUi.HDR_H;
        LppUi.fillRect(ms, 0, cardY, this.width, cardY + CARD_H, 0xFF0D1118);
        LppUi.sepH(ms, cardY + CARD_H - 1, this.width);

        // 32×32 icon (2× scaled) at left
        int iconX = 12;
        int iconY = cardY + (CARD_H - 32) / 2;
        if (!mainStack.isEmpty()) {
            ms.push();
            ms.translate(iconX, iconY, 0);
            ms.scale(2f, 2f, 1f);
            this.itemRenderer.renderInGui(mainStack, 0, 0);
            this.itemRenderer.renderGuiItemOverlay(this.textRenderer, mainStack, 0, 0);
            ms.pop();
            if (mouseX >= iconX && mouseX < iconX + 32 && mouseY >= iconY && mouseY < iconY + 32) {
                this.renderTooltip(ms, mainStack, mouseX, mouseY);
            }
        } else {
            LppUi.fillRect(ms, iconX, iconY, iconX + 32, iconY + 32, 0x22FFFFFF);
            this.textRenderer.drawWithShadow(ms, new LiteralText("?"), iconX + 12, iconY + 12, LppUi.C_DIM);
        }

        // 2-col info fields
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("ID", info.itemId());
        if (info.modelPath() != null) fields.put("Model", info.modelPath());
        if (info.texture() != null)   fields.put("Texture", info.texture());

        Identifier parsed = Identifier.tryParse(info.itemId());
        boolean registered = parsed != null && Registry.ITEM.containsId(parsed);
        fields.put("Status", registered ? "§2Registered" : "§cUnregistered");

        if (registered && !mainStack.isEmpty()) {
            Item item = mainStack.getItem();
            fields.put("TranslKey", item.getTranslationKey());
            fields.put("MaxStack", String.valueOf(item.getMaxCount()));
            if (item.isDamageable()) fields.put("Durability", String.valueOf(item.getMaxDamage()));
        }

        int infoX = iconX + 36;
        int infoY = cardY + 6;
        int maxW  = this.width - infoX - 8;
        LppUi.drawAttrTable(ms, infoX, infoY, maxW, fields, 4, this.textRenderer);
    }

    // ── Analysis panel ─────────────────────────────────────────────────────

    private void renderAnalysisPanel(MatrixStack ms) {
        int panelY = this.height - LppUi.FTR_H - ANALYSIS_H;
        LppUi.sepH(ms, panelY, this.width);
        LppUi.fillRect(ms, 0, panelY + 1, this.width, this.height - LppUi.FTR_H, 0xDD0A0F18);

        String raw = selectedRef.rawLine();
        if (raw == null || raw.isBlank()) {
            this.textRenderer.drawWithShadow(ms, new LiteralText("(no raw line)"),
                12f, panelY + ANALYSIS_H * 0.5f - 4, LppUi.C_DIM);
            return;
        }

        // Header label
        int x = 12;
        int y = panelY + 5;
        this.textRenderer.drawWithShadow(ms,
            new TranslatableText("menu.relootplusplus.analysis"), x, y, LppUi.C_ATTR_K);
        y += this.textRenderer.fontHeight + 4;

        // Extract attrs for display
        Map<String, String> table = buildAnalysisTable(raw);
        int maxW = this.width - x * 2;
        int rows = (ANALYSIS_H - (this.textRenderer.fontHeight + 4 + 5) - 4)
            / (this.textRenderer.fontHeight + 3);
        LppUi.drawAttrTable(ms, x, y, maxW, table, Math.max(2, rows), this.textRenderer);
    }

    // ── Ref list widget ────────────────────────────────────────────────────

    private final class ReferenceListWidget
            extends AlwaysSelectedEntryListWidget<ReferenceListWidget.Entry> {

        ReferenceListWidget(MinecraftClient client, int width, int height,
                            int top, int bottom, int itemHeight) {
            super(client, width, height, top, bottom, itemHeight);
        }

        void setEntries(List<AddonConfigIndex.ItemRef> entries) {
            this.clearEntries();
            for (AddonConfigIndex.ItemRef ref : entries) this.addEntry(new Entry(ref));
        }

        @Override protected int getScrollbarPositionX() { return this.width - 6; }

        final class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
            private final AddonConfigIndex.ItemRef ref;

            Entry(AddonConfigIndex.ItemRef ref) { this.ref = ref; }

            @Override
            public void render(MatrixStack ms, int idx, int y, int x,
                               int eW, int eH, int mX, int mY, boolean hov, float dt) {
                if (hov) LppUi.fillRect(ms, x, y, x + eW, y + eH, LppUi.HOVER_BG);
                else if (idx % 2 == 0) LppUi.fillRect(ms, x, y, x + eW, y + eH, LppUi.STRIPE);

                String kind  = ref.kind() != null ? ref.kind() : "";
                int    color = LppUi.kindColor(kind);
                LppUi.stripe(ms, x, y, eH, color);

                // Kind badge
                String badge = kind.isEmpty() ? "?" : kind.substring(0, Math.min(3, kind.length())).toUpperCase(Locale.ROOT);
                int bW = LppUi.drawBadge(ms, x + 6, y + (eH - 13) / 2, 13, badge, color, textRenderer);

                // Source (right-aligned)
                SourceLoc loc = ref.sourceLoc();
                String    where = loc == null ? "unknown" : loc.formatShort();
                int       sw    = textRenderer.getWidth(where);
                int       srcX  = x + eW - sw - 8;
                textRenderer.drawWithShadow(ms, new LiteralText(where), srcX, y + 3, LppUi.C_DIM);

                // Row 1: raw line (clipped)
                int    textX  = x + 8 + bW + 4;
                int    maxLW  = srcX - textX - 8;
                String raw    = ref.rawLine() != null ? ref.rawLine() : "";
                textRenderer.drawWithShadow(ms,
                    new LiteralText(LppUi.clip(raw, maxLW, textRenderer)),
                    textX, y + 3, LppUi.C_BODY);

                // Row 2: full raw (dimmed)
                textRenderer.drawWithShadow(ms,
                    new LiteralText(LppUi.clip(raw, eW - 16, textRenderer)),
                    x + 10, y + 13, LppUi.C_SUB);
            }

            @Override public Text getNarration() { return new LiteralText(ref.kind() + " " + ref.rawLine()); }

            @Override
            public boolean mouseClicked(double mx, double my, int btn) {
                setSelected(this);
                selectedRef      = ref;
                selectedStack    = buildStackForRef(ref);
                selectedNbtText  = extractNbtSummary(ref);
                rawButton.active = ref.rawLine() != null && !ref.rawLine().isBlank();
                return true;
            }
        }
    }

    // ── Button actions ─────────────────────────────────────────────────────

    private void cycleSort() {
        sortMode = (sortMode + 1) % 4;
        sortButton.setMessage(new TranslatableText(sortKey(sortMode)));
        refList.setEntries(applySort(allRefs));
    }

    private void copyId() {
        if (this.client != null) this.client.keyboard.setClipboard(info.itemId());
    }

    private void openRaw() {
        if (selectedRef == null) return;
        String raw = selectedRef.rawLine();
        if (raw != null && !raw.isBlank())
            this.client.setScreen(new ReLootPlusPlusRawLineScreen(this, raw));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private List<AddonConfigIndex.ItemRef> applySort(List<AddonConfigIndex.ItemRef> src) {
        List<AddonConfigIndex.ItemRef> out = new ArrayList<>(src);
        switch (sortMode) {
            case 1 -> out.sort(java.util.Comparator.comparing(AddonConfigIndex.ItemRef::kind));
            case 2 -> out.sort(java.util.Comparator.comparing(
                ref -> ref.sourceLoc() == null ? "" : ref.sourceLoc().formatShort()));
            case 3 -> out.sort(java.util.Comparator.comparing(
                AddonConfigIndex.ItemRef::rawLine, String::compareToIgnoreCase));
            default -> { /* no sort */ }
        }
        return out;
    }

    private static String sortKey(int mode) {
        return switch (mode) {
            case 1  -> "menu.relootplusplus.sort_kind";
            case 2  -> "menu.relootplusplus.sort_source";
            case 3  -> "menu.relootplusplus.sort_raw";
            default -> "menu.relootplusplus.sort_none";
        };
    }

    private static Map<String, String> buildAnalysisTable(String raw) {
        Map<String, String> table = new LinkedHashMap<>();
        String[] keys = {"kind", "type", "ID", "id", "effect", "amount", "damage",
                         "luck", "pos", "posOffset", "size", "power", "range", "delay", "NBTTag"};
        for (String k : keys) {
            String v = extractToken(raw, k);
            if (v != null && !v.isBlank() && table.size() < 16) {
                table.put(k, v);
            }
        }
        return table;
    }

    private static String extractToken(String raw, String key) {
        if (raw == null || key == null) return null;
        String needle = key + "=";
        int idx = raw.indexOf(needle);
        if (idx < 0) return null;
        int start = idx + needle.length();
        int end   = raw.length();
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ',' || c == ';' || c == ')') { end = i; break; }
        }
        if (end <= start) return null;
        return raw.substring(start, end).trim();
    }

    private static ItemStack resolveStack(String id, AddonConfigIndex.ItemRef ref,
                                          @SuppressWarnings("unused") Object ignored) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) return ItemStack.EMPTY;
        Item item = Registry.ITEM.get(parsed);
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        if (ref != null) {
            String raw = ref.rawLine();
            String nbtRaw = extractNbtPayload(raw);
            if (nbtRaw != null) {
                LegacyWarnReporter reporter = new LegacyWarnReporter();
                reporter.setLogToConsole(false);
                NbtCompound tag = LenientNbtParser.parseOrNull(nbtRaw, reporter,
                    ref.sourceLoc(), "LegacyNBT");
                if (tag != null && !tag.isEmpty()) stack.setNbt(tag);
            }
        }
        return stack;
    }

    private static ItemStack buildStackForRef(AddonConfigIndex.ItemRef ref) {
        if (ref == null) return ItemStack.EMPTY;
        String id = extractToken(ref.rawLine(), "ID");
        if (id == null || id.isBlank()) return ItemStack.EMPTY;
        return resolveStack(id, ref, null);
    }

    private static String extractNbtSummary(AddonConfigIndex.ItemRef ref) {
        if (ref == null) return null;
        String nbt = extractNbtPayload(ref.rawLine());
        if (nbt == null) return null;
        return nbt.length() > 120 ? nbt.substring(0, 117) + "…" : nbt;
    }

    private static String extractNbtPayload(String raw) {
        if (raw == null) return null;
        int idx = raw.indexOf("NBTTag=");
        if (idx < 0) return null;
        int start = idx + 7;
        if (start >= raw.length()) return null;
        char first = raw.charAt(start);
        if (first == '(' || first == '{' || first == '[') {
            int end = findMatching(raw, start);
            if (end > start) return normalizeNbt(raw.substring(start, end + 1).trim());
        }
        int end = raw.indexOf(';', start);
        if (end < 0) end = raw.indexOf(',', start);
        if (end < 0) end = raw.length();
        return normalizeNbt(raw.substring(start, end).trim());
    }

    private static int findMatching(String raw, int start) {
        char open  = raw.charAt(start);
        char close = switch (open) { case '(' -> ')'; case '{' -> '}'; case '[' -> ']'; default -> 0; };
        if (close == 0) return -1;
        int depth = 0;
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == open)  depth++;
            if (c == close) { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private static String normalizeNbt(String payload) {
        if (payload == null || payload.isBlank()) return null;
        String t = payload.trim();
        if (t.startsWith("(") && t.endsWith(")")) return "{" + t.substring(1, t.length() - 1) + "}";
        return t;
    }
}
