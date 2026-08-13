package net.mehvahdjukaar.moonlight.api.item;

import com.mojang.blaze3d.vertex.PoseStack;
import net.mehvahdjukaar.candlelight.api.ClientOnly;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Takes over first person rendering entirely, injected early enough to cancel most of the vanilla code.
 * Use to render the arm together with the item, like vanilla maps do.
 * Attach with ClientAnimationExtension.attach, or implement directly in a client only item class.
 */
public interface IFirstPersonSpecialItemRenderer {

    /**
     * @return true to cancel the original item renderer
     */
    @ClientOnly
    boolean renderFirstPersonItem(AbstractClientPlayer player, ItemStack stack, InteractionHand hand, HumanoidArm arm, PoseStack poseStack,
                                  float partialTicks, float pitch, float attackAnim, float equipAnim,
                                  MultiBufferSource buffer, int light, ItemInHandRenderer renderer);

    @Nullable
    static IFirstPersonSpecialItemRenderer get(Item target) {
        if (target instanceof IFirstPersonSpecialItemRenderer p) return p;
        ClientAnimationExtension ext = ClientAnimationExtension.get(target);
        return ext == null ? null : ext.firstPersonRenderer();
    }
}
