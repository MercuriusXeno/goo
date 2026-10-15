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
