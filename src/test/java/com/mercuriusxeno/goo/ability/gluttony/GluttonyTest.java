package com.mercuriusxeno.goo.ability.gluttony;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gluttony's expiry and banks: a repeat invoke stacking its duration, a
 * heal past full health and a meal past full hunger each banking its excess
 * up to its cap, banked hunger
 * refilling a dropped bar, and the saved form carrying no clock
 * (decision gluttony-overheals-and-overhungers).
 */
class GluttonyTest {

    private static final long NOW = 1_000L;
    private static final int DURATION = 400;
    private static final int MAX_OVERHEAL = 4;
    private static final int MAX_OVERHUNGER = 3;
    private static final float MAX_HEALTH = 20f;
    private static final float HEAL = 3f;
    private static final Gluttony CAPS = Gluttony.caps(MAX_OVERHEAL, MAX_OVERHUNGER);

    private static Gluttony glutton() {
        return Gluttony.NONE.apply(CAPS, DURATION, NOW);
    }

    @Nested
    class Apply {

        @Test
        void anInvokeStandsForItsDurationUnderTheCaps() {
            Gluttony gluttony = glutton();
            assertEquals(NOW + DURATION, gluttony.expiresAt());
            assertEquals(MAX_OVERHEAL, gluttony.maxOverheal());
            assertEquals(MAX_OVERHUNGER, gluttony.maxOverhunger());
        }

        @Test
        void aRepeatInvokeStacksTheDurationAndKeepsTheBanks() {
            Gluttony banked = new Gluttony(NOW + DURATION, MAX_OVERHEAL, MAX_OVERHUNGER, 2f, 1);
            Gluttony stacked = banked.apply(CAPS, DURATION, NOW + 10);
            assertEquals(NOW + 2L * DURATION, stacked.expiresAt());
            assertEquals(1, stacked.overhunger());
            assertEquals(2f, stacked.overheal());
        }

        @Test
        void aHeldStartNeverExpires() {
            // self-effects-trickle-until-ended
            Gluttony held = Gluttony.NONE.hold(CAPS, NOW);
            assertEquals(Gluttony.NEVER_EXPIRES, held.expiresAt());
            assertSame(held, held.tick(NOW + 100L * DURATION));
        }

        @Test
        void aBrewOverAHeldGluttonyKeepsItHeld() {
            Gluttony held = Gluttony.NONE.hold(CAPS, NOW);
            assertEquals(Gluttony.NEVER_EXPIRES, held.apply(CAPS, DURATION, NOW + 1).expiresAt());
        }
    }

    @Nested
    class Tick {

        @Test
        void theGluttonyStandsUntilItsExpiry() {
            Gluttony gluttony = glutton();
            assertSame(gluttony, gluttony.tick(NOW + DURATION - 1));
            assertSame(Gluttony.NONE, gluttony.tick(NOW + DURATION));
        }

        @Test
        void nothingStandingStaysPut() {
            assertSame(Gluttony.NONE, Gluttony.NONE.tick(NOW));
        }
    }

    @Nested
    class BankHeal {

        @Test
        void aHealOnAFullBarBanksWhole() {
            assertEquals(HEAL, glutton().bankHeal(MAX_HEALTH, MAX_HEALTH, HEAL).overheal());
        }

        @Test
        void aHealOnAShortBarBanksOnlyItsExcess() {
            assertEquals(2f, glutton().bankHeal(MAX_HEALTH - 1f, MAX_HEALTH, HEAL).overheal());
        }

        @Test
        void aHealShortOfFullBanksNothing() {
            Gluttony gluttony = glutton();
            assertSame(gluttony, gluttony.bankHeal(MAX_HEALTH - HEAL, MAX_HEALTH, HEAL));
        }

        @Test
        void theBankStopsAtTheCapTheJsonNames() {
            Gluttony once = glutton().bankHeal(MAX_HEALTH, MAX_HEALTH, HEAL);
            assertEquals((float) MAX_OVERHEAL, once.bankHeal(MAX_HEALTH, MAX_HEALTH, HEAL).overheal());
        }

        @Test
        void aGluttonyNotStandingBanksNothing() {
            assertSame(Gluttony.NONE, Gluttony.NONE.bankHeal(MAX_HEALTH, MAX_HEALTH, HEAL));
        }
    }

    @Nested
    class BankMeal {

        private static final int MAX_FOOD = 20;
        private static final int NUTRITION = 2;

        @Test
        void aMealOnAFullBarBanksWhole() {
            assertEquals(NUTRITION, glutton().bankMeal(MAX_FOOD, MAX_FOOD, NUTRITION).overhunger());
        }

        @Test
        void aMealOnAShortBarBanksOnlyItsExcess() {
            assertEquals(1, glutton().bankMeal(MAX_FOOD - 1, MAX_FOOD, NUTRITION).overhunger());
        }

        @Test
        void aMealShortOfFullBanksNothing() {
            Gluttony gluttony = glutton();
            assertSame(gluttony, gluttony.bankMeal(MAX_FOOD - NUTRITION, MAX_FOOD, NUTRITION));
        }

        @Test
        void theBankStopsAtTheCapTheJsonNames() {
            Gluttony once = glutton().bankMeal(MAX_FOOD, MAX_FOOD, NUTRITION);
            assertEquals(MAX_OVERHUNGER, once.bankMeal(MAX_FOOD, MAX_FOOD, NUTRITION).overhunger());
        }

        @Test
        void aGluttonyNotStandingBanksNothing() {
            assertSame(Gluttony.NONE, Gluttony.NONE.bankMeal(MAX_FOOD, MAX_FOOD, NUTRITION));
        }
    }

    @Nested
    class Banks {

        @Test
        void bankedHungerRefillsADroppedBarOnly() {
            Gluttony banked = new Gluttony(NOW + DURATION, MAX_OVERHEAL, MAX_OVERHUNGER, 0f, 1);
            assertFalse(banked.refills(true));
            assertTrue(banked.refills(false));
            assertEquals(0, banked.refilled().overhunger());
            assertFalse(glutton().refills(false));
        }

        @Test
        void damageSpendsTheOwnedOverhealDownToWhatStands() {
            Gluttony banked = glutton().bankHeal(MAX_HEALTH, MAX_HEALTH, 2f);
            assertEquals(1f, banked.spentTo(1f).overheal());
            assertSame(banked, banked.spentTo(5f));
        }
    }

    @Nested
    class Codec {

        @Test
        void theSavedGluttonyCarriesNoClockAndRoundTrips() {
            Gluttony banked = new Gluttony(NOW + DURATION, MAX_OVERHEAL, MAX_OVERHUNGER, 2f, 1);
            JsonElement saved = Gluttony.CODEC.codec().encodeStart(JsonOps.INSTANCE, banked).getOrThrow();
            JsonObject fields = saved.getAsJsonObject();
            assertFalse(fields.has("interval"));
            assertFalse(fields.has("next_at"));
            assertEquals(banked, Gluttony.CODEC.codec().parse(JsonOps.INSTANCE, saved).getOrThrow());
        }
    }
}
