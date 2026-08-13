package net.mehvahdjukaar.moonlight.api.client.model;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.mehvahdjukaar.moonlight.api.platform.ClientHelper;

/**
 * The unbaked form of a CustomBlockModel, deserialized straight out of a
 * blockstates/ file wherever a normal model reference would go. Register the codec with
 * ClientHelper.addBlockModelRegistration.
 * <p>
 * NeoForge dispatches these on a "type" key and Fabric on "fabric:type", so a
 * blockstate that should work on both writes them both; each loader ignores the other's:
 * <pre>{@code
 * { "type": "mymod:jar", "fabric:type": "mymod:jar", "model": "mymod:block/jar", "width": 8 }
 * }</pre>
 * Nested models are a plain field of type BlockStateModel.Unbaked using
 * BlockStateModel.Unbaked.CODEC; remember to forward
 * ResolvableModel.resolveDependencies to them or their parents will never load.
 */
public interface CustomUnbakedModel extends ResolvableModel {

    CustomBlockModel bake(ModelBaker baker);

    /**
     * Must be the same codec this type was registered with.
     */
    MapCodec<? extends CustomUnbakedModel> codec();
}
