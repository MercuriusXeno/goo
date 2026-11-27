package com.mercuriusxeno.goo.client.ber;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/**
 * Render state snapshot for an oculus: the side its eye hovers out from, the time its bob reads, the turn toward the
 * camera and how shut its lids stand for this viewer.
 * decision oculus-prism-becomes-a-hovering-eye
 */
public class OculusRenderState extends BlockEntityRenderState {

    /** The side the eye hovers out from, away from the face the oculus attaches by. */
    public Direction facing = Direction.UP;

    /** The game time with the partial tick, which the eye's bob reads. */
    public float gameTime;

    /** The yaw, in degrees, that turns the eye toward the camera. */
    public float yawToCamera;

    /** How shut the lids stand this frame for this viewer, 0 open to 1 shut. */
    public float lidClosure = 1f;
}
