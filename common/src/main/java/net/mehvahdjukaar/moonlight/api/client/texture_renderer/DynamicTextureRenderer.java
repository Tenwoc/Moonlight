package net.mehvahdjukaar.moonlight.api.client.texture_renderer;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.mehvahdjukaar.moonlight.api.util.Utils;
import net.mehvahdjukaar.moonlight.core.Moonlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Cache of RenderableDynamicTextures keyed by texture id, plus helpers to draw into them.
 * Textures are dropped after a couple of minutes without use, so ask for them by id every time you
 * need one rather than holding on to the instance.
 */
public class DynamicTextureRenderer {

    //the box gui code draws in. Maps onto the whole texture whatever its actual resolution is
    private static final float GUI_BOX = 16;

    private static final Projection PROJECTION = new Projection();
    @Nullable
    private static ProjectionMatrixBuffer projectionBuffer = null;

    private static final Cache<Identifier, RenderableDynamicTexture> TEXTURE_CACHE = CacheBuilder.newBuilder()
            .removalListener((RemovalListener<Identifier, RenderableDynamicTexture>) notification -> {
                RenderableDynamicTexture texture = notification.getValue();
                //unregistering closes it
                if (texture != null) onRenderThread(texture::unregister);
            })
            .expireAfterAccess(2, TimeUnit.MINUTES)
            .build();

    //clears the texture cache and forces all to be re-rendered
    public static void clearCache() {
        TEXTURE_CACHE.invalidateAll();
    }

    private static void onRenderThread(Runnable task) {
        if (RenderSystem.isOnRenderThread()) task.run();
        else Minecraft.getInstance().execute(task);
    }

    /**
     * Gets a texture you can render onto, creating and drawing it the first time it's asked for.
     * For practical purposes you are only interested in something like
     * buffer.getBuffer(RenderType.entityCutout(texture.getTextureLocation())).
     *
     * @param id id of this texture. Must be unique
     * @return the texture, or null if creating it failed
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T extends RenderableDynamicTexture> T requestTexture(Identifier id, Supplier<T> textureSupplier) {
        RenderSystem.assertOnRenderThread();
        RenderableDynamicTexture texture = TEXTURE_CACHE.asMap().computeIfAbsent(id, key -> {
            try {
                T newTexture = textureSupplier.get();
                newTexture.register();
                newTexture.redraw();
                return newTexture;
            } catch (Throwable t) {
                Moonlight.LOGGER.error("Failed to create dynamic texture for id {}", key, t);
                return null;
            }
        });
        if (texture == null) return null;
        if (texture.isClosed()) {
            //closed behind our back, drop it so the next call makes a fresh one
            TEXTURE_CACHE.invalidate(id);
            return null;
        }
        return (T) texture;
    }

    /**
     * @param id                     id of this texture. Must be unique
     * @param textureSize            dimension in pixels
     * @param textureDrawingFunction responsible for drawing things onto this texture
     * @param updateEachFrame        redraw it on every tick from now on
     */
    @Nullable
    public static RenderableDynamicTexture requestTexture(
            Identifier id, int textureSize,
            @NotNull Consumer<RenderableDynamicTexture> textureDrawingFunction, boolean updateEachFrame) {
        var t = requestTexture(id, () -> new RenderableDynamicTexture(id, textureSize, textureDrawingFunction));
        if (t != null && updateEachFrame) {
            t.setUpdateNextTick(true);
        }
        return t;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T extends RenderableDynamicTexture> T getTextureIfPresent(Identifier id) {
        return (T) TEXTURE_CACHE.getIfPresent(id);
    }


    @Nullable
    public static RenderableDynamicTexture requestFlatItemStackTexture(Identifier res, ItemStack stack, int size) {
        return requestTexture(res, size, t -> drawItem(t, stack), true);
    }

    @Nullable
    public static RenderableDynamicTexture requestFlatItemTexture(Item item, int size) {
        return requestFlatItemTexture(item, size, null);
    }

    @Nullable
    public static RenderableDynamicTexture requestFlatItemTexture(Item item, int size, @Nullable Consumer<NativeImage> postProcessing) {
        Identifier id = Moonlight.res(Utils.getID(item).toString().replace(":", "/") + "/" + size);
        return requestFlatItemTexture(id, item, size, postProcessing, false);
    }

    @Nullable
    public static RenderableDynamicTexture requestFlatItemTexture(
            Identifier id, Item item, int size, @Nullable Consumer<NativeImage> postProcessing) {
        return requestFlatItemTexture(id, item, size, postProcessing, false);
    }

    /**
     * Draws a flat gui-like item onto a texture of the given size.
     *
     * @param id             texture id. Needs to be unique
     * @param item           item you want to draw
     * @param size           texture size
     * @param postProcessing extra drawing applied to the native image. Slow, it stalls on a gpu readback
     */
    @Nullable
    public static RenderableDynamicTexture requestFlatItemTexture(
            Identifier id, Item item, int size,
            @Nullable Consumer<NativeImage> postProcessing, boolean updateEachFrame) {
        return requestTexture(id, size, t -> {
            drawItem(t, item.getDefaultInstance());
            if (postProcessing != null) {
                t.download();
                postProcessing.accept(t.getPixels());
                t.upload();
            }
        }, updateEachFrame);
    }


    //Utility methods

    /**
     * Draws an item the way it looks in a gui, filling the whole texture.
     */
    public static void drawItem(RenderableDynamicTexture tex, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        drawAsInGUI(tex, (pose, collector) -> {
            ItemStackRenderState itemState = new ItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(itemState, stack, ItemDisplayContext.GUI, mc.level, mc.player, 0);
            mc.gameRenderer.getLighting().setupFor(itemState.usesBlockLight() ?
                    Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);

            pose.translate(GUI_BOX / 2, GUI_BOX / 2, 0);
            pose.scale(GUI_BOX, -GUI_BOX, GUI_BOX);
            itemState.submit(pose, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        });
    }

    /**
     * Same as drawAsInGUI but with coordinates going from 0 to 1 instead of 0 to 16.
     */
    public static void drawNormalized(RenderableDynamicTexture tex, BiConsumer<PoseStack, SubmitNodeCollector> drawFunction) {
        drawAsInGUI(tex, (pose, collector) -> {
            pose.scale(GUI_BOX, GUI_BOX, 1);
            drawFunction.accept(pose, collector);
        });
    }

    /**
     * Sets up an environment akin to gui rendering, with a box going from 0 to 16 with its origin top left,
     * and draws onto the texture with it. Everything submitted is flushed before this returns.
     * <p>
     * Lighting is set up for 3d items; change it inside your function if you need something else.
     */
    public static void drawAsInGUI(RenderableDynamicTexture tex, BiConsumer<PoseStack, SubmitNodeCollector> drawFunction) {
        RenderSystem.assertOnRenderThread();
        Minecraft mc = Minecraft.getInstance();
        if (projectionBuffer == null) projectionBuffer = new ProjectionMatrixBuffer("moonlight dynamic texture");

        RenderTarget target = tex.getRenderTarget();
        GpuTextureView oldColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView oldDepth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = target.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();

        RenderSystem.backupProjectionMatrix();
        PROJECTION.setupOrtho(-1000, 1000, GUI_BOX, GUI_BOX, true);
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(PROJECTION), ProjectionType.ORTHOGRAPHIC);
        mc.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);

        FeatureRenderDispatcher dispatcher = mc.gameRenderer.getFeatureRenderDispatcher();
        try {
            drawFunction.accept(new PoseStack(), dispatcher.getSubmitNodeStorage());
            dispatcher.renderAllFeatures();
            mc.renderBuffers().bufferSource().endBatch();
        } finally {
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.outputColorTextureOverride = oldColor;
            RenderSystem.outputDepthTextureOverride = oldDepth;
        }
    }
}
