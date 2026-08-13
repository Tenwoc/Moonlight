package net.mehvahdjukaar.moonlight.api.client.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Transformation;
import net.mehvahdjukaar.moonlight.api.platform.ClientHelper;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Cross loader quad builder, the single replacement for the old BakedQuadBuilder and
 * BakedQuadsTransformer. A quad is either filled in from scratch or seeded from an existing
 * one with fromQuad, tweaked, then pushed with emit.
 * <p>
 * It exists because a vanilla BakedQuad carries no per vertex colors or normals: those live
 * in NeoForge's MutableQuad and Fabric's QuadEmitter, which this maps onto.
 * <p>
 * Everything except the vertex data (positions, uvs, normals) is sticky across emit, so
 * a sprite, tint or transform set once applies to every following quad.
 * <p>
 * UVs are sprite relative, in the 0 to 1 range, never atlas coordinates.
 */
public abstract class QuadEmitter {

    public static final int VERTICES = 4;
    private static final int WHITE = 0xFFFFFFFF;

    protected final Vector3f[] positions = new Vector3f[]{new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    protected final Vector3f[] normals = new Vector3f[]{new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    protected final float[] us = new float[VERTICES];
    protected final float[] vs = new float[VERTICES];
    protected final int[] colors = new int[]{WHITE, WHITE, WHITE, WHITE};

    protected boolean hasNormals = false;
    @Nullable
    protected TextureAtlasSprite sprite = null;
    @Nullable
    protected Direction cullFace = null;
    @Nullable
    protected Direction lightFace = null;
    @Nullable
    protected Matrix4f transform = null;
    protected int tintIndex = -1;
    protected int lightEmission = 0;
    protected boolean shade = true;
    protected boolean ambientOcclusion = true;
    protected boolean forceTranslucent = false;

    /**
     * Hands the finished quad to the platform. Vertex data has already been transformed and the
     * lighting face resolved, so implementations only have to copy the state over.
     */
    protected abstract void pushQuad();

    public final QuadEmitter emit() {
        if (this.sprite == null) {
            throw new IllegalStateException("No sprite set on the quad emitter");
        }
        if (this.transform != null) {
            for (int i = 0; i < VERTICES; i++) {
                this.transform.transformPosition(this.positions[i]);
                if (this.hasNormals) this.transform.transformDirection(this.normals[i]);
            }
        }
        if (this.lightFace == null) this.lightFace = this.computeFace();
        this.pushQuad();
        this.resetVertices();
        return this;
    }

    /**
     * Emits every quad of an already baked model part, keeping the sprite and material of each.
     * Sticky state other than the transform is not applied, the source quads carry their own.
     */
    public final QuadEmitter emitAll(Iterable<BakedQuad> quads) {
        for (BakedQuad q : quads) {
            this.fromQuad(q).emit();
        }
        return this;
    }

    /**
     * Emits another baked model wholesale, the usual way to include a nested vanilla model. Prefer
     * this over CustomBlockModel.collectQuads when the geometry is passed through unchanged:
     * on Fabric it goes down the renderer pipeline directly, so the nested model keeps anything a
     * vanilla quad cannot express.
     */
    public QuadEmitter emitAll(BlockStateModel model, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos,
                               @Nullable BlockState state, RandomSource random) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        ClientHelper.collectModelParts(model, level, pos, state, random, parts);
        for (BlockStateModelPart part : parts) this.emitAll(part);
        return this;
    }

    /**
     * Replays a previously recorded batch. This emitter's transform is applied on top of whatever
     * the batch was recorded with.
     */
    public final QuadEmitter emitAll(QuadBatch batch) {
        batch.replay(this);
        return this;
    }

    /**
     * Emits every quad of an already baked model part, keeping its cull faces. Overwrites the
     * sticky cull face with the part's own.
     */
    public final QuadEmitter emitAll(BlockStateModelPart part) {
        for (Direction dir : Direction.values()) {
            this.cullFace(dir);
            this.emitAll(part.getQuads(dir));
        }
        this.cullFace(null);
        return this.emitAll(part.getQuads(null));
    }

    // vertex data

    public final QuadEmitter pos(int vertex, float x, float y, float z) {
        this.positions[vertex].set(x, y, z);
        return this;
    }

    public final QuadEmitter pos(int vertex, Vector3fc pos) {
        this.positions[vertex].set(pos);
        return this;
    }

    /**
     * Sprite relative uv, 0 to 1 over the sprite the quad is using.
     */
    public final QuadEmitter uv(int vertex, float u, float v) {
        this.us[vertex] = u;
        this.vs[vertex] = v;
        return this;
    }

    public final QuadEmitter normal(int vertex, float x, float y, float z) {
        this.normals[vertex].set(x, y, z);
        this.hasNormals = true;
        return this;
    }

    public final QuadEmitter normal(int vertex, Vector3fc normal) {
        return this.normal(vertex, normal.x(), normal.y(), normal.z());
    }

    public final QuadEmitter color(int vertex, int argb) {
        this.colors[vertex] = argb;
        return this;
    }

    public final QuadEmitter color(int argb) {
        for (int i = 0; i < VERTICES; i++) this.colors[i] = argb;
        return this;
    }

    public final QuadEmitter color(IntUnaryOperator vertexToArgb) {
        for (int i = 0; i < VERTICES; i++) this.colors[i] = vertexToArgb.applyAsInt(i);
        return this;
    }

    // material, sticky across emit

    public final QuadEmitter sprite(TextureAtlasSprite sprite) {
        this.sprite = sprite;
        return this;
    }

    public final QuadEmitter tintIndex(int tintIndex) {
        this.tintIndex = tintIndex;
        return this;
    }

    /**
     * Minimum block light the quad is lit with, 0 to 15.
     */
    public final QuadEmitter lightEmission(int lightEmission) {
        this.lightEmission = lightEmission;
        return this;
    }

    public final QuadEmitter shade(boolean shade) {
        this.shade = shade;
        return this;
    }

    public final QuadEmitter ambientOcclusion(boolean ambientOcclusion) {
        this.ambientOcclusion = ambientOcclusion;
        return this;
    }

    /**
     * Forces the translucent chunk layer even when the sprite has no translucent pixels. Otherwise
     * the layer is derived from the sprite, there is no code side render type control anymore.
     */
    public final QuadEmitter forceTranslucent(boolean forceTranslucent) {
        this.forceTranslucent = forceTranslucent;
        return this;
    }

    /**
     * Face this quad is culled against, or null to never cull it.
     */
    public final QuadEmitter cullFace(@Nullable Direction cullFace) {
        this.cullFace = cullFace;
        return this;
    }

    /**
     * Face the quad takes its lighting from. Null (the default) derives it from the geometry.
     */
    public final QuadEmitter direction(@Nullable Direction direction) {
        this.lightFace = direction;
        return this;
    }

    /**
     * Transform applied to positions and normals of every following quad, null to clear.
     */
    public final QuadEmitter transform(@Nullable Matrix4f transform) {
        this.transform = transform;
        return this;
    }

    /**
     * Same as transform but around the block center, which is what model state
     * rotations are relative to.
     */
    public final QuadEmitter transform(@Nullable Transformation transformation) {
        if (transformation == null || Transformation.IDENTITY.equals(transformation)) {
            return this.transform((Matrix4f) null);
        }
        return this.transform(new Matrix4f()
                .translate(0.5f, 0.5f, 0.5f)
                .mul(transformation.getMatrix())
                .translate(-0.5f, -0.5f, -0.5f));
    }

    /**
     * Seeds this emitter with an existing quad. Colors are reset to white, a vanilla baked quad has
     * none. The sticky transform, cull face and ambient occlusion are left alone.
     */
    public final QuadEmitter fromQuad(BakedQuad quad) {
        BakedQuad.MaterialInfo info = quad.materialInfo();
        this.sprite = info.sprite();
        this.tintIndex = info.tintIndex();
        this.shade = info.shade();
        this.lightEmission = info.lightEmission();
        this.forceTranslucent = info.layer().translucent();
        this.lightFace = quad.direction();
        this.hasNormals = false;
        for (int i = 0; i < VERTICES; i++) {
            this.positions[i].set(quad.position(i));
            long uv = quad.packedUV(i);
            this.us[i] = unlerp(UVPair.unpackU(uv), info.sprite().getU0(), info.sprite().getU1());
            this.vs[i] = unlerp(UVPair.unpackV(uv), info.sprite().getV0(), info.sprite().getV1());
            this.colors[i] = WHITE;
        }
        return this;
    }

    /**
     * A view of this emitter as a vertex consumer, so the usual vertex writing helpers can feed it.
     * Every fourth vertex emits a quad; closing it emits the one still being written, so use it in
     * a try with resources block.
     */
    public final QuadVertexConsumer asVertexConsumer() {
        return new AsVertexConsumer();
    }

    public interface QuadVertexConsumer extends VertexConsumer, AutoCloseable {
        @Override
        void close();
    }

    /**
     * Resets everything, material included.
     */
    public final QuadEmitter clear() {
        this.resetVertices();
        this.sprite = null;
        this.cullFace = null;
        this.transform = null;
        this.tintIndex = -1;
        this.lightEmission = 0;
        this.shade = true;
        this.ambientOcclusion = true;
        this.forceTranslucent = false;
        return this;
    }

    private void resetVertices() {
        for (int i = 0; i < VERTICES; i++) {
            this.positions[i].zero();
            this.normals[i].zero();
            this.us[i] = 0;
            this.vs[i] = 0;
        }
        this.hasNormals = false;
        this.lightFace = null;
    }

    private Direction computeFace() {
        Vector3f a = new Vector3f(this.positions[2]).sub(this.positions[0]);
        Vector3f b = new Vector3f(this.positions[3]).sub(this.positions[1]);
        Vector3f normal = a.cross(b);
        return normal.lengthSquared() < 1.0E-7f ? Direction.UP
                : Direction.getApproximateNearest(normal.x, normal.y, normal.z);
    }

    private static float unlerp(float value, float min, float max) {
        float range = max - min;
        return range == 0 ? 0 : (value - min) / range;
    }

    private class AsVertexConsumer implements QuadVertexConsumer {

        // index of the vertex currently being written, -1 before the first one
        private int current = -1;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (++this.current == VERTICES) {
                QuadEmitter.this.emit();
                this.current = 0;
            }
            QuadEmitter.this.pos(this.current, x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this.setColor(ARGB.color(a, r, g, b));
        }

        @Override
        public VertexConsumer setColor(int argb) {
            QuadEmitter.this.color(this.current, argb);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            QuadEmitter.this.uv(this.current, u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            QuadEmitter.this.normal(this.current, x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }

        @Override
        public void close() {
            if (this.current == VERTICES - 1) QuadEmitter.this.emit();
            this.current = -1;
        }
    }
}
