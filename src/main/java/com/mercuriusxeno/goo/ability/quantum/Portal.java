package com.mercuriusxeno.goo.ability.quantum;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A Quantum portal: a frame two blocks long on the faces a Portal cast
 * struck, one block wide. On a wall it stands two tall, its first block the
 * lower; on a floor or ceiling it lies flat along the way the caster faced.
 * A traveller steps in only by touching the frame's face, flush.
 * portals-link-by-looking-through-portal-vision
 *
 * @param block the frame's first block
 * @param face  the face of its blocks the frame covers
 * @param along the way from the first block to the second: up on a wall, the caster's facing on a floor or ceiling
 */
public record Portal(GlobalPos block, Direction face, Direction along) {

    /** Saves a portal. */
    public static final Codec<Portal> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            GlobalPos.CODEC.fieldOf("block").forGetter(Portal::block),
            Direction.CODEC.fieldOf("face").forGetter(Portal::face),
            Direction.CODEC.optionalFieldOf("along", Direction.UP).forGetter(Portal::along)
    ).apply(inst, Portal::new));

    /** Sends a portal. */
    public static final StreamCodec<ByteBuf, Portal> STREAM_CODEC = StreamCodec.composite(
            GlobalPos.STREAM_CODEC, Portal::block,
            Direction.STREAM_CODEC, Portal::face,
            Direction.STREAM_CODEC, Portal::along,
            Portal::new);

    private static final double HALF = 0.5;

    /**
     * The frame a cast lays on a struck face, where two solid blocks lie with
     * open cells in front: on a wall, the struck block and the one above it,
     * else the one below; on a floor or ceiling, the struck block and the next
     * along the caster's facing, else the one behind.
     *
     * @param solid  whether a block stands solid to hold a frame
     * @param open   whether a cell holds nothing a body collides with
     * @param struck the struck block
     * @param face   the struck face
     * @param facing the caster's horizontal facing
     * @return the frame, empty where none fits
     */
    public static Optional<Portal> frameAt(Predicate<BlockPos> solid, Predicate<BlockPos> open, GlobalPos struck,
            Direction face, Direction facing) {
        Direction along = face.getAxis().isHorizontal() ? Direction.UP : facing;
        BlockPos pos = struck.pos();
        return Stream.of(pos, pos.relative(along.getOpposite()))
                .map(first -> new Portal(GlobalPos.of(struck.dimension(), first), face, along))
                .filter(frame -> Stream.of(frame.block().pos(), frame.second())
                        .allMatch(cell -> solid.test(cell) && open.test(cell.relative(face))))
                .findFirst();
    }

    /**
     * @return the frame's second block
     */
    public BlockPos second() {
        return block.pos().relative(along);
    }

    /**
     * @param pos a block
     * @return whether the frame covers that block's face
     */
    public boolean covers(BlockPos pos) {
        return block.pos().equals(pos) || second().equals(pos);
    }

    /**
     * @return the two blocks the frame lies on, as one box
     */
    public AABB blocks() {
        return new AABB(block.pos()).minmax(new AABB(second()));
    }

    /**
     * @return the cell in front of the frame's first block
     */
    public BlockPos mouth() {
        return block.pos().relative(face);
    }

    /**
     * @return the middle of the frame's face
     */
    public Vec3 faceCenter() {
        return blocks().getCenter().add(face.getUnitVec3().scale(HALF));
    }

    /**
     * @return the frame as a gate the world can be seen through
     */
    public Gate gate() {
        return new Gate(block.dimension(), blocks(), face, up());
    }

    /**
     * The frame's up: along its length, so a wall frame's up is world up and
     * a floor frame's up runs the way the caster faced.
     *
     * @return the unit up
     */
    public Vec3 up() {
        return along.getUnitVec3();
    }
}
