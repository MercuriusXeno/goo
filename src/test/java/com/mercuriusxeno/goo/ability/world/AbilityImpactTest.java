package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AbilityImpact's landing rules: where a landing's program anchors, which
 * landings play their burnout later or turn into their own block, and which
 * prisms a landing ticks rather than lands in.
 */
class AbilityImpactTest {

    private static final BlockPos CELL = new BlockPos(3, 64, -1);
    /** An arc throw whose standing block waits, then explodes. */
    private static final String LINGERING_FUSE = """
            {"gooType": "unstable", "displayName": "lingering_fuse", "badge": "world", "cost": 10,
             "delivery": {"kind": "arc"},
             "behaviors": [{"type": "linger", "steps": [{"type": "wait", "ticks": 20},
                                                        {"type": "explode", "power": 2.5}]}]}
            """;

    /** A free ability's program anchors at the aimed point, not the cell's center. */
    // decision aim-point-follows-the-cursor
    @Test
    void anAimedPointAnchorsTheProgramThere() {
        Vec3 point = new Vec3(3.2, 64.9, -0.9);

        assertEquals(point, AbilityImpact.anchor(CELL, point));
    }

    // decision aim-point-follows-the-cursor
    @Test
    void noAimedPointAnchorsTheProgramAtTheCellsCenter() {
        assertEquals(new Vec3(3.5, 64.5, -0.5), AbilityImpact.anchor(CELL, null));
    }

    /**
     * A lingering block whose body explodes waits for that explosion to play
     * its burnout; Blast resolves at the splat and plays it there.
     * decision elemental-explosion-per-type
     */
    @Test
    void aLingeringFuseExplodesLaterAndBlastDoesNot() {
        assertTrue(AbilityImpact.explodesLater(AbilityJson.decodeText("lingering_fuse", LINGERING_FUSE)));
        assertFalse(AbilityImpact.explodesLater(AbilityJson.decode("unstable_blast")));
    }

    /**
     * Shriek's trap shrieks on its landing and never explodes, so its burnout plays at the landing.
     */
    @Test
    void aLingeringFieldNeverExplodesLater() {
        assertFalse(AbilityImpact.explodesLater(AbilityJson.decode("xeno_shriek")));
    }

    /**
     * Ignite's blob turns into the fire it lights, the fire standing in for its burnout.
     */
    // idea ignite-plays-no-burnout
    @Test
    void ignitesBlobTurnsIntoItsFire() {
        assertTrue(AbilityImpact.lightsItsFire(AbilityJson.decode("blaze_ignite")));
    }

    /**
     * Zap, which carries the relay as its on_prism reaction, still ticks a
     * prism already holding a combo as a redstone device rather than growing
     * a second combo in it, and lands in a bare prism.
     * zap-ticks-the-device-and-stuns
     * thrown-hosts-absorb-their-prism-combos
     */
    @Test
    void zapTicksAComboPrismAndLandsInABareOne() {
        AbilityDefinition zap = AbilityJson.decode("pulse_zap");
        PrismBlockEntity combined = mock(PrismBlockEntity.class);
        when(combined.hasCombo()).thenReturn(true);
        PrismBlockEntity bare = mock(PrismBlockEntity.class);

        assertFalse(zap.onPrism().isEmpty(), "pulse_zap's on_prism relay");
        assertTrue(AbilityImpact.ticksAComboPrism(combined, zap));
        assertFalse(AbilityImpact.ticksAComboPrism(bare, zap));
    }
}
