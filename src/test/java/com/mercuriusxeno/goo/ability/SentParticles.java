package com.mercuriusxeno.goo.ability;

import net.minecraft.SharedConstants;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.loading.FMLLoader;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import java.util.List;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

/**
 * Reads the particles layer visuals send to a mocked ServerLevel.
 */
final class SentParticles {

    private SentParticles() {
    }

    /**
     * Stands the built-in registries that ServerLevel's and ParticleTypes' class init read;
     * the vanilla bootstrap asks FML whether it runs in production, which a stubbed loader answers.
     */
    static void standRegistries() {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    /**
     * The options of every sendParticles call the level took, in call order.
     *
     * @param level the mocked level
     * @return the sent options
     */
    static List<ParticleOptions> of(ServerLevel level) {
        ArgumentCaptor<ParticleOptions> sent = ArgumentCaptor.forClass(ParticleOptions.class);
        verify(level, atLeast(0)).sendParticles(sent.capture(), anyDouble(), anyDouble(), anyDouble(),
                anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        return sent.getAllValues();
    }
}
