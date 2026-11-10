package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.function.Function;

/**
 * The world side of a {@link SoundStep}, shared by every host that plays
 * a sound: resolves the id and the category, and logs and skips an id
 * the registry lacks.
 */
public final class SoundPlays {

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
    public static void play(ServerLevel level, Vec3 at, SoundCue cue) {
        playExcept(level, null, at, cue);
    }

    /**
     * Plays the cue at a point for everyone in range but one entity.
     *
     * @param level  the level to play in
     * @param except the entity left out, or null for none
     * @param at     the point the sound plays at
     * @param cue    the evaluated sound
     */
    public static void playExcept(ServerLevel level, @Nullable Entity except, Vec3 at, SoundCue cue) {
        Optional<Holder<SoundEvent>> holder = holderOf(cue.sound());
        if (holder.isEmpty()) {
            Goo.LOGGER.warn(LOG_UNKNOWN_SOUND, cue.sound());
            return;
        }
        level.playSound(except, at.x(), at.y(), at.z(), holder.get(), source(cue.source()), cue.volume(),
                cue.pitch());
    }

    /**
     * The sound an id names: the registry's, or for an id of the mod's own
     * the registry lacks, the sound by its id alone, which the client plays
     * from goo's sounds.json as vanilla's playsound command plays any id.
     *
     * @param id the sound's id
     * @return the sound, or none for another namespace's id the registry lacks
     */
    private static Optional<Holder<SoundEvent>> holderOf(Identifier id) {
        Optional<Holder<SoundEvent>> registered = BuiltInRegistries.SOUND_EVENT.get(id).map(Function.identity());
        if (registered.isPresent() || !Goo.MODID.equals(id.getNamespace())) {
            return registered;
        }
        return Optional.of(Holder.direct(SoundEvent.createVariableRangeEvent(id)));
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
