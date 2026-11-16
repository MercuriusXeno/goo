package com.mercuriusxeno.goo.ability.zoo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers whose hit ends a rally, the rally order an older save loads with,
 * and whose cast releases the rally.
 */
class RalliedTest {

    private static final UUID FIRST = new UUID(0L, 1L);
    private static final UUID SECOND = new UUID(0L, 2L);
    private static final float DAMAGE = 6f;

    /** rally-is-a-mob-effect-on-one-target: the rally fades when its rallier strikes the mob, as Charm does. */
    @Nested
    class BrokenBy {

        @Test
        void theRalliersHitBreaksTheRally() {
            assertTrue(new Rallied(FIRST, DAMAGE).brokenBy(FIRST));
        }

        @Test
        void anotherAttackersHitLeavesTheRally() {
            assertFalse(new Rallied(FIRST, DAMAGE).brokenBy(SECOND));
        }

        @Test
        void damageWithNoAttackerLeavesTheRally() {
            assertFalse(new Rallied(FIRST, DAMAGE).brokenBy(null));
        }
    }

    /** slots-follow-rally-order-size-and-count */
    @Nested
    class RallyOrder {

        @Test
        void aRallySavedBeforeTheOrderLoadsWithTheDefaultOrder() {
            JsonObject saved = new JsonObject();
            JsonArray caster = new JsonArray();
            caster.add(0);
            caster.add(0);
            caster.add(0);
            caster.add(1);
            saved.add("caster", caster);
            saved.addProperty("damage", DAMAGE);
            Rallied loaded = Rallied.CODEC.codec().parse(JsonOps.INSTANCE, saved).getOrThrow();
            assertEquals(new Rallied(FIRST, DAMAGE, Optional.empty(), RallyArmor.NONE, Rallied.DEFAULT_RALLY_ORDER),
                    loaded);
        }
    }

    /** rally-cast-on-your-rallied-mob-releases-it */
    @Nested
    class ReleasedBy {

        @Test
        void theRalliersCastReleasesTheRally() {
            assertTrue(new Rallied(FIRST, DAMAGE).releasedBy(FIRST));
        }

        @Test
        void anotherCastersCastLeavesTheRally() {
            assertFalse(new Rallied(FIRST, DAMAGE).releasedBy(SECOND));
        }

        @Test
        void aMobHoldingNoRallyHasNoRallyToRelease() {
            // NONE's own caster UUID asked, so only the no-rally rule answers false
            assertFalse(Rallied.NONE.releasedBy(Rallied.NONE.caster()));
        }
    }
}
