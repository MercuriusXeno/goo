package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.BlobModelSize;
import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeTextures;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Resolves the sprites a goo type renders with (decision
 * type-named-textures): the texture its JSON names when that texture is
 * stitched, otherwise the grey base that the type's highlight color tints.
 * The atlas lookup is passed in, so the choice reads without a client.
 */
public final class GooTypeSprites {

    /**
     * The grey base fluid sprite, on the block atlas.
     */
    public static final Identifier GREY_FLUID = Identifier.fromNamespaceAndPath(Goo.MODID, "fluid/goo_fluid");

    private GooTypeSprites() {
    }

    /**
     * One resolved sprite.
     *
     * @param sprite the sprite id
     * @param tinted true when the sprite is the grey base, which the type's highlight color tints
     */
    public record TypeSprite(Identifier sprite, boolean tinted) {
    }

    /**
     * The still and flowing sprites of a type's fluid, tinted together
     * because one fluid model carries one tint.
     *
     * @param still   the still sprite id
     * @param flowing the flowing sprite id
     * @param tinted  true when both are the grey base, which the type's highlight color tints
     */
    public record FluidSprites(Identifier still, Identifier flowing, boolean tinted) {
    }

    /**
     * The blob sprite of a type at a model size.
     *
     * @param textures the type's named textures, or null for a key no entry stands behind
     * @param size     the blob model size
     * @param stitched whether a sprite id is stitched into the item atlas
     * @return the named sprite when stitched, otherwise the tinted grey base of that size
     */
    public static TypeSprite blob(@Nullable GooTypeTextures textures, BlobModelSize size,
                                  Predicate<Identifier> stitched) {
        Optional<Identifier> named = textures == null ? Optional.empty() : textures.blob(size).filter(stitched);
        return named.map(id -> new TypeSprite(id, false)).orElseGet(() -> new TypeSprite(size.greyBase(), true));
    }

    /**
     * The fluid sprites of a type. A named still sprite that is stitched
     * wins; the flowing sprite falls back to it when the JSON names no
     * stitched flowing sprite, since the bundled art draws both from one texture.
     *
     * @param textures the type's named textures, or null for a key no entry stands behind
     * @param stitched whether a sprite id is stitched into the block atlas
     * @return the named sprites when the still sprite is stitched, otherwise the tinted grey base
     */
    public static FluidSprites fluid(@Nullable GooTypeTextures textures, Predicate<Identifier> stitched) {
        Optional<Identifier> still = textures == null ? Optional.empty() : textures.fluidStill().filter(stitched);
        if (still.isEmpty()) {
            return new FluidSprites(GREY_FLUID, GREY_FLUID, true);
        }
        Identifier flowing = textures.fluidFlowing().filter(stitched).orElse(still.get());
        return new FluidSprites(still.get(), flowing, false);
    }
}
