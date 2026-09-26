package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooTypeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Rendering helpers that emit no vertex: the UV rectangle, the fluid sprite
 * lookup and the crosshair test; {@link RenderContext} is the one vertex
 * emitter (decision render-context-is-the-one-emitter).
 */
public final class GooRenderUtil {

    /** Fully opaque white in ARGB. */
    public static final int OPAQUE_WHITE = 0xFFFFFFFF;

    private GooRenderUtil() {}

    /**
     * Looks up the fluid sprite for a goo type; the resolution lives in
     * {@link GooSubmitter#fluidSprite(ResourceKey<GooTypeDefinition>)}.
     *
     * @param type the goo type
     * @return the fluid sprite
     */
    public static TextureAtlasSprite lookupFluidSprite(ResourceKey<GooTypeDefinition> type) {
        return GooSubmitter.fluidSprite(type);
    }

    /**
     * Returns true if the player's crosshair is currently on the given
     * block position. Used by BERs to highlight when aimed at.
     *
     * @param pos the block position
     * @return true if blockTargeted
     */
    public static boolean isBlockTargeted(BlockPos pos) {
        HitResult hit = Minecraft.getInstance().hitResult;
        return hit instanceof BlockHitResult bhr
                && bhr.getType() == HitResult.Type.BLOCK
                && bhr.getBlockPos().equals(pos);
    }

    /**
     * UV rectangle: texture coordinate bounds for a quad face.
     *
     * @param u0 the minimum U coordinate
     * @param v0 the minimum V coordinate
     * @param u1 the maximum U coordinate
     * @param v1 the maximum V coordinate
     */
    public record UvRect(float u0, float v0, float u1, float v1) {}
}
