package com.mercuriusxeno.goo.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A spray drip's provider makes a glove mote, so the stream's client swings
 * it with its caster's glove at launch.
 * glove-stream-motes-swing-with-the-glove-at-launch
 */
class SprayDripParticleTest {

    @Test
    void theSprayDripsProviderMakesAGloveMote() {
        SpriteSet sprites = mock(SpriteSet.class);
        when(sprites.get(anyInt(), anyInt())).thenReturn(mock(TextureAtlasSprite.class));
        ClientLevel level = mock(ClientLevel.class, RETURNS_DEEP_STUBS);
        SprayDripParticle.Provider provider = new SprayDripParticle.Provider(sprites, BlendPalette.GOOP_SLIME_GREENS);

        assertInstanceOf(GloveMote.class, provider.createParticle(mock(SimpleParticleType.class), level,
                0.3, 1.3, 0.2, 0.17, -0.13, 0.58, RandomSource.create(1L)));
    }
}
