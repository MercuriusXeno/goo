package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;

/**
 * Render state snapshot for the ability block BER. Captures goo type,
 * stack count and program state for the slime-like orb visual and the
 * visuals of the abilities that linger.
 */
public class AbilityBlockRenderState extends BlockEntityRenderState {

    /** The goo type determining color and fluid texture. */
    public ResourceKey<GooTypeDefinition> gooType = GooTypes.ROCK;


    /** The id of the ability the marker runs. */
    public String abilityId = "";


    /** Partial tick for smooth interpolation. */
    public float partialTick;

    /** True when the player's crosshair is on this block. */
    public boolean targeted;

    /** Current game time including partial tick, for pulse calculation. */
    public float gameTime;

    /** The face this marker was placed on (for directional rendering). */
    public Direction placedFace = Direction.UP;

    /** True while the marker's program runs. */
    public boolean behaviorActive;

}
