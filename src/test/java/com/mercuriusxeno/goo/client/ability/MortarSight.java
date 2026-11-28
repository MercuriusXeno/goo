package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.BoreDrillShape;
import com.mercuriusxeno.goo.ability.program.BoreStep;
import com.mercuriusxeno.goo.ability.program.BreakPace;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.ability.program.GigaDrill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.phys.Vec3;
import java.util.function.Predicate;

/**
 * An eye line east through a cell before a stone wall, the cell holding Mortar
 * or air, as Bore's client visuals read it.
 */
final class MortarSight {

    static final Vec3 EYE = new Vec3(0.5, 2.62, 0.5);
    static final Vec3 LOOK = new Vec3(1, 0, 0);
    static final double REACH = 4;
    static final boolean GLOVE_RIGHT = true;
    static final CameraEye.Sight SIGHT = new CameraEye.Sight(EYE, LOOK);
    static final ChannelAim AIM = new ChannelAim(EYE.add(LOOK.scale(REACH)), null, 0);
    /** The cell the eye line crosses before the wall. */
    static final BlockPos BEFORE_THE_WALL = new BlockPos(1, 2, 0);
    /** The first stone block on the eye line. */
    static final BlockPos FIRST_STONE = new BlockPos(2, 2, 0);
    static final BoreStep BORE = new BoreStep(TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath("goo", "bore_breakable")), new BreakPace(5, 1.5));
    static final BoreDrillShape GIGA = GigaDrill.SHIPPED.swell(BoreDrillShape.SHIPPED);
    private static final int FULL = 15;
    private static final Predicate<BlockPos> WALL = pos -> pos.getX() >= 2 && pos.getX() <= 3
            && Math.abs(pos.getY() - 2) <= 2 && Math.abs(pos.getZ()) <= 2;

    private MortarSight() {
    }

    /**
     * The blocks along the line, the cell before the wall holding a full Mortar cell or air.
     *
     * @param mortarStands whether the cell before the wall holds Mortar
     * @return the blocks
     */
    static BoreDrill.SightBlocks blocks(boolean mortarStands) {
        Predicate<BlockPos> mortar = pos -> mortarStands && pos.equals(BEFORE_THE_WALL);
        return new BoreDrill.SightBlocks(pos -> !WALL.test(pos) && !mortar.test(pos),
                pos -> mortar.test(pos) ? FULL : 0, WALL);
    }

    /**
     * Whether a cell lets the drill through: anything off the wall, air or the Mortar cell.
     *
     * @return the predicate
     */
    static Predicate<BlockPos> passable() {
        return WALL.negate();
    }

    /**
     * Whether a cell holds a block the bore breaks: the wall.
     *
     * @return the predicate
     */
    static Predicate<BlockPos> breakable() {
        return WALL;
    }
}
