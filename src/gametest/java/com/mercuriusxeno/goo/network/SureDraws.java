package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.program.TendingRandom;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

/**
 * A random source whose every draw is zero, the draw each of vanilla's
 * growth chances lands under, for a gametest holding a stream's tick so a
 * plant grows on every random tick the stream deals it.
 * gametests-waiting-on-a-random-draw-are-found-and-made-deterministic
 */
final class SureDraws extends LegacyRandomSource {

    SureDraws() {
        super(0);
    }

    @Override
    public int next(int bits) {
        return 0;
    }

    @Override
    public double nextGaussian() {
        return 0;
    }

    /**
     * Runs one held tick of a stream with the plants it tends drawing on
     * sure draws, the level's own random source standing again after it.
     *
     * @param player the streaming player
     * @param tick   the stream's payload
     */
    static void streamTick(ServerPlayer player, GooStreamPayload tick) {
        Runnable release = TendingRandom.hold(new SureDraws());
        try {
            GooStreamHandler.streamTick(player, tick);
        } finally {
            release.run();
        }
    }
}
