package net.mehvahdjukaar.moonlight.api.map.client;

import net.mehvahdjukaar.moonlight.api.map.decoration.MLMapDecoration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Describes how a custom map decoration looks. It does not draw anything itself: it fills a
 * MLDecorationRenderState during the extract pass and Moonlight submits it later.
 * <p>
 * Override the small hooks for the usual cases (a tint, an outline, a different sprite), or
 * extract itself when the decoration needs the map data, for instance to recompute its
 * position from a live entity.
 */
public class MapDecorationRenderer<T extends MLMapDecoration> {

    protected final Identifier textureId;

    public MapDecorationRenderer(Identifier texture) {
        this.textureId = texture;
    }

    /**
     * The sprite used when no decoration instance is around, like a gui button showing this type
     */
    public Identifier getDefaultSprite() {
        return this.textureId;
    }

    protected Identifier getSprite(T decoration) {
        return this.textureId;
    }

    /**
     * ARGB tint, alpha included
     */
    protected int getColor(T decoration) {
        return -1;
    }

    protected boolean hasOutline(T decoration) {
        return false;
    }

    protected boolean rendersOnFrame(T decoration) {
        return true;
    }

    /**
     * @return false to skip this decoration entirely for this frame
     */
    public boolean extract(T decoration, MapItemSavedData mapData, MLDecorationRenderState state) {
        state.sprite = MapDecorationClientManager.getSprite(this.getSprite(decoration));
        state.x = decoration.getX();
        state.y = decoration.getY();
        state.rot = decoration.getRot();
        state.color = this.getColor(decoration);
        state.outline = this.hasOutline(decoration);
        state.renderOnFrame = this.rendersOnFrame(decoration);
        state.name = decoration.getDisplayName();
        return true;
    }
}
