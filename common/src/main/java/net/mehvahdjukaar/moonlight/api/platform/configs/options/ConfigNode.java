package net.mehvahdjukaar.moonlight.api.platform.configs.options;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Loader independent description of a single row in a config screen. Both config holders translate their internal
 * representation into a tree of these, so the screen stays platform agnostic. A node is either a
 * ConfigCategory (navigable, opens a sub screen) or a ConfigOption (an editable leaf value).
 */
public abstract class ConfigNode {

    private final Component title;
    @Nullable
    private Component description;
    @Nullable
    private ConfigCategory parent;
    @Nullable
    private Identifier icon;

    protected ConfigNode(Component title, @Nullable Component description) {
        this.title = title;
        this.description = description;
    }

    @ApiStatus.Internal
    public void setParent(ConfigCategory parent) {
        this.parent = parent;
    }

    @Nullable
    public ConfigCategory parent() {
        return parent;
    }

    @ApiStatus.Internal
    public void setDescription(@Nullable Component description) {
        this.description = description;
    }

    @ApiStatus.Internal
    public void setIcon(@Nullable Identifier icon) {
        this.icon = icon;
    }

    @Nullable
    public Identifier icon() {
        return icon;
    }

    public Component title() {
        return title;
    }

    @Nullable
    public Component description() {
        return description;
    }
}
