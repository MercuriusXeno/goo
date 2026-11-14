package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.ability.zone.ConjuredDoor;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the starfield cover's life on the entering client: it holds from
 * the moment the local caster's door envelops them, through the level
 * change, until the room reads ready; another caster's door covers nothing
 * (decision doorway-starfield-fills-the-view-until-the-room-is-ready).
 */
class EntryCoverTest {

    private static final int LOCAL = 7;
    private static final int OTHER = 9;
    private static final Vec3 FEET = new Vec3(3.5, 64, 8.5);
    private static final int CHARGE = 20;
    private static final ConjuredDoor GROWING = ConjuredDoor.standing(FEET, 0f, CHARGE / 2, CHARGE);
    private static final ConjuredDoor ENVELOPED = ConjuredDoor.standing(FEET, 0f, CHARGE, CHARGE);

    @Test
    void coversFromTheEnvelopThroughTheChangeUntilTheRoomIsReady() {
        EntryCover cover = new EntryCover();

        cover.doorSeen(LOCAL, LOCAL, GROWING);
        assertFalse(cover.covering(), "a growing door covers nothing yet");
        cover.doorSeen(LOCAL, LOCAL, ENVELOPED);
        assertTrue(cover.covering(), "covering once the door envelops the caster");
        cover.levelChanged();
        for (int tick = 0; tick < EntryCover.WAITS_FOR_CHANGE * 2; tick++) {
            cover.tick();
        }
        assertTrue(cover.covering(), "still covering after the change while the room is not ready");
        cover.clear();
        assertFalse(cover.covering(), "clear once the room reads ready");
    }

    @Test
    void anotherCastersEnvelopCoversNothing() {
        EntryCover cover = new EntryCover();

        cover.doorSeen(OTHER, LOCAL, ENVELOPED);

        assertFalse(cover.covering());
    }

    @Test
    void anEnvelopNoChangeFollowsClearsOnItsOwn() {
        EntryCover cover = new EntryCover();
        cover.doorSeen(LOCAL, LOCAL, ENVELOPED);

        for (int tick = 0; tick < EntryCover.WAITS_FOR_CHANGE; tick++) {
            cover.tick();
        }

        assertFalse(cover.covering(), "an entry the server refused leaves no cover standing");
    }
}
