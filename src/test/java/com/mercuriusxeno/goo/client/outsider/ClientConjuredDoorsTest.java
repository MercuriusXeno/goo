package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.ConjuredDoor;
import com.mercuriusxeno.goo.network.ConjuredDoorPayload;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Covers the client drawing a conjured door: it grows smoothly between the
 * updates the server sends, and once they stop, its channel let go, it
 * collapses to nothing over a few ticks and is gone
 * (decision channeling-to-the-pocket-conjures-a-door-that-envelops-the-player).
 */
class ClientConjuredDoorsTest {

    private static final int CASTER = 7;
    private static final Vec3 FEET = new Vec3(3.5, 64, 8.5);
    private static final int CHARGE = 20;
    private static final double EXACT = 1.0E-9;

    @AfterEach
    void forgetTheDoors() {
        ClientConjuredDoors.forget();
    }

    @Test
    void aDoorGrowsBetweenUpdatesThenCollapsesOnceTheyStop() {
        ConjuredDoor fourth = ConjuredDoor.standing(FEET, 0f, 4, CHARGE);
        ConjuredDoor fifth = ConjuredDoor.standing(FEET, 0f, 5, CHARGE);
        ClientConjuredDoors.take(new ConjuredDoorPayload(CASTER, fourth), 0);
        ClientConjuredDoors.take(new ConjuredDoorPayload(CASTER, fifth), 1);

        assertEquals((fourth.size() + fifth.size()) / 2, ClientConjuredDoors.drawnAt(1.5).get(CASTER).size(), EXACT,
                "halfway between its updates");
        double collapsing = 1 + ClientConjuredDoors.OUTLASTS + ClientConjuredDoors.COLLAPSE_TICKS / 2.0;
        assertEquals(fifth.size() / 2, ClientConjuredDoors.drawnAt(collapsing).get(CASTER).size(), EXACT,
                "half collapsed");
        double gone = 1 + ClientConjuredDoors.OUTLASTS + ClientConjuredDoors.COLLAPSE_TICKS + 1;
        assertFalse(ClientConjuredDoors.drawnAt(gone).containsKey(CASTER), "collapsed and gone");
    }
}
