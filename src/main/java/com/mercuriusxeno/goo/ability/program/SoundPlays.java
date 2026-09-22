package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * The world side of a {@link SoundStep}, shared by every host that plays
 * a sound: resolves the id and the category, and logs and skips an id
 * the registry lacks.
 */
final class SoundPlays {

    private static final String LOG_UNKNOWN_SOUND = "Sound step names {}, which no registry holds";

    private SoundPlays() {
    }

    /**
     * Plays the cue at a point for everyone in range.
     *
     * @param level the level to play in
     * @param at    the point the sound plays at
     * @param cue   the evaluated sound
     */
    static void play(ServerLevel level, Vec3 at, SoundCue cue) {
        Optional<Holder.Reference<SoundEvent>> holder = BuiltInRegistries.SOUND_EVENT.get(cue.sound());
        if (holder.isEmpty()) {
            Goo.LOGGER.warn(LOG_UNKNOWN_SOUND, cue.sound());
            return;
        }
        level.playSound(null, at.x(), at.y(), at.z(), holder.get(), source(cue.source()), cue.volume(), cue.pitch());
    }

    /**
     * Maps a sound kind to the level's sound source.
     *
     * @param kind the kind the step named
     * @return the sound source
     */
    private static SoundSource source(SoundKind kind) {
        return switch (kind) {
            case BLOCKS -> SoundSource.BLOCKS;
            case HOSTILE -> SoundSource.HOSTILE;
            case PLAYERS -> SoundSource.PLAYERS;
        };
    }
}
