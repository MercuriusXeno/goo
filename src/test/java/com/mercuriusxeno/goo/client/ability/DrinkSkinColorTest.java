package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypeTextures;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Tests that an Unmake stream reaches the hand in its goo's own solid color,
 * the mean of its fluid sprite's own pixels, for a type that wears its own
 * named fluid sprite as every bundled type does, and crossfades to it from
 * the block's color along the route (decisions unmake-skin-is-a-solid-sampled-color
 * and diagnose-then-fix-unmake-metallic-color). The client is stood up by
 * mocks: a level whose registry holds the type, and a block atlas that has
 * the type's sprite stitched with two pixels of deep brown.
 */
class DrinkSkinColorTest {

    /** Rock's bundled highlight, a pale tan accent, which the skin read as the goo's color. */
    private static final int ROCK_HIGHLIGHT = 0xD1B777;
    /** Two pixels of rock's fluid sprite, a dark and a lighter brown. */
    private static final int[] ROCK_FLUID = {0xFF6E3410, 0xFF8E4418};
    /** Their mean: the deep brown rock goo is. */
    private static final int ROCK_BROWN = 0xFF7E3C14;
    /** Stone's sampled color, about its particle sprite's mean. */
    private static final int STONE = 0xFF7F7F7F;
    /** Each channel halfway from stone's to rock's brown, rounded half up: 7F to 7E, 7F to 3C, 7F to 14. */
    private static final int HALFWAY_STONE_TO_ROCK = 0xFF7F5E4A;
    private static final Identifier ROCK_SPRITE = Identifier.parse("goo:fluid/rock_fluid");
    private static final Identifier BLAZE_SPRITE = Identifier.parse("goo:fluid/blaze_fluid");
    /** Blaze's fluid, one flat orange. */
    private static final int BLAZE_ORANGE = 0xFFFF7A10;
    /** Three parts rock's brown to one part blaze's orange, each channel rounded. */
    private static final int THREE_ROCK_TO_ONE_BLAZE = 0xFF9E4C13;

    /** decision diagnose-then-fix-unmake-metallic-color */
    @Test
    void aTexturedTypesGooColorIsItsFluidSpritesMeanNotItsHighlight() {
        try (MockedStatic<Minecraft> client = clientWithRock()) {
            int color = DrinkRenderer.gooColorOf(MingledGoo.of(Map.of(GooTypes.ROCK, 1000)));

            assertEquals(ROCK_BROWN, color,
                "rock reaches the hand as " + Integer.toHexString(color) + ", not its fluid's deep brown");
        }
    }

    /** decision diagnose-then-fix-unmake-metallic-color */
    @Test
    void aTexturedTypesSkinCrossfadesFromTheBlocksColorToItsFluidsColorAlongTheRoute() {
        try (MockedStatic<Minecraft> client = clientWithRock()) {
            int goo = DrinkRenderer.gooColorOf(MingledGoo.of(Map.of(GooTypes.ROCK, 1000)));

            assertEquals(STONE, DrinkColors.skinAt(STONE, goo, 0), "the block's own color at the block");
            assertEquals(HALFWAY_STONE_TO_ROCK, DrinkColors.skinAt(STONE, goo, 0.5),
                    "halfway between the block's color and rock's brown halfway along the route");
            assertEquals(ROCK_BROWN, DrinkColors.skinAt(STONE, goo, 1), "rock's own brown at the hand");
        }
    }

    /** decision diagnose-then-fix-unmake-metallic-color */
    @Test
    void aBlockOfSeveralTypesReachesTheHandInTheirFluidColorsBlendedByTheirShares() {
        try (MockedStatic<Minecraft> client = clientWithRock()) {
            int color = DrinkRenderer.gooColorOf(MingledGoo.of(Map.of(GooTypes.ROCK, 750, GooTypes.BLAZE, 250)));

            assertEquals(THREE_ROCK_TO_ONE_BLAZE, color, "three quarters rock's brown and a quarter blaze's orange");
        }
    }

    private static MockedStatic<Minecraft> clientWithRock() {
        Minecraft minecraft = mock(Minecraft.class, RETURNS_DEEP_STUBS);
        minecraft.level = levelHoldingRock();
        TextureAtlas atlas = minecraft.getAtlasManager().getAtlasOrThrow(any());
        TextureAtlasSprite rock = spriteOf(ROCK_FLUID);
        TextureAtlasSprite blaze = spriteOf(BLAZE_ORANGE);
        when(atlas.getSprite(any())).thenAnswer(call -> BLAZE_SPRITE.equals(call.getArgument(0)) ? blaze : rock);
        when(atlas.missingSprite()).thenReturn(mock(TextureAtlasSprite.class));
        MockedStatic<Minecraft> client = mockStatic(Minecraft.class);
        client.when(Minecraft::getInstance).thenReturn(minecraft);
        return client;
    }

    private static TextureAtlasSprite spriteOf(int... pixels) {
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class, RETURNS_DEEP_STUBS);
        NativeImage image = mock(NativeImage.class);
        when(image.getWidth()).thenReturn(pixels.length);
        when(image.getHeight()).thenReturn(1);
        for (int x = 0; x < pixels.length; x++) {
            when(image.getPixel(x, 0)).thenReturn(pixels[x]);
        }
        when(sprite.contents().getOriginalImage()).thenReturn(image);
        return sprite;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ClientLevel levelHoldingRock() {
        Holder.Reference rock = holderOf(ROCK_SPRITE);
        Holder.Reference blaze = holderOf(BLAZE_SPRITE);
        ClientLevel level = mock(ClientLevel.class, RETURNS_DEEP_STUBS);
        when(level.registryAccess().get(GooTypes.ROCK)).thenReturn((Optional) Optional.of(rock));
        when(level.registryAccess().get(GooTypes.BLAZE)).thenReturn((Optional) Optional.of(blaze));
        return level;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Holder.Reference holderOf(Identifier fluid) {
        GooTypeDefinition type = mock(GooTypeDefinition.class);
        GooTypeTextures textures = mock(GooTypeTextures.class);
        when(textures.fluidStill()).thenReturn(Optional.of(fluid));
        when(textures.fluidFlowing()).thenReturn(Optional.empty());
        when(type.textures()).thenReturn(textures);
        when(type.highlight()).thenReturn(ROCK_HIGHLIGHT);
        Holder.Reference holder = mock(Holder.Reference.class);
        when(holder.value()).thenReturn(type);
        return holder;
    }
}
