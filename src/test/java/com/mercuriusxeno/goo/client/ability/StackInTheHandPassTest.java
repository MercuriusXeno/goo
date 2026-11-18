package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The local player's held stack draws in the hand's pass with the lifted
 * blob, laid out as in the level and carried onto the same spot of the screen;
 * every other caster's draws in the level.
 * decision knives-hover-above-the-blob-cube-over-the-hand
 * decision dart-javelins-hover-over-the-shrinking-blob-cube
 */
class StackInTheHandPassTest {

    private static final double WITHIN = 1e-4;

    @Test
    void theHandPassMatrixCarriesAPointAsTheHandPassViewDoes() {
        Quaternionf rotation = new Quaternionf().rotateY(0.7f).rotateX(-0.3f);
        Matrix4f bob = new Matrix4f().translate(0.01f, -0.02f, 0f).rotateZ(0.02f);
        Matrix4f toHand = HandPassView.toHandPassMatrix(rotation, 90f, bob);
        for (Vec3 point : new Vec3[]{new Vec3(0.4, -0.2, 0.7), new Vec3(-1.3, 0.5, 2.1), new Vec3(0, 0, 0.3)}) {
            Vec3 expected = HandPassView.toHandPass(point, rotation, 90f, bob);
            Vector3f carried = toHand.transformPosition(point.toVector3f(), new Vector3f());
            assertEquals(0, expected.distanceTo(new Vec3(carried)), WITHIN, "the point " + point);
        }
    }

    @Test
    void theLocalPlayersStackDrawsInTheHandPassNotTheLevel() {
        try (SprayingClient client = SprayingClient.local(GooTypes.CRYSTAL)) {
            Player caster = (Player) Minecraft.getInstance().level.getEntity(SprayingClient.CASTER);
            assertFalse(HoveringKnives.drawsInTheLevel(caster));
        }
    }

    @Test
    void anotherCastersStackDrawsInTheLevel() {
        try (SprayingClient client = SprayingClient.another(GooTypes.CRYSTAL)) {
            Player caster = (Player) Minecraft.getInstance().level.getEntity(SprayingClient.CASTER);
            assertTrue(HoveringKnives.drawsInTheLevel(caster));
        }
    }
}