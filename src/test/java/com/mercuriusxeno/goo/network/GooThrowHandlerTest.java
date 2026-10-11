package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.item.ReagentScanner;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A point an ability aims beyond the throw range is capped onto the range
 * along the throw's line rather than refused, so aiming at the sky throws to
 * the range's end (decision aim-point-follows-the-cursor).
 */
class GooThrowHandlerTest {

    private static final double TOLERANCE = 1e-9;
    private static final Vec3 EYE = new Vec3(10, 65.62, -4);

    @Test
    void aPointBeyondRangeIsCappedOntoTheRangeAlongItsLine() {
        Vec3 far = EYE.add(30, 90, -60);

        Vec3 capped = GooThrowHandler.capToRange(EYE, far, GooThrowHandler.MAX_RANGE);

        assertEquals(GooThrowHandler.MAX_RANGE, capped.distanceTo(EYE), TOLERANCE);
        Vec3 along = far.subtract(EYE).normalize();
        assertEquals(1, capped.subtract(EYE).normalize().dot(along), TOLERANCE);
    }

    @Test
    void aPointWithinRangePassesUnchanged() {
        Vec3 near = EYE.add(12, -3, 40);

        assertSame(near, GooThrowHandler.capToRange(EYE, near, GooThrowHandler.MAX_RANGE));
    }

    @Test
    void aPointOnTheRangePassesUnchanged() {
        Vec3 edge = EYE.add(0, 0, GooThrowHandler.MAX_RANGE);

        assertSame(edge, GooThrowHandler.capToRange(EYE, edge, GooThrowHandler.MAX_RANGE));
    }

    @Test
    void aMobAbilityNamingNoEntityIsRefusedAndOneNamingAnEntityIsNot() {
        assertTrue(GooThrowHandler.aimsNoMob(AbilityBadge.MOB, false));
        assertFalse(GooThrowHandler.aimsNoMob(AbilityBadge.MOB, true));
        assertFalse(GooThrowHandler.aimsNoMob(AbilityBadge.WORLD, false));
    }

    @Test
    void spawnThrownByAPlayerHoldingNothingPassesTheReagentCheck() {
        // ability-json-names-its-reagent
        List<Identifier> spawnReagents = AbilityJson.decode("zoo_spawn").consumes();

        assertTrue(ReagentScanner.holdsEvery(spawnReagents, reagent -> false));
    }

    @Test
    void enchantThrownByAPlayerHoldingNoBookFailsTheReagentCheck() {
        List<Identifier> enchantReagents = AbilityJson.decode("hex_enchant").consumes();

        assertFalse(ReagentScanner.holdsEvery(enchantReagents, reagent -> false));
    }
}
