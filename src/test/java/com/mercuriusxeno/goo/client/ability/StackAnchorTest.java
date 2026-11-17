package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.crystal.StackHover;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a held stack, Knives' or Dart's, centers: one cube edge over the
 * palm the hand's pass draws for the local player in first person, and one
 * cube edge over the body's hand for a caster drawn in third person, so the
 * pile stands over the hand however it is seen.
 * decision knives-hover-above-the-blob-cube-over-the-hand
 * decision dart-javelins-hover-over-the-shrinking-blob-cube
 */
class StackAnchorTest {

    private static final double DELTA = 1e-6;
    /** The full blob cube's edge, which a stack's cube stands over the palm by: one cube clear of it. */
    private static final double CUBE_EDGE = 2 * 0.15 * 0.5 * 0.9;

    private static Player caster() {
        return (Player) Minecraft.getInstance().level.getEntity(SprayingClient.CASTER);
    }

    private static void assertNear(Vec3 expected, Vec3 actual) {
        assertTrue(expected.distanceTo(actual) <= DELTA, "expected " + expected + ", was " + actual);
    }

    @Test
    void anotherCastersStackCentersOneCubeEdgeOverTheirBodysHand() {
        try (SprayingClient client = SprayingClient.another(GooTypes.CRYSTAL)) {
            Player caster = caster();
            Vec3 overTheHand = KnivesCharge.handAt(caster, true, 0f).add(0, CUBE_EDGE, 0);

            assertNear(overTheHand, StackAnchor.cubeOf(caster, 0f));
        }
    }

    @Test
    void theLocalCastersStackCentersOneCubeEdgeOverThePalmTheHandsPassDraws() {
        try (SprayingClient client = SprayingClient.local(GooTypes.CRYSTAL)) {
            assertNear(SpraySource.of(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, 0f,
                    StackHover.KNIFE_RISE_ABOVE_THE_PALM), StackAnchor.cubeOf(caster(), 0f));
        }
    }
}