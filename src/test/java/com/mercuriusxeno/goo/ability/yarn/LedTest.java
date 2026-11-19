package com.mercuriusxeno.goo.ability.yarn;

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
 * Covers whose hit ends a lead, the lead order an older save loads with,
 * and whose cast releases the lead.
 */
class LedTest {

    private static final UUID FIRST = new UUID(0L, 1L);
    private static final UUID SECOND = new UUID(0L, 2L);
    private static final float DAMAGE = 6f;

    /** rally-is-a-mob-effect-on-one-target: the lead fades when its leader strikes the mob, as Charm does. */
    @Nested
    class BrokenBy {

        @Test
        void theLeadersHitBreaksTheLead() {
            assertTrue(new Led(FIRST, DAMAGE).brokenBy(FIRST));
        }

        @Test
        void anotherAttackersHitLeavesTheLead() {
            assertFalse(new Led(FIRST, DAMAGE).brokenBy(SECOND));
        }

        @Test
        void damageWithNoAttackerLeavesTheLead() {
            assertFalse(new Led(FIRST, DAMAGE).brokenBy(null));
        }
    }

    /** slots-follow-rally-order-size-and-count */
    @Nested
    class LeadOrder {

        @Test
        void aLeadSavedBeforeTheOrderLoadsWithTheDefaultOrder() {
            JsonObject saved = new JsonObject();
            JsonArray caster = new JsonArray();
            caster.add(0);
            caster.add(0);
            caster.add(0);
            caster.add(1);
            saved.add("caster", caster);
            saved.addProperty("damage", DAMAGE);
            Led loaded = Led.CODEC.codec().parse(JsonOps.INSTANCE, saved).getOrThrow();
            assertEquals(new Led(FIRST, DAMAGE, Optional.empty(), LeadArmor.NONE, Led.DEFAULT_LEAD_ORDER),
                    loaded);
        }
    }

    /** rally-cast-on-your-rallied-mob-releases-it */
    @Nested
    class ReleasedBy {

        @Test
        void theLeadersCastReleasesTheLead() {
            assertTrue(new Led(FIRST, DAMAGE).releasedBy(FIRST));
        }

        @Test
        void anotherCastersCastLeavesTheLead() {
            assertFalse(new Led(FIRST, DAMAGE).releasedBy(SECOND));
        }

        @Test
        void aMobHoldingNoLeadHasNoLeadToRelease() {
            // NONE's own caster UUID asked, so only the no-rally rule answers false
            assertFalse(Led.NONE.releasedBy(Led.NONE.caster()));
        }
    }
}
