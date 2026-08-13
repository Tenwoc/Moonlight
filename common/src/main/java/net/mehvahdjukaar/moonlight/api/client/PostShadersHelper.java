package net.mehvahdjukaar.moonlight.api.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lets several post effects be active at once, in a defined order, each owned by its own group so mods don't
 * clobber each other. Vanilla only has room for a single post effect id at a time
 * (net.minecraft.client.renderer.GameRenderer.setPostEffect), which is the whole reason this exists.
 * <p>
 * Effect ids are the same ones vanilla uses, i.e. the path of a post_effect/<id>.json definition.
 */
public class PostShadersHelper {

    public record Group(Identifier id, float priority) {
        public static final Group DEFAULT = new Group(Identifier.withDefaultNamespace("default"), 0);
        public static final Group SPECTATOR_SHADERS = new Group(Identifier.withDefaultNamespace("spectator_shaders"), 1);
    }

    /**
     * Effects applied to the main screen after the level is drawn, on top of whatever post effect vanilla itself
     * has active. Add to it with toggleEffect.
     */
    public static final EffectStack SCREEN = new EffectStack();

    /**
     * Adds a screen post effect without clearing the ones other mods added.
     *
     * @param newPost post effect id. Null to remove this group's effect
     * @param group   effect group, used for priority and mutual exclusivity
     */
    public static void toggleEffect(@Nullable Identifier newPost, Group group) {
        SCREEN.toggle(newPost, group);
    }

    /**
     * An ordered set of post effects, at most one per group. Runs them back to back over a render target, which
     * gives the same result as vanilla's single chain applied repeatedly.
     * Use your own instance to post process an offscreen target rather than the screen.
     */
    public static class EffectStack {

        private final Map<Group, Identifier> byGroup = new HashMap<>();
        private List<Identifier> ordered = List.of();

        /**
         * @param effect effect id, or null to clear this group
         * @return true if the stack actually changed
         */
        public boolean toggle(@Nullable Identifier effect, Group group) {
            Identifier old = effect == null ? byGroup.remove(group) : byGroup.put(group, effect);
            if (Objects.equals(old, effect)) return false;
            this.ordered = byGroup.entrySet().stream()
                    .sorted(Comparator.comparingDouble(e -> e.getKey().priority()))
                    .map(Map.Entry::getValue)
                    .toList();
            return true;
        }

        public boolean isEmpty() {
            return ordered.isEmpty();
        }

        public List<Identifier> effects() {
            return ordered;
        }

        /**
         * Runs every effect in priority order. Effects whose definition is missing or failed to compile are skipped,
         * same as vanilla does for its own.
         */
        public void process(RenderTarget target, GraphicsResourceAllocator resourcePool) {
            if (ordered.isEmpty()) return;
            ShaderManager shaderManager = Minecraft.getInstance().getShaderManager();
            for (Identifier effect : ordered) {
                PostChain chain = shaderManager.getPostChain(effect, LevelTargetBundle.MAIN_TARGETS);
                if (chain != null) chain.process(target, resourcePool);
            }
        }
    }

    @ApiStatus.Internal
    public static void processScreenEffects(GraphicsResourceAllocator resourcePool) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        SCREEN.process(mc.getMainRenderTarget(), resourcePool);
    }
}
