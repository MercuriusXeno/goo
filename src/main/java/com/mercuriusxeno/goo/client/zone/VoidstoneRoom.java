package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.ability.zone.RoomExtent;
import com.mercuriusxeno.goo.client.ability.ClientRoomEdges;
import org.joml.Matrix4f;
import java.util.Optional;

/**
 * The room voidstone.fsh wraps its field at, as both voidstone draws hand it
 * to the shader: the room's center column and extent packed into the texture
 * matrix, which neither voidstone pipeline samples a texture by. The room's
 * own faces hand it through their render type's texture transform, the
 * copies' through the transform their pass writes. Each center coordinate is
 * split into a high and a low part, each a whole number a float holds
 * exactly however far out the room stands. An extent of 0 tells the shader
 * no room is known, and the field draws unwrapped.
 * decision voidstone-starfield-tiles-in-either-direction
 */
public final class VoidstoneRoom {

    /** Where a center coordinate splits into its high and low parts. */
    static final int CENTER_SPLIT = 4096;

    private VoidstoneRoom() {
    }

    /**
     * @return the texture matrix for the room the player stands in, as the client holds it now
     */
    public static Matrix4f current() {
        return matrixOf(ClientRoomEdges.bounds());
    }

    /**
     * Packs a room into the texture matrix the voidstone shader reads: the
     * center's x high and low, then its z high and low, down the first column,
     * and the extent atop the second.
     *
     * @param bounds the room's boundary, empty where no room is known
     * @return the matrix
     */
    static Matrix4f matrixOf(Optional<RoomExtent.Bounds> bounds) {
        Matrix4f packed = new Matrix4f().zero();
        bounds.ifPresent(room -> packed
                .m00(Math.floorDiv(room.centerX(), CENTER_SPLIT))
                .m01(Math.floorMod(room.centerX(), CENTER_SPLIT))
                .m02(Math.floorDiv(room.centerZ(), CENTER_SPLIT))
                .m03(Math.floorMod(room.centerZ(), CENTER_SPLIT))
                .m10((float) room.extent()));
        return packed;
    }
}
