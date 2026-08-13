package net.mehvahdjukaar.moonlight.core.mixins;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.mehvahdjukaar.moonlight.api.client.PostShadersHelper;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow
    @Final
    private CrossFrameResourcePool resourcePool;

    // the pop of the "world" section: right after vanilla's own post effect and still inside the
    // shouldRenderLevel branch, so a paused frame doesn't reprocess a stale target.
    // Vanilla can only ever have one effect id active, so the extra grouped ones are run here
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/profiling/ProfilerFiller;pop()V", ordinal = 0))
    private void moonlight$processExtraPostEffects(CallbackInfo ci) {
        PostShadersHelper.processScreenEffects(this.resourcePool);
    }

    // vanilla drops its own effect here when the camera entity has none. Drop the matching group too, otherwise
    // an entity shader added by a mod at the tail of this method would stay on forever
    @Inject(method = "checkEntityPostEffect", at = @At("HEAD"))
    private void moonlight$clearSpectatorGroup(@Nullable Entity cameraEntity, CallbackInfo ci) {
        PostShadersHelper.toggleEffect(null, PostShadersHelper.Group.SPECTATOR_SHADERS);
    }
}
