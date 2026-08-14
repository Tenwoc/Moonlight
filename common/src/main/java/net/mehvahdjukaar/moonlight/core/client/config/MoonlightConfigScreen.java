package net.mehvahdjukaar.moonlight.core.client.config;

import net.mehvahdjukaar.moonlight.api.client.gui.ConfigEditSession;
import net.mehvahdjukaar.moonlight.api.client.gui.GuiHelper;
import net.mehvahdjukaar.moonlight.api.client.gui.misc.ConfigGuiColors;
import net.mehvahdjukaar.moonlight.api.client.gui.widget.BreadcrumbWidget;
import net.mehvahdjukaar.moonlight.api.client.gui.widget.IconButton;
import net.minecraft.ChatFormatting;
import net.mehvahdjukaar.moonlight.api.platform.configs.ModConfigHolder;
import net.mehvahdjukaar.moonlight.api.platform.configs.options.ConfigCategory;
import net.mehvahdjukaar.moonlight.api.platform.configs.options.ConfigNode;
import net.mehvahdjukaar.moonlight.api.platform.configs.options.ConfigOption;
import net.mehvahdjukaar.moonlight.api.platform.configs.options.ConfigReloadType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static net.mehvahdjukaar.moonlight.core.client.config.ConfigScreenLayout.*;

public class MoonlightConfigScreen extends ConfigPageScreen {

    // reused so leaving repeatedly refreshes one toast instead of stacking duplicates
    private static final SystemToast.SystemToastId RELOAD_TOAST_ID = new SystemToast.SystemToastId();

    private final ConfigEditSession session;
    private final ConfigCategory category;
    @Nullable
    private final MoonlightConfigScreen parentPage; // null = root level
    @Nullable
    private final Identifier background;

    // top-bar geometry: title centered on the first line, breadcrumb + search on the second
    private static final int SIDE_MARGIN = 14;
    private static final int CRUMB_Y = 25;
    private static final int SEARCH_WIDTH = 110;
    private static final int SEARCH_HEIGHT = 14;
    private static final int SEARCH_ICON_SIZE = 12;

    private Button saveButton;
    private EditBox searchBox;
    private String searchQuery = "";

    public MoonlightConfigScreen(ModConfigHolder holder, ConfigCategory root, Screen returnScreen,
                                 @Nullable Identifier background) {
        this(root, null, new ConfigEditSession(holder, returnScreen), background);
    }

    public static Screen create(ModConfigHolder holder, ConfigCategory root, Screen returnScreen,
                                @Nullable Identifier background) {
        return new MoonlightConfigScreen(holder, root, returnScreen, background);
    }

    private MoonlightConfigScreen(ConfigCategory category, @Nullable MoonlightConfigScreen parentPage,
                                  ConfigEditSession session, @Nullable Identifier background) {
        // the header keeps the config's own name on every sub-screen; the breadcrumb is what tracks the category
        super(session.holder().getReadableName());
        this.category = category;
        this.parentPage = parentPage;
        this.session = session;
        this.background = background;
    }

    private boolean isRoot() {
        return parentPage == null;
    }

    @Override
    public ConfigEditSession session() {
        return this.session;
    }

    @Override
    public void openCategory(ConfigCategory cat) {
        this.minecraft.setScreen(new MoonlightConfigScreen(cat, this, session, background));
    }

    @Override
    public void onValueEdited() {
        refreshSave();
    }

    @Override
    protected void init() {
        this.overlay.clear(); // widgets are rebuilt here, so drop any stale open popup
        this.list = new ConfigRowList(this.minecraft, this.width, this.height - HEADER - FOOTER, HEADER, ITEM_HEIGHT);

        // search box on the right, breadcrumb trail filling the space to its left. Both are registered for input
        // here but drawn in render(), on top of the header bar
        this.searchBox = new EditBox(this.font, this.width - SIDE_MARGIN - SEARCH_WIDTH, CRUMB_Y - 3,
                SEARCH_WIDTH, SEARCH_HEIGHT, Component.translatable("gui.moonlight.config.search"));
        this.searchBox.setHint(Component.translatable("gui.moonlight.config.search")
                .withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.GRAY));
        this.searchBox.setValue(this.searchQuery);
        this.searchBox.setResponder(query -> {
            this.searchQuery = query;
            populate();
        });
        this.addRenderableWidget(this.searchBox);

        List<BreadcrumbWidget.Crumb> crumbs = new ArrayList<>();
        for (MoonlightConfigScreen s = this; s != null; s = s.parentPage) {
            Component label = s.isRoot() ? Component.literal("⌂") : s.category.title(); // home icon
            crumbs.addFirst(new BreadcrumbWidget.Crumb(label, s, s == this));
        }
        int trailRight = this.searchBox.getX() - SEARCH_ICON_SIZE - 6; // leave room for the magnifier glyph + a gap
        BreadcrumbWidget breadcrumb = new BreadcrumbWidget(SIDE_MARGIN, CRUMB_Y, trailRight - SIDE_MARGIN, this.font.lineHeight,
                this.font, crumbs, target -> {
            if (target != this) this.minecraft.setScreen(target);
        });
        this.addRenderableWidget(breadcrumb);

        populate();
        this.addRenderableWidget(this.list);

        // [Reset all] Save | Back, all the same size. The session is shared across the navigation stack so Save
        // persists sub-category edits too. Reset all only shows at the root page, since it acts on the whole config
        int y = this.height - 28;
        int bw = 100, gap = 4;
        if (isRoot()) {
            int total = 3 * bw + 2 * gap;
            int x0 = (this.width - total) / 2;
            this.addRenderableWidget(new IconButton(x0, y, bw, 20,
                    Component.translatable("gui.moonlight.config.reset_all"), RESET_ICON, 12, 12, b -> confirmResetAll()));
            this.saveButton = new IconButton(x0 + bw + gap, y, bw, 20, Component.empty(), SAVE_ICON, 12, 12, b -> doSave());
            this.addRenderableWidget(this.saveButton);
            this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                    .bounds(x0 + 2 * (bw + gap), y, bw, 20).build());
        } else {
            this.saveButton = new IconButton(this.width / 2 - 104, y, bw, 20, Component.empty(), SAVE_ICON, 12, 12, b -> doSave());
            this.addRenderableWidget(this.saveButton);
            this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                    .bounds(this.width / 2 + 4, y, bw, 20).build());
        }
        this.addRenderableWidget(new GearButton(8, y, 20,
                b -> this.minecraft.setScreen(new ModsTilesScreen(this, background))));
        refreshSave();
    }

    private void confirmResetAll() {
        this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                resetAllToDefaults(this.category);
                session.apply();        // reset writes straight through, like the per-row reset + Save
                session.clearPending();
            }
            this.minecraft.setScreen(this); // re-inits, so rows re-read the (now saved) values
        }, Component.translatable("gui.moonlight.config.reset_all.title"),
                Component.translatable("gui.moonlight.config.reset_all.message")));
    }

    // stages (does not save) the default of every editable option in cat and its sub-categories
    private void resetAllToDefaults(ConfigCategory cat) {
        for (ConfigNode e : cat.entries()) {
            if (e instanceof ConfigCategory sub) {
                resetAllToDefaults(sub);
            } else if (e instanceof ConfigOption<?> v && !(v instanceof ConfigOption.UnsupportedValue)) {
                session.put(v, v.defaultValue());
            }
        }
    }

    @Override
    protected void populate() {
        List<ConfigListRow> rows = new ArrayList<>();
        String query = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            for (ConfigNode e : category.entries()) {
                // the gate shows as a normal checkmark row here AND as the parent screen's inline toggle
                if (e instanceof ConfigCategory cat) {
                    rows.add(new CategoryRow(this, cat));
                } else if (e instanceof ConfigOption<?> v) {
                    addOption(rows, v);
                }
            }
        } else {
            // flat, recursive search across this category's whole subtree
            List<ConfigOption<?>> matches = new ArrayList<>();
            collectMatches(category, query, matches);
            for (ConfigOption<?> v : matches) {
                addOption(rows, v);
            }
        }
        this.list.setRows(rows);
    }

    private void addOption(List<ConfigListRow> rows, ConfigOption<?> v) {
        rows.add(new OptionRow(this, v));
        addDescriptionRows(rows, v);
    }

    private static void collectMatches(ConfigCategory category, String query, List<ConfigOption<?>> out) {
        for (ConfigNode e : category.entries()) {
            if (e == category.gate()) continue; // gate is edited via its category's inline toggle
            if (e instanceof ConfigCategory cat) {
                collectMatches(cat, query, out);
            } else if (e instanceof ConfigOption<?> v) {
                if (v.title().getString().toLowerCase(Locale.ROOT).contains(query)) out.add(v);
            }
        }
    }

    private void doSave() {
        session.apply();
        session.clearPending();
        this.rebuildWidgets(); // re-read saved values, reset counter and rollback buttons
    }

    private void refreshSave() {
        if (this.saveButton == null) return;
        int unsaved = session.unsavedCount();
        // the "(N)" unsaved counter is tinted amber to draw the eye when there are pending edits
        Component count = Component.literal("(" + unsaved + ")")
                .withStyle(s -> s.withColor(TextColor.fromRgb(ConfigGuiColors.MODIFIED)));
        this.saveButton.setMessage(unsaved > 0
                ? Component.translatable("gui.moonlight.config.save_count", count)
                : Component.translatable("gui.moonlight.config.save"));
        this.saveButton.active = unsaved > 0;
    }

    @Override
    public void onClose() {
        // leaving the config entirely with pending edits would silently drop them, so confirm first. Going back to a
        // parent category stays within the shared session, so nothing is lost
        if (isRoot() && session.unsavedCount() > 0) {
            this.minecraft.setScreen(new ConfirmScreen(discard -> {
                if (discard) leaveConfig();
                else this.minecraft.setScreen(this);
            },
                    Component.translatable("gui.moonlight.config.discard.title"),
                    Component.translatable("gui.moonlight.config.discard.message", session.unsavedCount()),
                    Component.translatable("gui.moonlight.config.discard.confirm"), CommonComponents.GUI_CANCEL));
            return;
        }
        if (isRoot()) leaveConfig();
        else this.minecraft.setScreen(parentPage);
    }

    // back to the screen the config was opened from, warning via toast if any saved change needs a reload
    private void leaveConfig() {
        ConfigReloadType reload = session.appliedReload();
        if (reload != ConfigReloadType.NONE) {
            Component message = Component.translatable(reload == ConfigReloadType.GAME_RESTART
                    ? "gui.moonlight.config.reload_needed.game" : "gui.moonlight.config.reload_needed.world");
            this.minecraft.getToastManager().addToast(SystemToast.multiline(this.minecraft, RELOAD_TOAST_ID,
                    Component.translatable("gui.moonlight.config.reload_needed.title"), message));
        }
        this.minecraft.setScreen(session.returnScreen());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        // header chrome sits in the background layer (drawn before the widgets by super.render): the bar, its
        // separator, the title and the search magnifier. The row list is scissored below HEADER, so rows slide
        // under the bar cleanly; the breadcrumb and search box are renderable widgets drawn on top of it.
        GuiHelper.renderHeaderBar(graphics, this.width, HEADER);
        graphics.centeredText(this.font, this.title, this.width / 2, 7, ConfigGuiColors.TITLE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SEARCH_ICON, this.searchBox.getX() - SEARCH_ICON_SIZE - 2,
                this.searchBox.getY() + (SEARCH_HEIGHT - SEARCH_ICON_SIZE) / 2, SEARCH_ICON_SIZE, SEARCH_ICON_SIZE);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        renderOverlayOrTooltip(graphics, mouseX, mouseY);
    }
}
