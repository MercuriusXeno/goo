package com.mercuriusxeno.goo.ability.outsider;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.ConjuredDoorPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.registry.GooSounds;
import net.minecraft.core.particles.ParticleTypes;
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
 * The doors players channeling Outsider toward the pocket have conjured,
 * each kept while its channel holds: every held tick stands the door where
 * the hold has brought it and sends it to the caster and every player
 * tracking them, the first with a burst of void and the conjuring sound. A
 * door its channel stopped feeding falls a tick later, so a channel let go
 * early collapses it, and the door of a channel that carried its caster in
 * falls the same way once they are gone.
 * decision channeling-to-the-pocket-conjures-a-door-that-envelops-the-player
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

    /** Each caster's door and the server tick its channel last stood it. */
    private static final Map<UUID, Standing> DOORS = new ConcurrentHashMap<>();

    private ConjuredDoors() {
    }

    /**
     * A door as its channel last stood it.
     *
     * @param door the door
     * @param tick the server tick it stood
     */
    private record Standing(ConjuredDoor door, int tick) {
    }

    /**
     * Stands a caster's door where their hold has brought it this tick and
     * sends it to them and their watchers, conjuring it the first tick.
     *
     * @param caster the player channeling toward the pocket
     * @param held   the hold's tick count
     * @param charge the ticks the charge takes
     */
    public static void conjure(ServerPlayer caster, int held, int charge) {
        ServerLevel level = caster.level();
        ConjuredDoor door = ConjuredDoor.standing(caster.position(), caster.getYRot(), held, charge);
        Standing before = DOORS.put(caster.getUUID(), new Standing(door, level.getServer().getTickCount()));
        if (before == null) {
            showConjuring(level, door);
            OutsiderRooms.loadRoomAhead(caster);
        }
        sendDoor(caster, door);
    }

    /**
     * Sends a caster's door to the caster and every player tracking them.
     *
     * @param caster the caster
     * @param door   the door
     */
    static void sendDoor(ServerPlayer caster, ConjuredDoor door) {
        EntityVisuals.sendToWatchers(caster, new ConjuredDoorPayload(caster.getId(), door));
    }

    /**
     * Bursts void where a door is conjured and plays the conjuring sound.
     *
     * @param level the caster's level
     * @param door  the door, at its start
     */
    private static void showConjuring(ServerLevel level, ConjuredDoor door) {
        Vec3 middle = door.at().add(0, DOOR_MIDDLE, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, middle.x, middle.y, middle.z, CONJURE_PARTICLES,
                CONJURE_SPREAD, CONJURE_TALL, CONJURE_SPREAD, CONJURE_SPEED);
        level.playSound(null, middle.x, middle.y, middle.z, GooSounds.OUTSIDER_CONJURE.get(), SoundSource.PLAYERS,
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
     * Drops every door its channel stopped standing.
     *
     * @param event the post-tick event
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int now = event.getServer().getTickCount();
        DOORS.values().removeIf(standing -> now - standing.tick() > OUTLASTS);
    }
}
