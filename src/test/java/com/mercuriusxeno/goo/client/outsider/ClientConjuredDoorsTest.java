package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.ConjuredDoor;
import com.mercuriusxeno.goo.network.ConjuredDoorPayload;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Covers the client drawing a conjured door: it grows smoothly between the
 * updates the server sends, and once they stop, its channel let go, it
 * collapses to nothing over a few ticks and is gone
 * (decision channeling-to-the-pocket-conjures-a-door-that-envelops-the-player);
 * the local caster's door follows the look of the frame drawn, and another
 * caster's turns between updates along the shorter arc
 * (decision conjured-door-follows-the-look-smoothly).
 */
class ClientConjuredDoorsTest {

    private static final int CASTER = 7;
    private static final Vec3 FEET = new Vec3(3.5, 64, 8.5);
    private static final int CHARGE = 20;
    private static final double EXACT = 1.0E-5;
    private static final float QUARTER_TURN_HALVED = 45f;
    private static final double FULL_TURN = 360;

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

    @Nested
    class TheLocalCastersDoor {

        @Test
        void turnsWithTheFramesLookAndStandsItsStandOffBeforeTheFeet() {
            ConjuredDoor held = ConjuredDoor.standing(FEET, 0f, 4, CHARGE);
            ClientConjuredDoors.Seen seen = new ClientConjuredDoors.Seen(held, held, 0);

            ClientConjuredDoors.Drawn first = ClientConjuredDoors.drawnAt(seen, 0.5,
                    new ClientConjuredDoors.Look(FEET, 0f)).orElseThrow();
            ClientConjuredDoors.Drawn turned = ClientConjuredDoors.drawnAt(seen, 0.5,
                    new ClientConjuredDoors.Look(FEET, QUARTER_TURN_HALVED)).orElseThrow();

            assertEquals(QUARTER_TURN_HALVED, turned.yaw() - first.yaw(), EXACT, "turned with the look");
            assertEquals(0, standOffAlong(0f).distanceTo(first.at()), EXACT, "before the feet along the first look");
            assertEquals(0, standOffAlong(QUARTER_TURN_HALVED).distanceTo(turned.at()), EXACT,
                    "before the feet along the turned look");
        }

        private static Vec3 standOffAlong(float yaw) {
            double radians = Math.toRadians(yaw);
            return FEET.add(new Vec3(-Math.sin(radians), 0, Math.cos(radians)).scale(ConjuredDoor.STANDS_OFF));
        }
    }

    @Nested
    class TheLocalArriversRelease {

        @AfterEach
        void forgetTheRelease() {
            ClientConjuredDoors.forgetRelease();
        }

        @Test
        void waitsOnTheCoverThenPlaysFromTheFrameItClears() {
            ClientConjuredDoors.takeLocalRelease(ConjuredDoor.releasing(FEET, 0f, 0, ConjuredDoor.RELEASE_TICKS));
            ClientConjuredDoors.Look look = new ClientConjuredDoors.Look(FEET, 0f);

            assertFalse(ClientConjuredDoors.releasedAt(5, look, true).isPresent(), "nothing shows under the cover");
            assertEquals(1, ClientConjuredDoors.releasedAt(40, look, false).orElseThrow().size(), EXACT,
                    "full size the frame the cover clears");
            double halfway = 40 + ConjuredDoor.RELEASE_TICKS / 2.0;
            ClientConjuredDoors.Drawn moving = ClientConjuredDoors.releasedAt(halfway, look, false).orElseThrow();
            assertEquals(0.5, moving.size(), EXACT, "half closed halfway through");
            assertEquals(ConjuredDoor.STANDS_OFF / 2, moving.at().distanceTo(FEET), EXACT, "half its stand-off off");
            assertFalse(ClientConjuredDoors.releasedAt(40 + ConjuredDoor.RELEASE_TICKS + 1, look, false).isPresent(),
                    "gone once its release ticks pass");
        }
    }

    @Nested
    class AnotherCastersDoor {

        @Test
        void turnsBetweenUpdatesAlongTheShorterArc() {
            ClientConjuredDoors.take(new ConjuredDoorPayload(CASTER, ConjuredDoor.standing(FEET, 350f, 4, CHARGE)), 0);
            ClientConjuredDoors.take(new ConjuredDoorPayload(CASTER, ConjuredDoor.standing(FEET, 10f, 5, CHARGE)), 1);

            float halfway = ClientConjuredDoors.drawnAt(1.5).get(CASTER).yaw();

            double wrapped = ((halfway % FULL_TURN) + FULL_TURN) % FULL_TURN;
            assertEquals(0, Math.min(wrapped, FULL_TURN - wrapped), EXACT, "halfway from 350 to 10 is 0");
        }
    }
}
