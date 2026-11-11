package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.client.TargetResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The aimed mob is outlined once, on the copy the aim reaches: the one before
 * the player where the aim runs straight, the one seen through the gate the
 * aim passed where it runs through, never also on a copy another pass draws,
 * so the outlines never change in number as one copy hides another, as the
 * operator found walking up to a portal (decision aim-and-shots-carry-through-linked-gates).
 */
class AimTrackerTest {

    private static final int COLOR = 0xFF33AAFF;
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    /** The gate the aim passed. */
    private static final Gate PASSED = new Gate(OVERWORLD, new AABB(new BlockPos(0, 0, 0)).minmax(
            new AABB(new BlockPos(0, 1, 0))), Direction.NORTH, Direction.UP.getUnitVec3());
    private static final TargetResult.Passage THROUGH = new TargetResult.Passage(PASSED, new Vec3(0.5, 1.0, 0.0));

    @Test
    void aMobAimedStraightIsOutlinedBeforeThePlayerAlone() {
        assertEquals(COLOR, AimTracker.outlineIn(COLOR, false, null, null), "The copy before the player");
        assertEquals(0, AimTracker.outlineIn(COLOR, true, PASSED, null), "A copy seen through a portal");
        assertEquals(0, AimTracker.outlineIn(COLOR, true, null, null), "A copy seen a portal deeper");
    }

    @Test
    void aMobAimedThroughAPortalIsOutlinedInThatPortalsViewAlone() {
        assertEquals(COLOR, AimTracker.outlineIn(COLOR, true, PASSED, THROUGH), "The copy through the portal");
        assertEquals(0, AimTracker.outlineIn(COLOR, false, null, THROUGH), "The copy before the player");
        assertEquals(0, AimTracker.outlineIn(COLOR, true, null, THROUGH), "A copy seen a portal deeper");
    }
}
