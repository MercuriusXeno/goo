package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.ListenerSoundPayload;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
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
        resolved(cue.sound()).ifPresent(holder -> level.playSound(except, at.x(), at.y(), at.z(), holder,
                sourceOf(cue.source()), cue.volume(), cue.pitch()));
    }

    /**
     * Plays the cue at a point beside a player's head: that player's client
     * plays it at the listener, unpanned and unattenuated, and everyone else in
     * range hears it at the point. A sound packet rounds its point to an
     * eighth of a block, so a source placed at the hearer's own head sits a
     * fraction of a block off the ears and pans wholly to one side.
     * diagnose-and-correct-the-spray-squelch-playing-in-one-ear
     * goo-sound-at-the-listener-reaches-both-ears
     *
     * @param level    the level everyone else hears it in
     * @param listener the player at whose head the sound plays
     * @param at       the point everyone else hears it at
     * @param cue      the evaluated sound
     */
    public static void playAtListener(ServerLevel level, ServerPlayer listener, Vec3 at, SoundCue cue) {
        ListenerSoundPayload atEars = new ListenerSoundPayload(cue);
        // a connection that never negotiated the payload hears the positioned play rather than nothing
        boolean hearsAtEars = listener.connection.hasChannel(atEars);
        resolved(cue.sound()).ifPresent(holder -> {
            level.playSound(hearsAtEars ? listener : null, at.x(), at.y(), at.z(), holder, sourceOf(cue.source()),
                    cue.volume(), cue.pitch());
            if (hearsAtEars) {
                PacketDistributor.sendToPlayer(listener, atEars);
            }
        });
    }

    /**
     * Plays the cue at a point on or beside a body: at the listener for a
     * player, as {@link #playAtListener} plays it, and at the point for
     * everyone where the body is no player.
     * goo-sound-at-the-listener-reaches-both-ears
     *
     * @param level the level to play in
     * @param body  the body the sound sits on or beside
     * @param at    the point the sound plays at
     * @param cue   the evaluated sound
     */
    public static void playAtHeadOf(ServerLevel level, Entity body, Vec3 at, SoundCue cue) {
        if (body instanceof ServerPlayer player) {
            playAtListener(level, player, at, cue);
        } else {
            play(level, at, cue);
        }
    }

    /**
     * The sound an id names, logging an id no registry holds.
     *
     * @param id the sound's id
     * @return the sound, or none where it is unknown
     */
    private static Optional<Holder<SoundEvent>> resolved(Identifier id) {
        Optional<Holder<SoundEvent>> holder = holderOf(id);
        if (holder.isEmpty()) {
            Goo.LOGGER.warn(LOG_UNKNOWN_SOUND, id);
        }
        return holder;
    }

    /**
     * Plays the cue at a point on this client alone, as a client draws a
     * visual the server sent it.
     *
     * @param level the client's level
     * @param at    the point the sound plays at
     * @param cue   the sound
     */
    public static void playLocally(Level level, Vec3 at, SoundCue cue) {
        resolved(cue.sound()).ifPresent(holder -> level.playLocalSound(at.x(), at.y(), at.z(), holder.value(),
                sourceOf(cue.source()), cue.volume(), cue.pitch(), false));
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
    public static SoundSource sourceOf(SoundKind kind) {
        return switch (kind) {
            case BLOCKS -> SoundSource.BLOCKS;
            case HOSTILE -> SoundSource.HOSTILE;
            case PLAYERS -> SoundSource.PLAYERS;
        };
    }
}
