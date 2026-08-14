package net.mehvahdjukaar.moonlight.api.fluids.client;

import net.mehvahdjukaar.moonlight.api.client.TextureCache;
import net.mehvahdjukaar.moonlight.api.client.texture_renderer.DynamicTextureRenderer;
import net.mehvahdjukaar.moonlight.api.fluids.SoftFluid;
import net.mehvahdjukaar.moonlight.api.fluids.SoftFluidRegistry;
import net.mehvahdjukaar.moonlight.api.fluids.SoftFluidStack;
import net.mehvahdjukaar.moonlight.api.fluids.SoftFluidTank;
import net.mehvahdjukaar.moonlight.api.platform.ClientHelper;
import net.mehvahdjukaar.moonlight.api.resources.textures.PalettedPermutationsHelper;
import net.mehvahdjukaar.moonlight.core.Moonlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * Everything tint related for soft fluids. Lives in its own client package so that nothing a dedicated server
 * loads, SoftFluidStack and SoftFluidTank in particular, ever names a client only type.
 * Since 26.1 BlockAndTintGetter is client only, so these queries cannot live on the fluid classes anymore.
 */
public class SoftFluidColors implements ResourceManagerReloadListener {

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        //also using this to reset texture cache
        DynamicTextureRenderer.clearCache();

        //also using for this
        TextureCache.clear();
        PalettedPermutationsHelper.invalidate();

        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            refreshParticleColors(SoftFluidRegistry.get(level.registryAccess()));
        }
    }

    /**
     * Called once the server has sent over its soft fluid registry, so that fluids that only exist in a data pack
     * get their particle color too
     */
    public static void onFluidsSynced(RegistryAccess registryAccess) {
        refreshParticleColors(SoftFluidRegistry.get(registryAccess));
    }

    /**
     * Copies textures and tint off the fluid a soft fluid points its use_texture_from at, and averages its still
     * texture for the particle color. Both have to happen here rather than in the SoftFluid constructor: fluid models
     * are baked on a resource reload while soft fluids are built on a datapack one, so at construction time the model
     * either doesn't exist yet or belongs to the previous pack.
     */
    public static void refreshParticleColors(Registry<SoftFluid> reg) {
        for (var fluid : reg) {
            fluid.setResolvedAppearance(resolveAppearance(fluid.getTextureOverride()));

            Identifier location = fluid.getStillTexture();
            int averageColor = -1;

            int tint = fluid.getTintMethod().appliesToStill() ? fluid.getTintColor() : -1;

            TextureAtlas textureMap = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
            TextureAtlasSprite sprite = textureMap.getSprite(location);
            try {
                averageColor = getAverageColor(sprite, tint);
            } catch (Exception e) {
                Moonlight.LOGGER.warn("Failed to load particle color for {} using current resource pack. might be a broken png.mcmeta", sprite);
            }
            fluid.setAverageTextureTint(averageColor);
        }
    }

    //credits to Random832
    @SuppressWarnings("ConstantConditions")
    private static int getAverageColor(TextureAtlasSprite sprite, int tint) {
        var c = sprite.contents();
        if (sprite == null || c.getFrameCount() == 0) return -1;

        int tintR = tint >> 16 & 255;
        int tintG = tint >> 8 & 255;
        int tintB = tint & 255;
        int total = 0, totalR = 0, totalB = 0, totalG = 0;

        for (int tryFrame = 0; tryFrame < c.getFrameCount(); tryFrame++) {
            try {
                for (int x = 0; x < c.width(); x++) {
                    for (int y = 0; y < c.height(); y++) {

                        int pixel = ClientHelper.getPixelABGR(sprite, tryFrame, x, y);

                        // this is in 0xAABBGGRR format, not the usual 0xAARRGGBB.
                        int pixelB = pixel >> 16 & 255;
                        int pixelG = pixel >> 8 & 255;
                        int pixelR = pixel & 255;
                        ++total;
                        totalR += pixelR;
                        totalG += pixelG;
                        totalB += pixelB;
                    }
                }
                break;
            } catch (Exception e) {
                total = 0;
                totalR = 0;
                totalB = 0;
                totalG = 0;
            }
        }
        if (total <= 0) return -1;
        return ARGB.color(255,
                totalR / total * tintR / 255,
                totalG / total * tintG / 255,
                totalB / total * tintB / 255);
    }

    @Nullable
    private static SoftFluid.Appearance resolveAppearance(@Nullable Identifier useTexturesFrom) {
        if (useTexturesFrom == null) return null;
        Fluid fluid = BuiltInRegistries.FLUID.getValue(useTexturesFrom);
        if (fluid == null || fluid == Fluids.EMPTY) return null;
        FluidState state = fluid.defaultFluidState();
        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(state);
        BlockTintSource tint = model.tintSource();
        return new SoftFluid.Appearance(
                model.stillMaterial().sprite().contents().name(),
                model.flowingMaterial().sprite().contents().name(),
                tint == null ? -1 : tint.color(state.createLegacyBlock()));
    }

    /**
     * World and stack dependent tint of the vanilla fluid a soft fluid stack maps onto, which beats the fluid's own
     * flat tint color. 0 when there is nothing better than that flat color to show.
     */
    public static int getSpecialColor(SoftFluidStack stack, @Nullable BlockAndTintGetter world, @Nullable BlockPos pos) {
        //yay hardcoding
        DyedItemColor dyeColor = stack.get(DataComponents.DYED_COLOR);
        if (dyeColor != null) return dyeColor.rgb();

        PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
        if (potionContents != null) return potionContents.getColor();

        DyeColor discreteDyeColor = stack.get(DataComponents.BASE_COLOR);
        if (discreteDyeColor != null) return discreteDyeColor.getTextureDiffuseColor();

        //at least this works for any fluid
        Fluid fluid = stack.getVanillaFluid().value();
        if (fluid == Fluids.EMPTY) return 0;
        FluidState fluidState = fluid.defaultFluidState();
        BlockTintSource tint = Minecraft.getInstance().getModelManager()
                .getFluidStateModelSet().get(fluidState).tintSource();
        if (tint == null) return 0;
        BlockState blockState = fluidState.createLegacyBlock();
        int color = world != null && pos != null
                ? tint.colorInWorld(blockState, world, pos)
                : tint.color(blockState);
        return color == -1 ? 0 : color;
    }

    /**
     * @return tint color to be applied on the fluid texture
     */
    public static int getStillColor(SoftFluidStack fluidStack, @Nullable BlockAndTintGetter world, @Nullable BlockPos pos) {
        SoftFluid fluid = fluidStack.fluid();
        SoftFluid.TintMethod method = fluid.getTintMethod();
        if (method == SoftFluid.TintMethod.NO_TINT) return -1;
        int specialColor = getSpecialColor(fluidStack, world, pos);

        if (specialColor != 0) return specialColor;
        return fluid.getTintColor();
    }

    /**
     * @return tint color to be applied on the flowing fluid texture
     */
    public static int getFlowingColor(SoftFluidStack fluidStack, @Nullable BlockAndTintGetter world, @Nullable BlockPos pos) {
        SoftFluid.TintMethod method = fluidStack.fluid().getTintMethod();
        if (method == SoftFluid.TintMethod.FLOWING) return getParticleColor(fluidStack, world, pos);
        else return getStillColor(fluidStack, world, pos);
    }

    /**
     * @return tint color to be used on particles. Differs from the still color since it falls back to a color
     * extrapolated from the fluid texture itself
     */
    public static int getParticleColor(SoftFluidStack fluidStack, @Nullable BlockAndTintGetter world, @Nullable BlockPos pos) {
        int tintColor = getStillColor(fluidStack, world, pos);
        //if tint color is white gets averaged color
        if (tintColor == -1) return fluidStack.fluid().getAverageTextureTintColor();
        return tintColor;
    }

    public static int getCachedStillColor(SoftFluidTank tank, @Nullable BlockAndTintGetter world, @Nullable BlockPos pos) {
        return refreshIfNeeded(tank, world, pos).still;
    }

    public static int getCachedFlowingColor(SoftFluidTank tank, @Nullable BlockAndTintGetter world, @Nullable BlockPos pos) {
        return refreshIfNeeded(tank, world, pos).flowing;
    }

    public static int getCachedParticleColor(SoftFluidTank tank, @Nullable BlockAndTintGetter world, @Nullable BlockPos pos) {
        return refreshIfNeeded(tank, world, pos).particle;
    }

    private static SoftFluidTank.TintCache refreshIfNeeded(SoftFluidTank tank, @Nullable BlockAndTintGetter world,
                                                           @Nullable BlockPos pos) {
        SoftFluidTank.TintCache cache = tank.getTintCache();
        if (cache.needsRefresh) {
            SoftFluidStack fluidStack = tank.getFluid();
            cache.still = getStillColor(fluidStack, world, pos);
            cache.flowing = getFlowingColor(fluidStack, world, pos);
            cache.particle = getParticleColor(fluidStack, world, pos);
            cache.needsRefresh = false;
        }
        return cache;
    }

}
