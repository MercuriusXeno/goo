package com.mercuriusxeno.goo.ability.crystal;

import com.mercuriusxeno.goo.ability.TravelForm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The sizes, facing and stack geometry the operator ruled at the playtest: a
 * javelin 1.25 blocks long and a knife 0.4, held and thrown alike; each aims
 * at a point 64 blocks straight ahead, no block or mob in the way consulted;
 * and no row of the inverted knife
 * pyramid passes through the row under it.
 * decision knives-hover-above-the-blob-cube-over-the-hand
 * decision dart-javelins-hover-over-the-shrinking-blob-cube
 * decision knives-hover-in-an-upside-down-bowling-pin-pyramid
 */
class StackShapeTest {

    private static final double WITHIN = 1e-6;
    private static final double JAVELIN_LENGTH = 1.25;
    private static final double KNIFE_LENGTH = 0.4;
    private static final double AIM_AHEAD = 64;

    @Test
    void aJavelinIsOneAndAQuarterBlocksAndAKnifeFourTenths() {
        assertEquals(JAVELIN_LENGTH, TravelForm.DART.frontLength(), WITHIN);
        assertEquals(KNIFE_LENGTH, StackHover.KNIFE_LENGTH, WITHIN);
    }

    @Test
    void eachAimsAtAPointSixtyFourBlocksStraightAhead() {
        Player player = mock(Player.class);
        Vec3 eye = new Vec3(1, 70, -2);
        Vec3 look = Vec3.directionFromRotation(12f, 33f);
        when(player.getEyePosition(1f)).thenReturn(eye);
        when(player.getViewVector(1f)).thenReturn(look);
        assertEquals(0, eye.add(look.scale(AIM_AHEAD)).distanceTo(ThrownKnife.aimPointOf(player, 1f)), WITHIN);
    }

    @Test
    void noRowOfTheKnifePyramidPassesThroughTheRowUnderIt() {
        for (int held = 1; held <= StackHover.KNIVES; held++) {
            for (int upper = 0; upper < held; upper++) {
                for (int lower = 0; lower < held; lower++) {
                    StackHover.Slot above = StackHover.knifeSlot(upper, held);
                    StackHover.Slot below = StackHover.knifeSlot(lower, held);
                    boolean sideBySide = Math.abs(above.right() - below.right()) < StackHover.KNIFE_WIDTH;
                    if (above.up() > below.up() + WITHIN && sideBySide) {
                        assertTrue(above.up() - below.up() >= 2 * StackHover.KNIFE_HALF_HEIGHT,
                                "knife " + upper + " stands " + (above.up() - below.up()) + " over knife " + lower
                                        + " at " + held + " held, through its blade");
                    }
                }
            }
        }
    }
}