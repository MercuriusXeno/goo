package com.mercuriusxeno.goo.client.ber;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Render state snapshot for a light beacon: the side its column leaves by, the column's clock and its widening.
 * decision bulb-one-model-max-light-beacon-combo
 */
public class LightBeaconRenderState extends BlockEntityRenderState {

    /** The side the column leaves by, away from the face the beacon attaches by. */
    public Direction facing = Direction.UP;

    /** The column's spin, scroll and breath clock, in ticks. */
    public float animationTime;

    /** The factor the column's radii take at the camera's distance. */
    public float radiusScale = 1f;

    /** How far the column draws from the beacon's center, to the first block that stops it. */
    public float columnLength = BeaconBeams.BEAM_REACH;

    /** The camera's position in the world, which the column fades by. */
    public Vec3 camera = Vec3.ZERO;

    /** The camera distance the column has faded to nothing by: the render distance, in blocks. */
    public double fadeOut = BeaconBeams.BEAM_REACH;
}
