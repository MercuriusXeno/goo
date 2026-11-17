package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a held stack centers: over the palm the hand's pass draws for the
 * local player in first person, and over the body's hand for a caster drawn
 * in third person, so the pile stands over the hand however it is seen.
 * decision knives-hover-above-the-blob-cube-over-the-hand
 */
class StackAnchorTest {

    private static final double DELTA = 1e-6;
    /** The full blob cube's edge, which the knife pile's cube stands over the palm by: one cube clear of it. */
    private static final double CUBE_EDGE = 2 * 0.15 * 0.5 * 0.9;

    private static Player caster() {
        return (Player) Minecraft.getInstance().level.getEntity(SprayingClient.CASTER);
    }

    private static void assertNear(Vec3 expected, Vec3 actual) {
        assertTrue(expected.distanceTo(actual) <= DELTA, "expected " + expected + ", was " + actual);
    }

    @Test
    void anotherCastersStackCentersOverTheirBodysHand() {
        try (SprayingClient client = SprayingClient.another(GooTypes.CRYSTAL)) {
            Player caster = caster();
            Vec3 overTheHand = KnivesCharge.handAt(caster, true, 0f).add(0, SpraySource.RISE_ABOVE_THE_PALM, 0);

            assertNear(overTheHand, StackAnchor.cubeOf(caster, 0f, true));
        }
    }

    @Test
    void theLocalCastersStackCentersOverThePalmTheHandsPassDraws() {
        try (SprayingClient client = SprayingClient.local(GooTypes.CRYSTAL)) {
            assertNear(SpraySource.of(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, 0f),
                    StackAnchor.cubeOf(caster(), 0f, true));
        }
    }

    @Test
    void anotherCastersKnifePileCentersJustOverTheirBodysHand() {
        try (SprayingClient client = SprayingClient.another(GooTypes.CRYSTAL)) {
            Player caster = caster();
            Vec3 justOverTheHand = KnivesCharge.handAt(caster, true, 0f).add(0, CUBE_EDGE, 0);

            assertNear(justOverTheHand, StackAnchor.cubeOf(caster, 0f, false));
        }
    }}
