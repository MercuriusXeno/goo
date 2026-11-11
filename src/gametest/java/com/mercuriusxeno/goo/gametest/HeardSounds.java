package com.mercuriusxeno.goo.gametest;

import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * Reads the sounds a recorded player heard: those of one sound event, and
 * those of them played at a point, read to the eighth of a block a sound
 * packet carries its position in and the float it reads it back through.
 */
public final class HeardSounds {

    /** A sound packet carries its position in eighths of a block. */
    private static final double SOUND_GRID = 0.125;

    private HeardSounds() {
    }

    /**
     * The sounds of one event a player heard, in the order they were sent.
     *
     * @param heard the player's recorder
     * @param sound the sound event's id
     * @return the sounds
     */
    public static List<ClientboundSoundPacket> of(PacketRecorder heard, Identifier sound) {
        return heard.sentOf(ClientboundSoundPacket.class).stream().filter(packet -> packet.getSound().is(sound))
                .toList();
    }

    /**
     * How many sounds of one event a player heard played at a point.
     *
     * @param heard the player's recorder
     * @param sound the sound event's id
     * @param point the point
     * @return the count
     */
    public static long at(PacketRecorder heard, Identifier sound, Vec3 point) {
        return of(heard, sound).stream().filter(packet -> playedAt(packet, point)).count();
    }

    /**
     * Whether a sound was played at a point.
     *
     * @param packet the sound
     * @param point  the point
     * @return true where every axis lies within the packet's grid of the point
     */
    public static boolean playedAt(ClientboundSoundPacket packet, Vec3 point) {
        return nearOnAxis(packet.getX(), point.x) && nearOnAxis(packet.getY(), point.y)
                && nearOnAxis(packet.getZ(), point.z);
    }

    /** A packet reads its position back through a float, coarse this far from the origin, as gametests stand. */
    private static boolean nearOnAxis(double sent, double expected) {
        return Math.abs(sent - expected) <= SOUND_GRID + Math.ulp((float) expected);
    }

    /**
     * Where each sound was played, for a failure message.
     *
     * @param sounds the sounds
     * @return their positions
     */
    public static String describe(List<ClientboundSoundPacket> sounds) {
        return sounds.stream().map(packet -> "(" + packet.getX() + ", " + packet.getY() + ", " + packet.getZ() + ")")
                .toList().toString();
    }
}
