package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A portal view cuts away what stands between its camera and the partner's
 * face, but never the part of the local player's body that straddles that
 * face: cut there, the body shows its hollow inside, and the player saw its
 * own face looking back through a portal while it backed against the
 * partner (decision portals-link-by-looking-through-portal-vision).
 */
class PortalViewClipTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    /** The partner, three blocks before a camera at the origin looking along -z, its face the plane z -3. */
    private static final Gate PARTNER = new Gate(OVERWORLD, new AABB(-0.5, -1, -3, 0.5, 1, -2), Direction.NORTH,
            Direction.UP.getUnitVec3());
    private static final float FOV = (float) Math.toRadians(70);
    private static final float NEAR = 0.05F;
    private static final float FAR = 512F;
    /** A player's back, two tenths behind the partner's face, toward the camera. */
    private static final Vector4f BACK_BEHIND_THE_FACE = new Vector4f(0, 0, -2.8F, 1F);
    /** A player backed against the partner: its middle a tenth before the face, its box two tenths behind it. */
    private static final AABB STRADDLING = new AABB(-0.3, -1, -3.4, 0.3, 0.8, -2.8);
    /** How far a straddling box reaches behind the face. */
    private static final double REACHES = 0.2;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aBodyStraddlingThePartnersFaceIsDrawnWhole(boolean zeroToOne) {
        Matrix4f projection = clipped(zeroToOne, PortalView.bodyBehindTheFace(PARTNER, STRADDLING));

        float depth = depthOf(projection, BACK_BEHIND_THE_FACE);
        assertTrue(depth >= (zeroToOne ? 0F : -1F) && depth <= 1F, "The back behind the face should be drawn, "
                + "its depth reads " + depth);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void whatStandsBehindTheFaceWithNoBodyThereIsCutAway(boolean zeroToOne) {
        Matrix4f projection = clipped(zeroToOne, 0);

        float depth = depthOf(projection, BACK_BEHIND_THE_FACE);
        assertTrue(depth < (zeroToOne ? 0F : -1F), "What stands behind the face should be cut away, its depth reads "
                + depth);
    }

    @Test
    void aStraddlingBoxReachesBehindTheFaceByItsDepthThere() {
        assertEquals(REACHES, PortalView.bodyBehindTheFace(PARTNER, STRADDLING), 0.06,
                "A box two tenths behind the face should reach about that far, with a hair to spare");
    }

    @Test
    void aBoxWhollyBeforeTheFaceOrBesideTheOpeningReachesNothing() {
        assertEquals(0, PortalView.bodyBehindTheFace(PARTNER, STRADDLING.move(0, 0, -1)), 1.0E-9);
        assertEquals(0, PortalView.bodyBehindTheFace(PARTNER, STRADDLING.move(3, 0, 0)), 1.0E-9);
    }

    private static Matrix4f clipped(boolean zeroToOne, double reach) {
        Matrix4f projection = new Matrix4f().perspective(FOV, 1F, NEAR, FAR, zeroToOne);
        GpuDevice device = mock(GpuDevice.class);
        when(device.isZZeroToOne()).thenReturn(zeroToOne);
        try (MockedStatic<RenderSystem> render = mockStatic(RenderSystem.class)) {
            render.when(RenderSystem::getDevice).thenReturn(device);
            PortalView.clipAt(projection, new Matrix4f(), Vec3.ZERO, PARTNER, reach);
        }
        return projection;
    }

    private static float depthOf(Matrix4f projection, Vector4f point) {
        Vector4f clip = projection.transform(new Vector4f(point));
        return clip.z() / clip.w();
    }
}
