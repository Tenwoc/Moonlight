package net.mehvahdjukaar.moonlight.api.client.model;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Holds the quads a CustomBlockModel emitted, bucketed by cull face
 * (index 6 is the never culled bucket).
 */
public final class CollectedModelPart implements BlockStateModelPart {

    private static final int NO_CULL = 6;

    private final List<BakedQuad>[] byCullFace;
    private final Material.Baked particle;
    private int materialFlags = 0;
    private boolean ambientOcclusion = true;

    @SuppressWarnings("unchecked")
    public CollectedModelPart(Material.Baked particle) {
        this.particle = particle;
        this.byCullFace = new List[NO_CULL + 1];
        for (int i = 0; i <= NO_CULL; i++) this.byCullFace[i] = new ObjectArrayList<>();
    }

    public List<BakedQuad> bucket(@Nullable Direction cullFace) {
        return this.byCullFace[cullFace == null ? NO_CULL : cullFace.ordinal()];
    }

    /**
     * An emitter that fills this part with plain vanilla quads. NeoForge uses a richer one that can
     * also carry per vertex colors.
     */
    public BakedQuadEmitter emitter() {
        return new BakedQuadEmitter(this::bucket) {
            @Override
            protected void pushQuad() {
                // vanilla only has a per part flag, so one quad opting out turns it off for all
                CollectedModelPart.this.ambientOcclusion &= this.ambientOcclusion;
                super.pushQuad();
            }
        };
    }

    /**
     * Material flags are read once the model is done emitting, so they have to be folded in here.
     */
    public void computeFlags() {
        int flags = 0;
        for (List<BakedQuad> bucket : this.byCullFace) {
            for (BakedQuad q : bucket) flags |= q.materialInfo().flags();
        }
        this.materialFlags = flags;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable Direction direction) {
        return this.bucket(direction);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return this.ambientOcclusion;
    }

    @Override
    public Material.Baked particleMaterial() {
        return this.particle;
    }

    @Override
    public int materialFlags() {
        return this.materialFlags;
    }
}
