package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.client.PrismCrystal;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Render state snapshot for the prism: the look of its crystal, the face it
 * grew from, the size the growing transformation gives it and the combo it
 * holds. A combo style re-emits {@link PrismCrystal#PRISMS} in its own tint
 * over {@link #look}'s sprite, turned by {@link PrismCrystal#standOnLandingFace}.
 */
public class PrismRenderState extends BlockEntityRenderState {

    /** The plain crystal's sprite and tint, resolved from the block atlas. */
    public CrystalClusterSubmitter.@Nullable Look look;

    /** The landing blob's goo look while it morphs into the crystal, null once it has. */
    public CrystalClusterSubmitter.@Nullable Look blobLook;

    /** The prism's facing; the crystal grows from the opposite face, the base it scales about. */
    public Direction facing = Direction.UP;

    /** How far the landing blob has become the prism, 0 as it lands and 1 once grown. */
    public float scale = 1f;

    /** The id of the ability whose program is the prism's combo, empty for a plain prism. */
    public String combo = "";

    /** The beam's scroll clock: the game time within vanilla's 40-tick beacon cycle plus the partial tick. */
    public float animationTime;

    /** How much a beam widens with the camera's horizontal distance, as vanilla's beacon widens. */
    public float beamRadiusScale = 1f;

    /** Each linked reflector's offset from this prism's cell, for the rail beams (decision reflector-rails-carry-the-brightest-light). */
    public List<Vec3> links = List.of();

    /** The light the linked network's rails carry, 0 to 15. */
    public int linkLight;

    /** The game time with the partial tick, which a combo's animation reads. */
    public float gameTime;

    /** The game time the prism's combo took, which its transformation plays from. */
    public long comboSince;

    /**
     * The yaw, in degrees, that turns a combo's model from the prism's cell
     * toward the camera (decision oculus-prism-becomes-a-hovering-eye).
     */
    public float yawToCamera;

    /**
     * How shut an oculus's lids stand this frame for this viewer, 0 open to 1
     * shut (decision oculus-prism-becomes-a-hovering-eye).
     */
    public float lidClosure = 1f;

    /**
     * The redstone power the prism gives, a metronome's beat or a relay's
     * carried signal (decisions metronome-prism-pulses-at-the-learned-rate,
     * relay-prism-carries-the-signal-through-air).
     */
    public int power;

    /** Whether a redstone signal reaches the prism now (decision metronome-prism-pulses-at-the-learned-rate). */
    public boolean signalHeard;

    /** Seconds since the prism last gave a pulse of power, for a metronome's strobe. */
    public double sinceBeat = Double.MAX_VALUE;
    /** How strongly an agitator's beat shows this frame, 0 to 1; 0 for any other prism. */
    public float beat;
}
