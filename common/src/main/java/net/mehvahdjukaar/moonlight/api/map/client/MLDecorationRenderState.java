package net.mehvahdjukaar.moonlight.api.map.client;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Everything Moonlight needs to draw one custom map decoration. Filled in by a
 * MapDecorationRenderer during the extract pass, which is the last point where the
 * net.minecraft.world.level.saveddata.maps.MapItemSavedData is still around.
 */
public class MLDecorationRenderState {

    @Nullable
    public TextureAtlasSprite sprite;
    public byte x;
    public byte y;
    public byte rot;
    /**
     * ARGB, alpha included. Multiplied onto the sprite
     */
    public int color = -1;
    /**
     * Draws the sprite silhouette around itself, so the marker reads against a busy map
     */
    public boolean outline;
    public boolean renderOnFrame = true;
    @Nullable
    public Component name;
}
