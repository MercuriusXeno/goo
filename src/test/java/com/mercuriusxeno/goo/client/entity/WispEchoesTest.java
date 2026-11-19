package com.mercuriusxeno.goo.client.entity;

import com.mercuriusxeno.goo.client.entity.WispEchoes.Echo;
import com.mercuriusxeno.goo.entity.DriftingWisp;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The train of fading copies a beaming wisp's mote leaves on its straight
 * path: behind the mote and fainter than it while it flies, fading from the
 * goo end first once it stands on its spot.
 */
class WispEchoesTest {

    private static final Vec3 GOO = Vec3.atCenterOf(BlockPos.ZERO);
    private static final Vec3 SPOT = Vec3.atCenterOf(new BlockPos(10, 3, 3));
    private static final float MOTE_STRENGTH = 1f;
    private static final double ON_THE_LINE = 1e-6;

    @Nested
    class MidFlight {

        private final float midFlight = DriftingWisp.ARRIVAL_TICKS / 2f;
        private final Vec3 mote = GOO.lerp(SPOT, 0.5);
        private final List<Echo> train = WispEchoes.train(GOO, SPOT, midFlight);

        @Test
        void everyCopyStandsBetweenTheGooAndTheMote() {
            assertFalse(train.isEmpty(), "no copy stood behind the mote at mid flight");
            assertAll(train.stream().map(echo -> () -> {
                double off = GOO.distanceTo(echo.position()) + echo.position().distanceTo(mote) - GOO.distanceTo(mote);
                assertTrue(off < ON_THE_LINE, "a copy stood off the line from the goo to the mote, at "
                        + echo.position());
            }));
        }

        @Test
        void opacityFallsFromTheMoteTowardTheGoo() {
            assertFalse(train.isEmpty(), "no copy stood behind the mote at mid flight");
            for (int echo = 1; echo < train.size(); echo++) {
                Echo nearerTheGoo = train.get(echo - 1);
                Echo nearerTheMote = train.get(echo);
                assertTrue(GOO.distanceTo(nearerTheGoo.position()) < GOO.distanceTo(nearerTheMote.position()),
                        "copies run from the goo toward the mote");
                assertTrue(nearerTheGoo.strength() < nearerTheMote.strength(),
                        "copy " + echo + " read " + nearerTheMote.strength() + " beside " + nearerTheGoo.strength());
            }
            assertTrue(train.getLast().strength() < MOTE_STRENGTH, "the copy nearest the mote read as whole as it");
        }
    }

    @Nested
    class AfterArrival {

        @Test
        void theGooEndFadesOutFirstAndTheSpotEndLast() {
            int oldestGoneAt = -1;
            int newestGoneAt = -1;
            int lastTick = (int) Math.ceil(WispEchoes.lastTick());
            for (int tick = DriftingWisp.ARRIVAL_TICKS; tick <= lastTick; tick++) {
                List<Echo> train = WispEchoes.train(GOO, SPOT, tick);
                if (oldestGoneAt < 0 && train.getFirst().strength() <= 0) {
                    oldestGoneAt = tick;
                }
                if (newestGoneAt < 0 && train.getLast().strength() <= 0) {
                    newestGoneAt = tick;
                }
            }
            assertTrue(oldestGoneAt > DriftingWisp.ARRIVAL_TICKS, "the oldest copy was gone as the wisp arrived");
            assertTrue(oldestGoneAt < newestGoneAt,
                    "the oldest copy went on tick " + oldestGoneAt + ", the newest on " + newestGoneAt);
        }

        @Test
        void everyCopyIsGoneOnTheTrainsLastTick() {
            List<Echo> train = WispEchoes.train(GOO, SPOT, WispEchoes.lastTick());
            assertFalse(train.isEmpty(), "the train held no copy to fade");
            assertAll(train.stream().map(echo -> () -> assertEquals(0f, echo.strength(),
                    "a copy at " + echo.position() + " still showed")));
        }
    }
}
