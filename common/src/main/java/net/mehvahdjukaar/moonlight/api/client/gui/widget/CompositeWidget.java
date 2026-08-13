package net.mehvahdjukaar.moonlight.api.client.gui.widget;

import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

/**
 * Shared base for the multi-widget config controls (color field, range, vec3, etc).
 * <p>
 * Focus <em>between</em> the inner widgets is vanilla's job. The one gap is the boolean overload:
 * AbstractContainerWidget.setFocused(boolean) is a no-op, so when the row list clears the old row
 * through it a nested net.minecraft.client.gui.components.EditBox would keep its caret. Mirroring
 * the focus onto the focused child (as a leaf widget would) closes that.
 */
public abstract class CompositeWidget extends AbstractContainerWidget {

    protected CompositeWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message, AbstractScrollArea.defaultSettings(0));
    }

    // these controls lay their children out on one row and never scroll
    @Override
    protected int contentHeight() {
        return this.getHeight();
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        GuiEventListener child = this.getFocused();
        if (child != null) child.setFocused(focused);
    }
}
