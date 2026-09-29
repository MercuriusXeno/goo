package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * The tap-drip's look: a patch of its goo type's still fluid sprite under
 * the fluid tint muted toward the type's flat color, so the drop reads the
 * goo texture less noisily than the in-world fluid
 * (decision particles-render-muted-goo-texture).
 */
public final class TapDripLook {

    /** How far the fluid tint blends toward the type's flat color: 0 keeps the tint, 1 is flat. */
    static final float MUTE = 0.5f;

    /** Pixels across a fluid sprite, the step a patch offset moves by. */
    static final int SPRITE_PIXELS = 16;

    private TapDripLook() {
    }

    /**
     * Resolves the look of one tap-drip particle of a goo type.
     *
     * @param gooType the goo type the drip carries
     * @param spriteOf resolves a type's still fluid sprite
     * @param tintOf   resolves the tint a type's fluid renders under
     * @param colorOf  resolves a type's flat color
     * @param patchPixels the patch's width in sprite pixels, the drawn width at native scale
     * @param random   picks where on the sprite the patch sits
     * @return the sprite, the patch UVs and the muted color
     */
    static DripLook of(ResourceKey<GooTypeDefinition> gooType,
                       Function<ResourceKey<GooTypeDefinition>, TextureAtlasSprite> spriteOf,
                       ToIntFunction<ResourceKey<GooTypeDefinition>> tintOf,
                       ToIntFunction<ResourceKey<GooTypeDefinition>> colorOf,
                       int patchPixels, RandomSource random) {
        TextureAtlasSprite sprite = spriteOf.apply(gooType);
        int offsets = SPRITE_PIXELS - patchPixels + 1;
        return new DripLook(sprite,
                patch(sprite, patchPixels, random.nextInt(offsets), random.nextInt(offsets)),
                mute(tintOf.applyAsInt(gooType), colorOf.applyAsInt(gooType)));
    }

    /**
     * @param sprite      the fluid sprite
     * @param patchPixels the patch's width, in sprite pixels
     * @param offsetU     the patch's left edge, in sprite pixels
     * @param offsetV     the patch's top edge, in sprite pixels
     * @return the atlas UVs of that square of the sprite
     */
    static GooRenderUtil.UvRect patch(TextureAtlasSprite sprite, int patchPixels, int offsetU, int offsetV) {
        float span = (float) patchPixels / SPRITE_PIXELS;
        float u0 = (float) offsetU / SPRITE_PIXELS;
        float v0 = (float) offsetV / SPRITE_PIXELS;
        return GooSubmitter.spriteSubRect(sprite, u0, v0, u0 + span, v0 + span);
    }

    /**
     * @param fluidTint the tint the type's in-world fluid renders under
     * @param flatColor the type's flat color
     * @return the opaque tint blended {@link #MUTE} of the way toward the flat color
     */
    public static int mute(int fluidTint, int flatColor) {
        return ARGB.opaque(ARGB.srgbLerp(MUTE, fluidTint, flatColor));
    }
}
