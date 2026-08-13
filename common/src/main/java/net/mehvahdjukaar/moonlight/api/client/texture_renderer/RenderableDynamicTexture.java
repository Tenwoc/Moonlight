package net.mehvahdjukaar.moonlight.api.client.texture_renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.mehvahdjukaar.moonlight.core.Moonlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TickableTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * A texture you render into instead of loading from a resource pack. Register it with
 * register and its id then works anywhere a normal texture id does.
 * <p>
 * Content comes from a drawing function which is run by redraw with the game's draw output
 * pointed at this texture, so anything drawn inside it lands here rather than on screen.
 * DynamicTextureRenderer has helpers for the common cases and a cache keyed by texture id.
 */
public class RenderableDynamicTexture extends AbstractTexture implements TickableTexture {

    //runs when the texture is initialized and populates it. Runs again each tick if it's set to
    @NotNull
    protected final Consumer<? super RenderableDynamicTexture> drawingFunction;

    private final Identifier textureLocation;
    private final TextureTarget target;

    //cpu copy, only allocated if download() is ever called
    @Nullable
    private NativeImage pixels;

    private volatile boolean shouldTick = true;
    private boolean closed = false;

    @SuppressWarnings("unchecked")
    public RenderableDynamicTexture(Identifier resourceLocation, int width, int height,
                                    @NotNull Consumer<? extends RenderableDynamicTexture> textureDrawingFunction) {
        RenderSystem.assertOnRenderThread();
        this.textureLocation = resourceLocation;
        this.drawingFunction = (Consumer<? super RenderableDynamicTexture>) textureDrawingFunction;
        //depth is needed for 3d block models to sort against themselves
        this.target = new TextureTarget(resourceLocation.toString(), width, height, true);
        //hand the target's color attachment to the texture manager. Both halves of the same gpu texture
        this.texture = target.getColorTexture();
        this.textureView = target.getColorTextureView();
    }

    public RenderableDynamicTexture(Identifier resourceLocation, int size,
                                    @NotNull Consumer<? extends RenderableDynamicTexture> textureDrawingFunction) {
        this(resourceLocation, size, size, textureDrawingFunction);
    }

    public Identifier getTextureLocation() {
        return textureLocation;
    }

    /**
     * The framebuffer this texture is the color attachment of. Needed to run a PostChain over the
     * result, or to point rendering code that resolves its own target at this texture.
     */
    public RenderTarget getRenderTarget() {
        return target;
    }

    public int getWidth() {
        return target.width;
    }

    public int getHeight() {
        return target.height;
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * Clears the texture and runs the drawing function with the draw output redirected onto it.
     * You normally don't call this yourself: it happens on creation and on any tick where
     * setUpdateNextTick was set.
     */
    public void redraw() {
        if (closed) {
            Moonlight.LOGGER.error("Tried to redraw closed dynamic texture {}", textureLocation);
            return;
        }
        RenderSystem.assertOnRenderThread();
        RenderSystem.getDevice().createCommandEncoder()
                .clearColorAndDepthTextures(target.getColorTexture(), 0, target.getDepthTexture(), 1);

        GpuTextureView oldColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView oldDepth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = target.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();
        try {
            drawingFunction.accept(this);
        } finally {
            RenderSystem.outputColorTextureOverride = oldColor;
            RenderSystem.outputDepthTextureOverride = oldDepth;
        }
    }

    /**
     * The cpu side copy of this texture, valid after a download. Edit it and push the result
     * back with upload.
     */
    public NativeImage getPixels() {
        if (this.pixels == null) {
            this.pixels = new NativeImage(getWidth(), getHeight(), false);
        }
        return this.pixels;
    }

    /**
     * Reads the texture back into getPixels. This blocks until the gpu has finished every draw
     * queued before it, so keep it to one-off texture generation rather than per frame work.
     * <p>
     * Rows come back in render target order, which is bottom up. upload is its exact inverse,
     * so an edit that round trips through both comes out the way you left it.
     */
    public void download() {
        if (closed) {
            Moonlight.LOGGER.error("Tried to download closed dynamic texture {}", textureLocation);
            return;
        }
        RenderSystem.assertOnRenderThread();
        GpuTexture color = target.getColorTexture();
        int width = target.width;
        int height = target.height;
        int pixelSize = color.getFormat().pixelSize();
        NativeImage image = getPixels();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Moonlight texture readback",
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) width * height * pixelSize)) {
            encoder.copyTextureToBuffer(color, buffer, 0, () -> {
            }, 0);
            awaitGpu(encoder);
            try (GpuBuffer.MappedView view = encoder.mapBuffer(buffer, true, false)) {
                ByteBuffer data = view.data();
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        image.setPixelABGR(x, y, data.getInt((x + y * width) * pixelSize));
                    }
                }
            }
        }
    }

    /**
     * Pushes getPixels back onto the gpu. Counterpart of download.
     */
    public void upload() {
        if (closed || pixels == null) return;
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(target.getColorTexture(), pixels);
    }

    //the completion callback of copyTextureToBuffer is only polled once a frame, and mapping right after the
    //copy races the draws feeding it, so block on our own fence instead. awaitCompletion doesn't flush by itself
    private static void awaitGpu(CommandEncoder encoder) {
        try (GpuFence fence = encoder.createFence()) {
            GL11.glFlush();
            //noinspection StatementWithEmptyBody
            while (!fence.awaitCompletion(1_000_000_000L)) {
            }
        }
    }

    /**
     * Marks this texture to be redrawn on the next client tick. Set it every tick for a live texture.
     */
    public void setUpdateNextTick(boolean shouldTick) {
        this.shouldTick = shouldTick;
    }

    @ApiStatus.Internal
    @Override
    public void tick() {
        if (!shouldTick) return;
        shouldTick = false;
        redraw();
    }

    public void register() {
        Minecraft.getInstance().getTextureManager().register(textureLocation, this);
    }

    public void unregister() {
        //this also calls close
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        AbstractTexture t = tm.getTexture(textureLocation);
        //if it's us we release it. Otherwise it means we have already been closed
        if (t == this) {
            tm.release(textureLocation);
        }
    }

    @Override
    public void close() {
        this.closed = true;
        //the render target owns the color texture we borrowed, so it does the freeing. Drop our
        //references first so AbstractTexture#close doesn't double free them
        this.texture = null;
        this.textureView = null;
        this.target.destroyBuffers();
        if (this.pixels != null) {
            this.pixels.close();
            this.pixels = null;
        }
    }
}
