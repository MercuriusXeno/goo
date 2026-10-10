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
    /** How strongly an agitator's beat shows this frame, 0 to 1; 0 for any other prism. */
    public float beat;
}
