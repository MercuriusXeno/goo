package com.mercuriusxeno.goo.client;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.BlobModelSize;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypeTextures;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests the texture-resolution seam of decision type-named-textures: a type
 * naming stitched textures answers them untinted, and a type naming none,
 * or naming a sprite the atlas lacks, answers the grey base tinted.
 */
class GooTypeSpritesTest {

    private static final String BLAZE_JSON = "/data/goo/goo/goo_type/blaze.json";
    private static final Identifier BLAZE_FLUID = Identifier.parse("goo:fluid/blaze_fluid");
    private static final Predicate<Identifier> EVERY_SPRITE_STITCHED = id -> true;
    private static final Predicate<Identifier> NO_SPRITE_STITCHED = id -> false;

    /**
     * Bundled blaze answers its own blob sprite at each size, untinted.
     */
    @ParameterizedTest
    @EnumSource(BlobModelSize.class)
    void blob_namedTexturesAnswerBlazeArt(BlobModelSize size) throws Exception {
        Identifier expected = Identifier.parse("goo:item/blaze_blob_" + size.getSerializedName());
        assertEquals(new GooTypeSprites.TypeSprite(expected, false),
                GooTypeSprites.blob(blazeTextures(), size, EVERY_SPRITE_STITCHED));
    }

    /**
     * A type naming no textures, a key no entry stands behind, and a named
     * sprite the atlas lacks each answer the tinted grey base of the size.
     */
    @ParameterizedTest
    @EnumSource(BlobModelSize.class)
    void blob_unnamedOrUnstitchedAnswersTintedGreyBase(BlobModelSize size) throws Exception {
        GooTypeSprites.TypeSprite grey = new GooTypeSprites.TypeSprite(
                Identifier.parse("goo:item/goo_blob_" + size.getSerializedName()), true);
        assertEquals(grey, GooTypeSprites.blob(GooTypeTextures.NONE, size, EVERY_SPRITE_STITCHED));
        assertEquals(grey, GooTypeSprites.blob(null, size, EVERY_SPRITE_STITCHED));
        assertEquals(grey, GooTypeSprites.blob(blazeTextures(), size, NO_SPRITE_STITCHED));
    }

    /**
     * Bundled blaze answers its fluid sprite for still and flowing, untinted.
     */
    @Test
    void fluid_namedTexturesAnswerBlazeArt() throws Exception {
        assertEquals(new GooTypeSprites.FluidSprites(BLAZE_FLUID, BLAZE_FLUID, false),
                GooTypeSprites.fluid(blazeTextures(), EVERY_SPRITE_STITCHED));
    }

    /**
     * A type naming no fluid textures, a key no entry stands behind, and a
     * named still sprite the atlas lacks each answer the tinted grey base.
     */
    @Test
    void fluid_unnamedOrUnstitchedAnswersTintedGreyBase() throws Exception {
        GooTypeSprites.FluidSprites grey = new GooTypeSprites.FluidSprites(
                GooTypeSprites.GREY_FLUID, GooTypeSprites.GREY_FLUID, true);
        assertEquals(grey, GooTypeSprites.fluid(GooTypeTextures.NONE, EVERY_SPRITE_STITCHED));
        assertEquals(grey, GooTypeSprites.fluid(null, EVERY_SPRITE_STITCHED));
        assertEquals(grey, GooTypeSprites.fluid(blazeTextures(), NO_SPRITE_STITCHED));
    }

    /**
     * A named still sprite with no stitched flowing sprite answers the still
     * sprite for both, untinted.
     */
    @Test
    void fluid_flowingFallsBackToNamedStill() {
        Identifier still = Identifier.parse("pack:fluid/still");
        Identifier flowing = Identifier.parse("pack:fluid/flowing");
        GooTypeTextures textures = new GooTypeTextures(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(still), Optional.of(flowing));
        assertEquals(new GooTypeSprites.FluidSprites(still, still, false),
                GooTypeSprites.fluid(textures, still::equals));
        assertEquals(new GooTypeSprites.FluidSprites(still, flowing, false),
                GooTypeSprites.fluid(textures, EVERY_SPRITE_STITCHED));
    }

    private static GooTypeTextures blazeTextures() throws Exception {
        try (InputStream in = GooTypeSpritesTest.class.getResourceAsStream(BLAZE_JSON)) {
            assertNotNull(in, "Bundled JSON missing on classpath: " + BLAZE_JSON);
            return GooTypeDefinition.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .getOrThrow().textures();
        }
    }
}
