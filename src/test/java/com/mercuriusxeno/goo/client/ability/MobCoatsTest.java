package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static com.mercuriusxeno.goo.client.ability.MobCoats.COAT_DISSOLVE_TICKS;
import static com.mercuriusxeno.goo.client.ability.MobCoats.COAT_HOLD_TICKS;
import static com.mercuriusxeno.goo.client.ability.MobCoats.COAT_LIFE_TICKS;
import static com.mercuriusxeno.goo.client.ability.MobCoats.MAX_SPLATS_PER_MOB;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every hit adds its own splat to the struck mob, which holds whole for the
 * hold, then dissolves; a mob carries at most eight, its oldest dropping
 * first; a splat rides the mob as it moves, and a disconnect clears them all.
 */
class MobCoatsTest {

    private static final int MOB = 7;
    private static final long HIT_TICK = 1000;
    private static final MobCoats.Stance STOOD = new MobCoats.Stance(new Vec3(1, 1, 3), 0f);
    private static final Vec3 FIRST_HIT = new Vec3(1, 2, 3);
    private static final Vec3 SECOND_HIT = new Vec3(1, 1.5, 3);
    private static final Vec3 NO_AIM = Vec3.ZERO;
    private static final double EPSILON = 1e-6;

    private static MobCoats struckAt(long tick, Vec3 hit) {
        MobCoats coats = new MobCoats();
        coats.coat(MOB, GooTypes.BLAZE, tick, new MobCoats.Strike(hit, NO_AIM, STOOD));
        return coats;
    }

    private static List<Vec3> hitPointsOf(MobCoats coats, long tick) {
        return coats.coatsOf(MOB, tick).stream().map(MobCoats.Coat::hitPoint).toList();
    }

    @Nested
    class Life {

        @Test
        void holdIsAboutSixSecondsAndTheDissolveAboutThree() {
            assertEquals(120, COAT_HOLD_TICKS);
            assertEquals(60, COAT_DISSOLVE_TICKS);
        }

        @Test
        void splatStandsFromTheHitThroughTheHold() {
            MobCoats coats = struckAt(HIT_TICK, FIRST_HIT);

            MobCoats.Coat coat = coats.coatsOf(MOB, HIT_TICK).getFirst();
            assertEquals(GooTypes.BLAZE, coat.gooType());
            assertEquals(FIRST_HIT, coat.hitPoint());
            assertEquals(List.of(FIRST_HIT), hitPointsOf(coats, HIT_TICK + COAT_HOLD_TICKS - 1));
        }

        @Test
        void splatIsGoneAtTheEndOfItsLife() {
            MobCoats coats = struckAt(HIT_TICK, FIRST_HIT);

            assertEquals(List.of(FIRST_HIT), hitPointsOf(coats, HIT_TICK + COAT_LIFE_TICKS - 1));
            assertTrue(coats.coatsOf(MOB, HIT_TICK + COAT_LIFE_TICKS).isEmpty());
        }

        @Test
        void unstruckMobWearsNoSplat() {
            assertTrue(new MobCoats().coatsOf(MOB, HIT_TICK).isEmpty());
        }
    }

    @Nested
    class Dissolve {

        @Test
        void wholeThroughTheHold() {
            assertEquals(0f, MobCoats.Coat.dissolveProgress(0f));
            assertEquals(0f, MobCoats.Coat.dissolveProgress(COAT_HOLD_TICKS));
        }

        @Test
        void partlyDissolvedMidwayThroughTheDissolve() {
            float midway = MobCoats.Coat.dissolveProgress(COAT_HOLD_TICKS + COAT_DISSOLVE_TICKS / 2f);

            assertTrue(midway > 0f && midway < 1f, "midway progress " + midway);
        }

        @Test
        void fullyDissolvedAtTheEnd() {
            assertEquals(1f, MobCoats.Coat.dissolveProgress(COAT_LIFE_TICKS));
        }

        @Test
        void newHitMidDissolveAddsAWholeSplat() {
            MobCoats coats = struckAt(HIT_TICK, FIRST_HIT);
            long repeat = HIT_TICK + COAT_HOLD_TICKS + COAT_DISSOLVE_TICKS / 2;
            coats.coat(MOB, GooTypes.BLAZE, repeat, new MobCoats.Strike(SECOND_HIT, NO_AIM, STOOD));

            MobCoats.Coat fresh = coats.coatsOf(MOB, repeat).getLast();
            assertEquals(SECOND_HIT, fresh.hitPoint());
            assertEquals(0f, MobCoats.Coat.dissolveProgress(repeat - fresh.hitTick()));
        }
    }

    @Nested
    class Stacking {

        @Test
        void secondHitAddsItsOwnSplatBesideTheFirst() {
            MobCoats coats = struckAt(HIT_TICK, FIRST_HIT);
            coats.coat(MOB, GooTypes.FROST, HIT_TICK + 10, new MobCoats.Strike(SECOND_HIT, NO_AIM, STOOD));

            assertEquals(List.of(FIRST_HIT, SECOND_HIT), hitPointsOf(coats, HIT_TICK + 10));
            assertEquals(GooTypes.FROST, coats.coatsOf(MOB, HIT_TICK + 10).getLast().gooType());
        }

        @Test
        void eachSplatKeepsItsOwnLife() {
            MobCoats coats = struckAt(HIT_TICK, FIRST_HIT);
            coats.coat(MOB, GooTypes.BLAZE, HIT_TICK + 10, new MobCoats.Strike(SECOND_HIT, NO_AIM, STOOD));

            assertEquals(List.of(SECOND_HIT), hitPointsOf(coats, HIT_TICK + COAT_LIFE_TICKS));
            assertTrue(coats.coatsOf(MOB, HIT_TICK + 10 + COAT_LIFE_TICKS).isEmpty());
        }

        @Test
        void hitPastTheCapDropsTheOldest() {
            MobCoats coats = new MobCoats();
            for (int hit = 0; hit <= MAX_SPLATS_PER_MOB; hit++) {
                coats.coat(MOB, GooTypes.BLAZE, HIT_TICK + hit, new MobCoats.Strike(new Vec3(hit, 0, 0), NO_AIM, STOOD));
            }

            List<Vec3> standing = hitPointsOf(coats, HIT_TICK + MAX_SPLATS_PER_MOB);
            assertEquals(8, MAX_SPLATS_PER_MOB);
            assertEquals(MAX_SPLATS_PER_MOB, standing.size());
            assertEquals(new Vec3(1, 0, 0), standing.getFirst());
        }
    }

    @Nested
    class RidesTheMob {

        @Test
        void walkingCarriesTheSplatWithTheFeet() {
            MobCoats.Coat coat = struckAt(HIT_TICK, FIRST_HIT).coatsOf(MOB, HIT_TICK).getFirst();

            Vec3 carried = coat.hitPointOn(new MobCoats.Stance(new Vec3(4, 1, 3), 0f));

            assertEquals(new Vec3(4, 2, 3), carried);
        }

        @Test
        void turningTheBodyCarriesTheSplatAroundWithIt() {
            Vec3 frontHit = new Vec3(1, 2, 3.4);
            MobCoats.Coat coat = struckAt(HIT_TICK, frontHit).coatsOf(MOB, HIT_TICK).getFirst();

            Vec3 carried = coat.hitPointOn(new MobCoats.Stance(STOOD.feet(), 180f));

            assertEquals(1, carried.x, EPSILON);
            assertEquals(2, carried.y, EPSILON);
            assertEquals(2.6, carried.z, EPSILON);
        }
    }

    @Nested
    class Standing {

        @Test
        void visitsEveryStandingSplatAndDropsDissolvedOnes() {
            MobCoats coats = struckAt(HIT_TICK, FIRST_HIT);
            coats.coat(MOB, GooTypes.BLAZE, HIT_TICK + COAT_LIFE_TICKS, new MobCoats.Strike(SECOND_HIT, NO_AIM, STOOD));
            coats.coat(MOB + 1, GooTypes.BLAZE, HIT_TICK + COAT_LIFE_TICKS, new MobCoats.Strike(FIRST_HIT, NO_AIM, STOOD));
            List<String> visited = new ArrayList<>();

            coats.forEachStanding(HIT_TICK + COAT_LIFE_TICKS, (id, coat) -> visited.add(id + "@" + coat.hitPoint()));

            assertEquals(List.of(MOB + "@" + SECOND_HIT, (MOB + 1) + "@" + FIRST_HIT), visited);
        }

        @Test
        void clearDropsEverySplat() {
            MobCoats coats = struckAt(HIT_TICK, FIRST_HIT);
            coats.coat(MOB + 1, GooTypes.BLAZE, HIT_TICK, new MobCoats.Strike(FIRST_HIT, NO_AIM, STOOD));

            coats.clear();

            assertTrue(coats.coatsOf(MOB, HIT_TICK).isEmpty());
            assertTrue(coats.coatsOf(MOB + 1, HIT_TICK).isEmpty());
        }
    }
}
