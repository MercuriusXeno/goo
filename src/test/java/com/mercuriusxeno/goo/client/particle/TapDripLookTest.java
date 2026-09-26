package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Covers the tap-drip's look: its goo type's fluid sprite, a patch of it, and the muted tint. */
class TapDripLookTest {

    private static final ResourceKey<GooTypeDefinition> TYPE = GooTypes.ROCK;
    private static final float SPRITE_U0 = 0.25f;
    private static final float SPRITE_V0 = 0.5f;
    private static final float SPRITE_U1 = 0.375f;
    private static final float SPRITE_V1 = 0.625f;
    private static final int FLUID_TINT = 0xFFFFFFFF;
    private static final int FLAT_COLOR = 0xFF336699;
    private static final float UV_TOLERANCE = 1e-6f;

    private static TextureAtlasSprite fluidSprite() {
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.getU0()).thenReturn(SPRITE_U0);
        when(sprite.getV0()).thenReturn(SPRITE_V0);
        when(sprite.getU1()).thenReturn(SPRITE_U1);
        when(sprite.getV1()).thenReturn(SPRITE_V1);
        return sprite;
    }

    @Nested
    class SpriteResolution {

        private final TextureAtlasSprite sprite = fluidSprite();

        private DripLook lookFor(long seed) {
            return TapDripLook.of(TYPE,
                    type -> type == TYPE ? sprite : mock(TextureAtlasSprite.class),
                    type -> FLUID_TINT, type -> FLAT_COLOR, RandomSource.create(seed));
        }

        @Test
        void drawsTheTypesStillFluidSprite() {
            assertSame(sprite, lookFor(0L).sprite());
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L})
        void quadUvsLieInsideTheSpriteAndSpanAPatchOfIt(long seed) {
            GooRenderUtil.UvRect uv = lookFor(seed).uv();

            assertTrue(uv.u0() >= SPRITE_U0 - UV_TOLERANCE && uv.u1() <= SPRITE_U1 + UV_TOLERANCE, "u " + uv);
            assertTrue(uv.v0() >= SPRITE_V0 - UV_TOLERANCE && uv.v1() <= SPRITE_V1 + UV_TOLERANCE, "v " + uv);
            assertEquals((SPRITE_U1 - SPRITE_U0) * TapDripLook.PATCH_SPAN, uv.u1() - uv.u0(), UV_TOLERANCE);
            assertEquals((SPRITE_V1 - SPRITE_V0) * TapDripLook.PATCH_SPAN, uv.v1() - uv.v0(), UV_TOLERANCE);
        }

        @Test
        void farthestPatchEndsOnTheSpritesEdge() {
            GooRenderUtil.UvRect uv = TapDripLook.patch(sprite, 8, 8);

            assertEquals(SPRITE_U1, uv.u1(), UV_TOLERANCE);
            assertEquals(SPRITE_V1, uv.v1(), UV_TOLERANCE);
        }
    }

    @Nested
    class Mute {

        @Test
        void mutedColorLiesBetweenTheFluidTintAndTheFlatColorAtTheNamedFactor() {
            int muted = TapDripLook.mute(FLUID_TINT, FLAT_COLOR);

            assertChannelBetween(muted, ARGB::red);
            assertChannelBetween(muted, ARGB::green);
            assertChannelBetween(muted, ARGB::blue);
            assertEquals(ARGB.opaque(ARGB.srgbLerp(TapDripLook.MUTE, FLUID_TINT, FLAT_COLOR)), muted);
            assertEquals(0xFF, ARGB.alpha(muted));
        }

        @Test
        void aTintAlreadyFlatStaysFlat() {
            assertEquals(FLAT_COLOR, TapDripLook.mute(FLAT_COLOR, FLAT_COLOR));
        }

        private void assertChannelBetween(int muted, IntUnaryOperator channel) {
            int tint = channel.applyAsInt(FLUID_TINT);
            int flat = channel.applyAsInt(FLAT_COLOR);
            int value = channel.applyAsInt(muted);
            assertTrue(value > Math.min(tint, flat) && value < Math.max(tint, flat),
                    "channel " + value + " not strictly between " + tint + " and " + flat);
        }
    }
}
