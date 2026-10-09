package com.mercuriusxeno.goo.ability.gate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Where a Dragon Gate lies and where it sets travellers down: a three by
 * three patch of the surface centred on the struck block, in the plane of
 * the struck face, and a spot beside the patch on the open side.
 * Decision dragon-gate-banishes-blocks-and-opens-a-portal.
 */
public final class GateFootprint {

    /** How far the patch reaches from its centre along each axis of its plane. */
    static final int REACH = 1;
    /** How far from the centre a floor gate sets travellers down, past the patch's edge. */
    static final int FLOOR_STEP_ASIDE = REACH + 1;
    /** How far below a ceiling gate travellers drop, so they hang clear of it. */
    static final int CEILING_DROP = 2;

    private GateFootprint() {
    }

    /**
     * The cells a gate covers.
     *
     * @param center the struck block
     * @param face   the struck face, whose plane the patch lies in
     * @return the nine cells, the centre among them
     */
    public static List<BlockPos> patch(BlockPos center, Direction face) {
        List<BlockPos> cells = new ArrayList<>();
        Direction.Axis normal = face.getAxis();
        for (int first = -REACH; first <= REACH; first++) {
            for (int second = -REACH; second <= REACH; second++) {
                cells.add(switch (normal) {
                    case X -> center.offset(0, first, second);
                    case Y -> center.offset(first, 0, second);
                    case Z -> center.offset(first, second, 0);
                });
            }
        }
        return cells;
    }

    /**
     * Where a gate sets a traveller's feet down: beside a floor gate on the
     * surface it lies in, under a ceiling gate, and in front of a wall gate.
     *
     * @param center the gate's centre cell
     * @param face   the face the gate looks out of
     * @return the traveller's feet
     */
    public static Vec3 arrival(BlockPos center, Direction face) {
        BlockPos feet = switch (face) {
            case UP -> center.above().east(FLOOR_STEP_ASIDE);
            case DOWN -> center.below(CEILING_DROP);
            default -> center.relative(face);
        };
        return Vec3.atBottomCenterOf(feet);
    }
}
