package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypeTextures;
import com.mercuriusxeno.goo.type.GooTypes;
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
 * the type's color, for a type that wears its own named fluid sprite as every
 * bundled type does (decision unmake-skin-is-a-solid-sampled-color). The
 * client is stood up by mocks: a level whose registry holds the type, and a
 * block atlas that has the type's sprite stitched.
 */
class DrinkSkinColorTest {

    /** Blaze's bundled highlight color, its one color. */
    private static final int BLAZE_COLOR = 0xFF750F;
    /** Stone's sampled color, about its particle sprite's mean. */
    private static final int STONE = 0xFF7F7F7F;
    /** Each channel halfway from stone's to blaze's, rounded: 0xFF to 0x7F, 0x75 to 0x7F, 0x0F to 0x7F. */
    private static final int HALFWAY_STONE_TO_BLAZE = 0xBF7A47;

    @Test
    void aTexturedTypesGooColorIsTheTypesOwnColor() {
        Minecraft minecraft = mock(Minecraft.class, RETURNS_DEEP_STUBS);
        minecraft.level = levelHoldingBlaze();
        TextureAtlas atlas = minecraft.getAtlasManager().getAtlasOrThrow(any());
        when(atlas.getSprite(any())).thenReturn(mock(TextureAtlasSprite.class));
        when(atlas.missingSprite()).thenReturn(mock(TextureAtlasSprite.class));
        try (MockedStatic<Minecraft> client = mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(minecraft);

            int color = DrinkRenderer.gooColorOf(MingledGoo.of(Map.of(GooTypes.BLAZE, 1000)));

            assertEquals(0xFF000000 | BLAZE_COLOR, color,
                "blaze reaches the hand as " + Integer.toHexString(color) + ", not its own color");
        }
    }

    /** decision diagnose-then-fix-unmake-metallic-color */
    @Test
    void aTexturedTypesSkinCrossfadesFromTheBlocksColorToItsOwnColorAlongTheRoute() {
        Minecraft minecraft = mock(Minecraft.class, RETURNS_DEEP_STUBS);
        minecraft.level = levelHoldingBlaze();
        TextureAtlas atlas = minecraft.getAtlasManager().getAtlasOrThrow(any());
        when(atlas.getSprite(any())).thenReturn(mock(TextureAtlasSprite.class));
        when(atlas.missingSprite()).thenReturn(mock(TextureAtlasSprite.class));
        try (MockedStatic<Minecraft> client = mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(minecraft);
            int goo = DrinkRenderer.gooColorOf(MingledGoo.of(Map.of(GooTypes.BLAZE, 1000)));

            assertEquals(STONE, DrinkColors.skinAt(STONE, goo, 0), "the block's own color at the block");
            assertEquals(0xFF000000 | HALFWAY_STONE_TO_BLAZE, DrinkColors.skinAt(STONE, goo, 0.5),
                    "halfway between the block's and blaze's colors halfway along the route");
            assertEquals(0xFF000000 | BLAZE_COLOR, DrinkColors.skinAt(STONE, goo, 1),
                    "blaze's own color at the hand, not its fluid sprite's white tint");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ClientLevel levelHoldingBlaze() {
        GooTypeDefinition blaze = mock(GooTypeDefinition.class);
        GooTypeTextures textures = mock(GooTypeTextures.class);
        when(textures.fluidStill()).thenReturn(Optional.of(Identifier.parse("goo:fluid/blaze_fluid")));
        when(textures.fluidFlowing()).thenReturn(Optional.empty());
        when(blaze.textures()).thenReturn(textures);
        when(blaze.highlight()).thenReturn(BLAZE_COLOR);
        Holder.Reference holder = mock(Holder.Reference.class);
        when(holder.value()).thenReturn(blaze);
        ClientLevel level = mock(ClientLevel.class, RETURNS_DEEP_STUBS);
        when(level.registryAccess().get(GooTypes.BLAZE)).thenReturn((Optional) Optional.of(holder));
        return level;
    }
}
