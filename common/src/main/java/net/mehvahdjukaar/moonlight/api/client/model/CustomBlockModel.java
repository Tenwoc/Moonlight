package net.mehvahdjukaar.moonlight.api.client.model;

import net.mehvahdjukaar.moonlight.api.platform.ClientHelper;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A baked block model that builds its geometry from the world, the block entity data or both.
 * Wraps NeoForge's and Fabric's level aware BlockStateModel extensions.
 * <p>
 * Quads are emitted once, not once per render type: since 1.21.5 the chunk layer is a property of
 * each quad (derived from its sprite, see QuadEmitter.forceTranslucent), so a model that
 * mixes cutout and translucent geometry just emits all of it.
 */
public interface CustomBlockModel {

    /**
     * Called on the chunk meshing threads with a snapshot of the world, so the level and block
     * entities must not be mutated. Everything the geometry depends on should come from
     * data, which is the thread safe copy the block entity handed out.
     * <p>
     * Level, pos and state are null when the model is baked outside of a world, for a block item.
     */
    void emitQuads(QuadEmitter emitter, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos,
                   @Nullable BlockState state, RandomSource random, ExtraModelData data);

    TextureAtlasSprite getParticle(ExtraModelData data);

    /**
     * Everything that influences the geometry, bundled into one value with sane equals/hashCode.
     * Both loaders use it to reuse the mesh of an identical block elsewhere, so returning something
     * cheap here is a real win. Null (the default) opts out of the caching.
     */
    @Nullable
    default Object geometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
                               ExtraModelData data) {
        return null;
    }

    /**
     * Bakes another model down to plain quads, for when they have to be tweaked one by one before
     * being emitted. Pass them straight through QuadEmitter.emitAll instead when nothing is changed.
     */
    static List<BakedQuad> collectQuads(BlockStateModel model, @Nullable BlockAndTintGetter level,
                                        @Nullable BlockPos pos, @Nullable BlockState state, RandomSource random) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        ClientHelper.collectModelParts(model, level, pos, state, random, parts);
        List<BakedQuad> quads = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            for (Direction dir : Direction.values()) quads.addAll(part.getQuads(dir));
            quads.addAll(part.getQuads(null));
        }
        return quads;
    }
}
