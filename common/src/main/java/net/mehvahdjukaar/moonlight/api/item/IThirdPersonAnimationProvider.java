package net.mehvahdjukaar.moonlight.api.item;

import net.mehvahdjukaar.candlelight.api.ClientOnly;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Poses the holder arms before the item is rendered in third person.
 * Attach with ClientAnimationExtension.attach, or implement directly in a client only item class.
 * Returning ArmPose.SPYGLASS from getUseAnimation avoids the arm bob animation, otherwise call
 * AnimationUtils.bobModelPart(model.leftArm, entity.tickCount, -1.0F) yourself to "unbob" the arms, needed for two handed animations
 */
public interface IThirdPersonAnimationProvider {

    /**
     * @return true if the default animation should be skipped
     */
    @ClientOnly
    boolean poseRightArm(ItemStack stack, HumanoidModel<?> model, HumanoidRenderState state, HumanoidArm mainArm);

    /**
     * @return true if the default animation should be skipped
     */
    @ClientOnly
    boolean poseLeftArm(ItemStack stack, HumanoidModel<?> model, HumanoidRenderState state, HumanoidArm mainArm);

    /**
     * Controls whether the item in the other hand renders or not
     */
    @ClientOnly
    default boolean isTwoHanded() {
        return false;
    }

    @Nullable
    static IThirdPersonAnimationProvider get(Item target) {
        if (target instanceof IThirdPersonAnimationProvider p) return p;
        ClientAnimationExtension ext = ClientAnimationExtension.get(target);
        return ext == null ? null : ext.thirdPersonAnimation();
    }

}
