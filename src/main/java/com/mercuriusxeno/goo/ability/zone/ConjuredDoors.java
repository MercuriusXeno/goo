package com.mercuriusxeno.goo.ability.zone;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.ConjuredDoorPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.registry.GooSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The doors players channeling Zone toward the pocket, or End toward the
 * End, have conjured,
 * each kept while its channel holds: every held tick stands the door where
 * the hold has brought it and sends it to the caster and every player
 * tracking them, the first with a burst of void and the conjuring sound. A
 * door its channel stopped feeding falls a tick later, so a channel let go
 * early collapses it, and the door of a channel that carried its caster in
 * falls the same way once they are gone.
 * decision channeling-to-the-pocket-conjures-a-door-that-envelops-the-player
 * A player arriving in their room is released by a second door standing on
 * them, kept past the teleport and sent each tick until its release ticks pass.
 * decision matching-doorway-opens-in-the-room-and-releases-you
 * Each door carries its look, so Tartarus' gate is sent drawn with the nether
 * portal's texture and bursts the nether portal's own particles.
 * decision tartarus-summons-a-gate-to-the-nether-like-end-and-zone
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class ConjuredDoors {

    /** Server ticks a door outlasts the last tick its channel stood it, so one late tick drops no door. */
    private static final int OUTLASTS = 1;
    private static final int CONJURE_PARTICLES = 30;
    private static final double CONJURE_SPREAD = 0.3;
    /** How far up and down the burst spreads: over the door's height. */
    private static final double CONJURE_TALL = 0.6;
    private static final double CONJURE_SPEED = 0.4;
    private static final double DOOR_MIDDLE = 1.0;
    private static final float CONJURE_VOLUME = 0.8f;
    /** The door's sound plays where its player stands, as loud as the door's conjuring. */
    private static final float DOOR_VOLUME = 0.8f;

    /** Each caster's door and the server tick its channel last stood it. */
    private static final Map<UUID, Standing> DOORS = new ConcurrentHashMap<>();
    /** Each arriver's releasing door, kept past the teleport for its release ticks. */
    private static final Map<UUID, Release> RELEASES = new ConcurrentHashMap<>();

    private ConjuredDoors() {
    }

    /**
     * A door as its channel last stood it.
     *
     * @param door the door
     * @param tick the server tick it stood
     * @param look what the door is drawn with
     */
    private record Standing(ConjuredDoor door, int tick, GateLook look) {
    }

    /**
     * A doorway releasing a player into their room.
     *
     * @param arriver the player it releases
     * @param feet    where they arrived
     * @param yaw     the way they faced arriving, in degrees
     * @param start   the server tick it began
     * @param door    the door as it stands now
     * @param look    what the door is drawn with
     */
    private record Release(ServerPlayer arriver, Vec3 feet, float yaw, int start, ConjuredDoor door, GateLook look) {

        Release at(int now) {
            return new Release(arriver, feet, yaw, start,
                    ConjuredDoor.releasing(feet, yaw, now - start, door.standsOff()), look);
        }

        boolean overAt(int now) {
            return now - start > ConjuredDoor.RELEASE_TICKS || arriver.isRemoved();
        }
    }

    /**
     * Stands a caster's door where their hold has brought it this tick and
     * sends it to them and their watchers, conjuring it the first tick.
     *
     * @param caster the player channeling
     * @param held   the hold's tick count
     * @param charge the ticks the charge takes
     * @return true on the tick the door is first conjured
     */
    public static boolean conjure(ServerPlayer caster, int held, int charge) {
        return conjure(caster, held, charge, GateLook.STARFIELD);
    }

    /**
     * Stands a caster's door drawn with a look where their hold has brought
     * it this tick and sends it to them and their watchers, conjuring it the first tick.
     *
     * @param caster the player channeling
     * @param held   the hold's tick count
     * @param charge the ticks the charge takes
     * @param look   what the door is drawn with
     * @return true on the tick the door is first conjured
     */
    public static boolean conjure(ServerPlayer caster, int held, int charge, GateLook look) {
        ServerLevel level = caster.level();
        ConjuredDoor door = ConjuredDoor.standing(caster.position(), caster.getYRot(), held, charge,
                standsOffFor(caster, look));
        Standing before = DOORS.put(caster.getUUID(), new Standing(door, level.getServer().getTickCount(), look));
        if (before == null) {
            showConjuring(level, door, look);
        }
        sendDoor(caster, door, look);
        return before == null;
    }

    /**
     * Sends the door's own sound to the player it carries, where they stand,
     * on their own connection, so the level change cuts no sound a level
     * played: once as the door envelops them, once as they arrive.
     * decision door-carries-its-own-sound-through-the-change
     *
     * @param player the player the door carries
     */
    public static void sendDoorSound(ServerPlayer player) {
        player.connection.send(new ClientboundSoundPacket(GooSounds.ZONE_DOOR, SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(), DOOR_VOLUME, 1f, player.getRandom().nextLong()));
    }

    /**
     * Stands the doorway releasing a player who has just arrived in their
     * room, on them at full size, and sends it to them and their watchers.
     * decision matching-doorway-opens-in-the-room-and-releases-you
     *
     * @param arriver the player standing on their arrival cell
     */
    public static void release(ServerPlayer arriver) {
        release(arriver, GateLook.STARFIELD);
    }

    /**
     * Stands the doorway drawn with a look releasing a player who has just
     * arrived, on them at full size, and sends it to them and their watchers.
     *
     * @param arriver the arriving player
     * @param look    what the door is drawn with
     */
    public static void release(ServerPlayer arriver, GateLook look) {
        releaseAt(arriver, arriver.level().getServer().getTickCount(), look);
    }

    /**
     * Stands a release begun at a server tick and sends its first door.
     *
     * @param arriver the arriving player
     * @param now     the server tick the release begins
     */
    static void releaseAt(ServerPlayer arriver, int now) {
        releaseAt(arriver, now, GateLook.STARFIELD);
    }

    private static void releaseAt(ServerPlayer arriver, int now, GateLook look) {
        Release release = new Release(arriver, arriver.position(), arriver.getYRot(), now,
                ConjuredDoor.releasing(arriver.position(), arriver.getYRot(), 0, standsOffFor(arriver, look)), look);
        RELEASES.put(arriver.getUUID(), release);
        sendDoor(arriver, release.door(), look);
    }

    /**
     * How far before a player their door stands: Zone's and End's their look's
     * own distance, Tartarus' doorway as far as the space ahead is clear, up to its own.
     * decision tartarus-draws-its-own-portal-with-the-nether-portal-texture
     *
     * @param player the player the door stands before
     * @param look   what the door is drawn with
     * @return the stand-off, in blocks
     */
    private static double standsOffFor(ServerPlayer player, GateLook look) {
        if (look == GateLook.STARFIELD) {
            return look.standsOff();
        }
        ServerLevel level = player.level();
        return DoorStandOff.clearAhead(pos -> !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty(),
                player.position(), player.getYRot(), look.standsOff());
    }

    /**
     * Sends a caster's door to the caster and every player tracking them.
     *
     * @param caster the caster
     * @param door   the door
     * @param look   what the door is drawn with
     */
    static void sendDoor(ServerPlayer caster, ConjuredDoor door, GateLook look) {
        EntityVisuals.sendToWatchers(caster, new ConjuredDoorPayload(caster.getId(), door, look));
    }

    /**
     * Bursts void where a starfield door is conjured, and plays the conjuring
     * sound for every door; Tartarus' doorway gives off its own slow motes instead.
     * decision tartarus-draws-its-own-portal-with-the-nether-portal-texture
     *
     * @param level the caster's level
     * @param door  the door, at its start
     * @param look  what the door is drawn with
     */
    private static void showConjuring(ServerLevel level, ConjuredDoor door, GateLook look) {
        Vec3 middle = door.at().add(0, DOOR_MIDDLE, 0);
        if (look == GateLook.STARFIELD) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, middle.x, middle.y, middle.z, CONJURE_PARTICLES,
                    CONJURE_SPREAD, CONJURE_TALL, CONJURE_SPREAD, CONJURE_SPEED);
        }
        level.playSound(null, middle.x, middle.y, middle.z, GooSounds.ZONE_CONJURE.get(), SoundSource.PLAYERS,
                CONJURE_VOLUME, 1f);
    }

    /**
     * The door a caster's channel stands now.
     *
     * @param caster the caster's id
     * @return the door, empty while no channel toward the pocket holds
     */
    public static Optional<ConjuredDoor> doorOf(UUID caster) {
        return Optional.ofNullable(DOORS.get(caster)).map(Standing::door);
    }

    /**
     * The door releasing a player into their room now.
     *
     * @param arriver the player's id
     * @return the door, empty once no release stands
     */
    public static Optional<ConjuredDoor> releaseOf(UUID arriver) {
        return Optional.ofNullable(RELEASES.get(arriver)).map(Release::door);
    }

    /**
     * Drops every door its channel stopped standing, and moves each release
     * on a tick, sending it, until its release ticks pass.
     *
     * @param event the post-tick event
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int now = event.getServer().getTickCount();
        DOORS.values().removeIf(standing -> now - standing.tick() > OUTLASTS);
        moveReleasesAt(now);
    }

    /**
     * Drops each release whose ticks have passed, and moves the rest on to a
     * server tick, sending each.
     *
     * @param now the server tick
     */
    static void moveReleasesAt(int now) {
        RELEASES.values().removeIf(release -> release.overAt(now));
        RELEASES.replaceAll((arriver, release) -> {
            Release moved = release.at(now);
            if (now > release.start()) {
                sendDoor(moved.arriver(), moved.door(), moved.look());
            }
            return moved;
        });
    }
}
