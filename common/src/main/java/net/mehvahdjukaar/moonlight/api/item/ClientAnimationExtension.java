package net.mehvahdjukaar.moonlight.api.item;

import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.core.Moonlight;
import net.mehvahdjukaar.moonlight.core.misc.IExtendedItem;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * Client only animation and rendering extensions attached to an item.
 * An item has at most one of these, holding one slot per extension interface, so several mods can extend the
 * same item as long as they don't claim the same slot.
 * Preferred over implementing the interfaces on the item itself, which can only be done safely in client only classes
 */
public final class ClientAnimationExtension {

    @Nullable
    private IFirstPersonAnimationProvider firstPersonAnimation;
    @Nullable
    private IThirdPersonAnimationProvider thirdPersonAnimation;
    @Nullable
    private IFirstPersonSpecialItemRenderer firstPersonRenderer;
    @Nullable
    private IThirdPersonSpecialItemRenderer thirdPersonRenderer;

    /**
     * Attaches a client extension to any item. The object claims every slot whose interface it implements, so a
     * single object can provide all four behaviors at once.
     * Safe to call from common code, does nothing on a dedicated server
     */
    public static void attach(Item item, Object extension) {
        if (!PlatHelper.getPhysicalSide().isClient()) return;
        IExtendedItem extendedItem = (IExtendedItem) item;
        ClientAnimationExtension ext = extendedItem.moonlight$getClientAnimationExtension();
        if (ext == null) {
            ext = new ClientAnimationExtension();
            extendedItem.moonlight$setClientAnimationExtension(ext);
        }
        boolean claimedAnything = false;
        if (extension instanceof IFirstPersonAnimationProvider p) {
            ext.firstPersonAnimation = claimSlot(ext.firstPersonAnimation, p, item, "first person animation");
            claimedAnything = true;
        }
        if (extension instanceof IThirdPersonAnimationProvider p) {
            ext.thirdPersonAnimation = claimSlot(ext.thirdPersonAnimation, p, item, "third person animation");
            claimedAnything = true;
        }
        if (extension instanceof IFirstPersonSpecialItemRenderer p) {
            ext.firstPersonRenderer = claimSlot(ext.firstPersonRenderer, p, item, "first person renderer");
            claimedAnything = true;
        }
        if (extension instanceof IThirdPersonSpecialItemRenderer p) {
            ext.thirdPersonRenderer = claimSlot(ext.thirdPersonRenderer, p, item, "third person renderer");
            claimedAnything = true;
        }
        if (!claimedAnything) {
            throw new IllegalArgumentException("Object " + extension.getClass() +
                    " does not implement any client animation extension interface");
        }
    }

    private static <T> T claimSlot(@Nullable T existing, T incoming, Item item, String slotName) {
        if (existing != null && existing != incoming) {
            String msg = "A " + slotName + " extension (" + existing.getClass() + ") was already attached to item " +
                    item + ". It will be replaced by " + incoming.getClass();
            if (PlatHelper.isDev()) throw new AssertionError(msg);
            Moonlight.LOGGER.warn(msg);
        }
        return incoming;
    }

    @Nullable
    public static ClientAnimationExtension get(Item item) {
        return ((IExtendedItem) item).moonlight$getClientAnimationExtension();
    }

    @Nullable
    public IFirstPersonAnimationProvider firstPersonAnimation() {
        return firstPersonAnimation;
    }

    @Nullable
    public IThirdPersonAnimationProvider thirdPersonAnimation() {
        return thirdPersonAnimation;
    }

    @Nullable
    public IFirstPersonSpecialItemRenderer firstPersonRenderer() {
        return firstPersonRenderer;
    }

    @Nullable
    public IThirdPersonSpecialItemRenderer thirdPersonRenderer() {
        return thirdPersonRenderer;
    }
}
