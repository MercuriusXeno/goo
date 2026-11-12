package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import net.minecraft.world.phys.AABB;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.function.Predicate;

/**
 * The draws the copies of an Outsider room's blocks make in one frame: one
 * instance of the room's mesh for each copy the camera can see, each
 * translated by its copy's offset. The translations travel to the room copy
 * shaders as a texel buffer, each offset's east then south in fixed point,
 * sixteen bits of whole blocks and sixteen of fraction, little end first.
 * decision room-tiles-to-render-distance-held-to-the-picture
 */
public final class RoomCopyDraws {

    /** Bytes one copy's translation takes: east, then south, four bytes each. */
    public static final int BYTES_PER_COPY = 8;
    /** The fixed point's one: sixteen bits of fraction. */
    private static final double FIXED_ONE = 65_536.0;
    private static final double HALF_BLOCK = 0.5;

    private RoomCopyDraws() {
    }

    /**
     * One frame's copies of a room's mesh.
     *
     * @param drawn        the copies drawn, in instance order
     * @param translations each drawn copy's translation, in instance order, as the shaders read them
     */
    public record Draws(List<RoomCopies.Offset> drawn, ByteBuffer translations) {

        /**
         * @return how many instances of the room's mesh draw
         */
        public int instances() {
            return drawn.size();
        }
    }

    /**
     * The draws a room's copies make: one for each copy whose box the camera
     * sees, carrying that copy's offset as its translation.
     *
     * @param bounds  the room's boundary
     * @param offsets the room's copies
     * @param bottom  the lowest the room's blocks reach
     * @param top     the highest the room's blocks reach
     * @param seen    whether the camera sees a box
     * @return the draws
     */
    public static Draws of(RoomExtent.Bounds bounds, List<RoomCopies.Offset> offsets, double bottom, double top,
            Predicate<AABB> seen) {
        AABB room = boxOf(bounds, bottom, top);
        List<RoomCopies.Offset> drawn = offsets.stream()
                .filter(offset -> seen.test(room.move(offset.east(), 0, offset.south()))).toList();
        ByteBuffer translations = ByteBuffer.allocateDirect(Math.max(1, drawn.size()) * BYTES_PER_COPY)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (RoomCopies.Offset offset : drawn) {
            translations.putInt(fixed(offset.east())).putInt(fixed(offset.south()));
        }
        return new Draws(drawn, translations.flip());
    }

    /**
     * The translation the shaders read for one drawn copy.
     *
     * @param translations the draws' translations
     * @param instance     the copy's instance
     * @return its offset
     */
    public static RoomCopies.Offset translationOf(ByteBuffer translations, int instance) {
        ByteBuffer read = translations.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int at = instance * BYTES_PER_COPY;
        return new RoomCopies.Offset(read.getInt(at) / FIXED_ONE, read.getInt(at + Integer.BYTES) / FIXED_ONE);
    }

    /**
     * The box a room's blocks stand in: its boundary across, its blocks' reach up.
     *
     * @param bounds the room's boundary
     * @param bottom the lowest the room's blocks reach
     * @param top    the highest the room's blocks reach
     * @return the box
     */
    public static AABB boxOf(RoomExtent.Bounds bounds, double bottom, double top) {
        double x = bounds.centerX() + HALF_BLOCK;
        double z = bounds.centerZ() + HALF_BLOCK;
        return new AABB(x - bounds.extent(), bottom, z - bounds.extent(), x + bounds.extent(), top,
                z + bounds.extent());
    }

    private static int fixed(double blocks) {
        return (int) Math.round(blocks * FIXED_ONE);
    }
}
