package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.canister.CanisterGeometry;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * Render state snapshot for the tap BER. Wraps a single {@link SlotState}
 * for the body slot plus the spigot facing direction and the stream it pours.
 */
public class TapRenderState extends BlockEntityRenderState {

    /** The tap's canister stands on the tap body (cap bottom at 4px). */
    private static final CanisterGeometry CANISTER = CanisterGeometry.at(5f / 16f, 15f / 16f);

    /** The tap's facing direction (spigot direction). */
    public Direction facing = Direction.SOUTH;

    /** State of the single body slot. */
    public final SlotState slot = new SlotState();

    /** The goo type the tap pours at 1:1, or null while it drips or stands idle. */
    public @Nullable ResourceKey<GooTypeDefinition> streamType;

    /** The block-local Y the stream lands on, below the tap's block when it falls past it. */
    public float streamBottomY;

    /**
     * @return where the tap's canister stands
     */
    public CanisterGeometry canisterGeometry() {
        return CANISTER;
    }
}
