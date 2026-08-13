package net.mehvahdjukaar.moonlight.core.misc;

import net.mehvahdjukaar.moonlight.api.map.client.MLDecorationRenderState;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

// Custom decorations extracted alongside the vanilla ones. Vanilla's own decoration state is a fixed struct with no room
// for our tints or outlines, so we carry our own list across the extract boundary
@ApiStatus.Internal
public interface IMapRenderStateExtension {

    List<MLDecorationRenderState> moonlight$getCustomDecorations();
}
