package com.mercuriusxeno.goo.client.ber;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * Render state snapshot for a reflector: its links, its network's light and the beams' clock.
 * decision reflector-rails-carry-the-brightest-light
 */
public class ReflectorRenderState extends BlockEntityRenderState {

    /** Each linked reflector's offset from this reflector's cell. */
    public List<Vec3> links = List.of();

    /** The light the linked network's rails carry, 0 to 15. */
    public int linkLight;

    /** The beams' scroll and breath clock, in ticks. */
    public float animationTime;
}
