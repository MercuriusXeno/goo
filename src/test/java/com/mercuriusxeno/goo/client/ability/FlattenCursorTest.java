package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Flatten's disc cursor as the player sneaks: its point and the cell it lies
 * on resolve along the ray the caller passes, the camera's for the local
 * player in first person, so the disc eases with the view
 * (decision diagnose-and-correct-eye-aimed-visuals-jumping-on-sneak).
 */
class FlattenCursorTest {

    private static final double REACH = 4.5;
    private static final CameraEye.Sight CROUCHED_EYE = new CameraEye.Sight(new Vec3(0.5, 65.27, 0.5),
            new Vec3(0, -0.057, 1).normalize());
    private static final CameraEye.ViewClip OPEN_AIR =
            (from, to) -> BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to));

    private static CameraEye.Sight cameraAt(double eyeHeight, Vec3 look) {
        return new CameraEye.Sight(new Vec3(0.5, 64 + eyeHeight, 0.5), look);
    }

    private static Vec3 drawnPointFrom(double eyeHeight, CameraEye.ViewClip blocks, double reach) {
        return GloveThrowSender.cursorPoints(true, cameraAt(eyeHeight, CROUCHED_EYE.look()), CROUCHED_EYE, null,
                reach, blocks).drawn();
    }

    @Test
    void theCursorsPointInOpenAirMovesWithTheRayOriginsHeight() {
        assertEquals(0.15, drawnPointFrom(1.62, OPEN_AIR, REACH).y - drawnPointFrom(1.47, OPEN_AIR, REACH).y, 1e-9);
    }

    @Test
    void theCellTheDiscLiesOnFollowsTheRayTheCallerPasses() {
        ChannelAim.FacePlane wallFace = new ChannelAim.FacePlane(new BlockPos(0, 64, 10), Direction.NORTH);
        Vec3 standingEye = cameraAt(1.62, CROUCHED_EYE.look()).eye();
        Vec3 easingEye = cameraAt(1.47, CROUCHED_EYE.look()).eye();

        BlockPos standing = FlattenCursor.cellUnder(standingEye, drawnPointFrom(1.62, FlatWall::clip, 20), wallFace);
        BlockPos easing = FlattenCursor.cellUnder(easingEye, drawnPointFrom(1.47, FlatWall::clip, 20), wallFace);

        assertEquals(new BlockPos(0, 65, 9), standing);
        assertEquals(new BlockPos(0, 64, 9), easing);
    }
}
