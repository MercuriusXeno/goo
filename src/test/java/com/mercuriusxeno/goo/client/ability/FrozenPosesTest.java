package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A frozen entity's pose holds still on the client from the first frozen
 * frame until it thaws.
 */
class FrozenPosesTest {

    private static final int ENTITY = 7;

    private static LivingEntityRenderState posed(float phase) {
        LivingEntityRenderState state = new LivingEntityRenderState();
        state.walkAnimationPos = phase;
        state.walkAnimationSpeed = phase / 2;
        state.ageInTicks = phase * 10;
        state.bodyRot = phase + 1;
        state.yRot = phase + 2;
        state.xRot = phase + 3;
        return state;
    }

    @Test
    void aFrozenEntityDrawsThePoseOfItsFirstFrozenFrame() {
        FrozenPoses poses = new FrozenPoses();
        poses.hold(ENTITY, true, posed(1f));
        LivingEntityRenderState later = posed(5f);
        poses.hold(ENTITY, true, later);
        assertEquals(FrozenPoses.Pose.of(posed(1f)), FrozenPoses.Pose.of(later));
    }

    @Test
    void aThawedEntityMovesAgainAndFreezesAfreshNextTime() {
        FrozenPoses poses = new FrozenPoses();
        poses.hold(ENTITY, true, posed(1f));
        LivingEntityRenderState thawed = posed(5f);
        poses.hold(ENTITY, false, thawed);
        assertEquals(FrozenPoses.Pose.of(posed(5f)), FrozenPoses.Pose.of(thawed));
        LivingEntityRenderState refrozen = posed(9f);
        poses.hold(ENTITY, true, posed(8f));
        poses.hold(ENTITY, true, refrozen);
        assertEquals(FrozenPoses.Pose.of(posed(8f)), FrozenPoses.Pose.of(refrozen));
    }
}
