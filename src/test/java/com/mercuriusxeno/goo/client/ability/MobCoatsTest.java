package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static com.mercuriusxeno.goo.client.ability.MobCoats.COAT_HOLD_TICKS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A struck mob's splat holds for the hold from its latest hit, a repeat hit
 * replaces it at the new hit point and restarts it, and a disconnect clears
 * every splat.
 */
class MobCoatsTest {

    private static final int MOB = 7;
    private static final long HIT_TICK = 1000;
    private static final Vec3 FIRST_HIT = new Vec3(1, 2, 3);
    private static final Vec3 SECOND_HIT = new Vec3(1, 1.5, 3);

    @Nested
    class Hold {

        @Test
        void holdIsAboutThreeSeconds() {
            assertEquals(60, COAT_HOLD_TICKS);
        }

        @Test
        void splatStandsFromTheHitThroughTheHoldsLastTick() {
            MobCoats coats = new MobCoats();
            coats.coat(MOB, GooTypes.BLAZE, HIT_TICK, FIRST_HIT);

            MobCoats.Coat coat = coats.coatOf(MOB, HIT_TICK);
            assertEquals(GooTypes.BLAZE, coat.gooType());
            assertEquals(FIRST_HIT, coat.hitPoint());
            assertNotNull(coats.coatOf(MOB, HIT_TICK + COAT_HOLD_TICKS - 1));
        }

        @Test
        void splatIsGoneAtTheHoldsEnd() {
            MobCoats coats = new MobCoats();
            coats.coat(MOB, GooTypes.BLAZE, HIT_TICK, FIRST_HIT);

            assertNull(coats.coatOf(MOB, HIT_TICK + COAT_HOLD_TICKS));
        }

        @Test
        void unstruckMobWearsNoSplat() {
            assertNull(new MobCoats().coatOf(MOB, HIT_TICK));
        }
    }

    @Nested
    class Refresh {

        @Test
        void repeatHitMidHoldStandsAFullHoldAtTheNewPoint() {
            MobCoats coats = new MobCoats();
            long repeat = HIT_TICK + COAT_HOLD_TICKS / 2;
            coats.coat(MOB, GooTypes.BLAZE, HIT_TICK, FIRST_HIT);
            coats.coat(MOB, GooTypes.BLAZE, repeat, SECOND_HIT);

            assertEquals(SECOND_HIT, coats.coatOf(MOB, repeat + COAT_HOLD_TICKS - 1).hitPoint());
            assertNull(coats.coatOf(MOB, repeat + COAT_HOLD_TICKS));
        }

        @Test
        void repeatHitOfAnotherTypeSplatsInIt() {
            MobCoats coats = new MobCoats();
            coats.coat(MOB, GooTypes.BLAZE, HIT_TICK, FIRST_HIT);
            coats.coat(MOB, GooTypes.FROST, HIT_TICK + 1, FIRST_HIT);

            assertEquals(GooTypes.FROST, coats.coatOf(MOB, HIT_TICK + 1).gooType());
        }
    }

    @Nested
    class Clear {

        @Test
        void clearDropsEverySplat() {
            MobCoats coats = new MobCoats();
            coats.coat(MOB, GooTypes.BLAZE, HIT_TICK, FIRST_HIT);
            coats.coat(MOB + 1, GooTypes.BLAZE, HIT_TICK, FIRST_HIT);

            coats.clear();

            assertNull(coats.coatOf(MOB, HIT_TICK));
            assertNull(coats.coatOf(MOB + 1, HIT_TICK));
        }
    }
}
