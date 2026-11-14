package com.mercuriusxeno.goo.client.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.animal.cow.Cow;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * A downed mob's render state carries the turn onto its side and a frozen
 * walk; a standing mob's carries neither.
 */
class DownedPoseTest {

    private static final float WALK_PHASE = 3f;
    private static final float WALK_REACH = 0.8f;
    private static final float FACING = 37f;
    private static final float LEVEL = 1e-5f;

    private static LivingEntityRenderState walking() {
        LivingEntityRenderState state = new LivingEntityRenderState();
        state.walkAnimationPos = WALK_PHASE;
        state.walkAnimationSpeed = WALK_REACH;
        state.bodyRot = FACING;
        return state;
    }

    private static Cow cow(boolean downed) {
        Cow cow = mock(Cow.class);
        when(cow.hasData(GooAttachments.DOWNED)).thenReturn(downed);
        return cow;
    }

    @Test
    void aDownedMobLiesOnItsSideWithItsWalkFrozen() {
        LivingEntityRenderState state = walking();
        DownedPose.stampDownedPose(cow(true), state);
        Quaternionf fallen = state.getRenderData(DownedPose.FALLEN);
        assertNotNull(fallen);
        Vector3f up = fallen.transform(new Vector3f(0f, 1f, 0f));
        assertEquals(0f, up.y(), LEVEL, "the body's up should lie flat, stands " + up);
        assertEquals(0f, state.walkAnimationPos);
        assertEquals(0f, state.walkAnimationSpeed);
    }

    @Test
    void aStandingMobKeepsItsPoseAndWalk() {
        LivingEntityRenderState state = walking();
        DownedPose.stampDownedPose(cow(false), state);
        assertNull(state.getRenderData(DownedPose.FALLEN));
        assertEquals(WALK_PHASE, state.walkAnimationPos);
        assertEquals(WALK_REACH, state.walkAnimationSpeed);
    }
}
