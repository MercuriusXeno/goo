package com.mercuriusxeno.goo.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** A stream tick and a tap's drip report each living thing whose health their program raised, the list the healing stars play on (decisions vitality-waves-regenerate-and-court, vitality-drip-heals-below). */
class HealReportTest {

    private static final int COW_ID = 7;
    private static final int PIG_ID = 8;

    /** A living thing whose health the program sets. */
    private static final class Creature {
        private final int id;
        private float health;

        Creature(int id, float health) {
            this.id = id;
            this.health = health;
        }
    }

    private static final HealReport<Creature> REPORT = new HealReport<>(creature -> creature.health,
            creature -> creature.id);

    /** One stream tick's program on one living thing. */
    @Nested
    class RunNoting {

        @Test
        void aThingWhoseHealthRoseIsReportedHealed() {
            Creature cow = new Creature(COW_ID, 4f);
            List<Integer> healed = new ArrayList<>();
            REPORT.runNoting(cow, healed, () -> cow.health = 4.1f);
            assertEquals(List.of(COW_ID), healed);
        }

        @Test
        void aThingAtFullHealthIsNotReported() {
            List<Integer> healed = new ArrayList<>();
            REPORT.runNoting(new Creature(COW_ID, 10f), healed, () -> { });
            assertEquals(List.of(), healed);
        }

        @Test
        void aThingTheProgramHurtIsNotReported() {
            Creature cow = new Creature(COW_ID, 10f);
            List<Integer> healed = new ArrayList<>();
            REPORT.runNoting(cow, healed, () -> cow.health = 9f);
            assertEquals(List.of(), healed);
        }
    }

    /** One drip's program over the living things around its landing. */
    @Nested
    class HealedAmong {

        @Test
        void aDripReportsOnlyWhatItHealed() {
            Creature cow = new Creature(COW_ID, 4f);
            Creature pig = new Creature(PIG_ID, 10f);
            assertEquals(List.of(COW_ID), REPORT.healedAmong(List.of(cow, pig), () -> cow.health = 5f));
        }

        @Test
        void aDripHealingNothingReportsNothing() {
            assertEquals(List.of(), REPORT.healedAmong(List.of(new Creature(PIG_ID, 10f)), () -> { }));
        }
    }
}
