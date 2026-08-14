package net.mehvahdjukaar.moonlight.core.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.mehvahdjukaar.moonlight.core.client.config.ConfigScreenLayout.ROW_WIDTH;

class ConfigRowList extends ContainerObjectSelectionList<ConfigListRow> {

    private boolean drawFooterSeparator = true;
    private int rowWidth = ROW_WIDTH;
    private int topPadding;

    ConfigRowList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
    }

    void setRows(List<ConfigListRow> rows) {
        this.clearEntries();
        if (this.topPadding > 0) this.addEntry(new SpacerRow(), this.topPadding);
        for (ConfigListRow row : rows) this.addEntry(row);
        this.refreshScrollAmount();
    }

    @Nullable
    ConfigListRow getHovered(double mouseX, double mouseY) {
        return this.getEntryAtPosition(mouseX, mouseY);
    }

    @Override
    public int getRowWidth() {
        return this.rowWidth;
    }

    // narrows the rows, for lists that live in a pane instead of the whole screen
    void setRowWidth(int rowWidth) {
        this.rowWidth = rowWidth;
    }

    @Override
    protected int scrollBarX() {
        return this.getX() + this.width / 2 + this.getRowWidth() / 2 + 6;
    }

    // blank space above the first row, which is how rows get vertically centered in a pane taller than they need.
    // it's just an empty leading row, so hit-testing and scrolling still line up. Applies on the next setRows
    void setTopPadding(int padding) {
        this.topPadding = Math.max(0, padding);
    }

    // off when the screen draws its own full-width separator instead (the split layout)
    void setDrawFooterSeparator(boolean draw) {
        this.drawFooterSeparator = draw;
    }

    @Override
    protected void extractListSeparators(GuiGraphicsExtractor graphics) {
        // the top separator is owned by the screen's header bar, so only draw the footer one
        if (!this.drawFooterSeparator) return;
        Identifier footer = this.minecraft.level == null ? Screen.FOOTER_SEPARATOR : Screen.INWORLD_FOOTER_SEPARATOR;
        graphics.blit(RenderPipelines.GUI_TEXTURED, footer, this.getX(), this.getBottom(), 0f, 0f, this.getWidth(), 2, 32, 2);
    }

    // the empty leading row behind setTopPadding
    private static class SpacerRow extends ConfigListRow {
        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }

        @Nullable
        @Override
        Component getTooltip(int mouseX, int mouseY) {
            return null;
        }
    }
}
