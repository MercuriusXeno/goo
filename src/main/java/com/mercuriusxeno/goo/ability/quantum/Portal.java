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

/**
 * A Quantum portal: the face of a block a Portal cast struck. Its mouth is
 * the cell in front of the face, where a traveller steps in and comes out.
 * portals-link-by-looking-through-portal-vision
 *
 * @param block the block the portal stands on
 * @param face  the face of the block it covers
 */
public record Portal(GlobalPos block, Direction face) {

    /** Saves a portal. */
    public static final Codec<Portal> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            GlobalPos.CODEC.fieldOf("block").forGetter(Portal::block),
            Direction.CODEC.fieldOf("face").forGetter(Portal::face)
    ).apply(inst, Portal::new));

    /** Sends a portal. */
    public static final StreamCodec<ByteBuf, Portal> STREAM_CODEC = StreamCodec.composite(
            GlobalPos.STREAM_CODEC, Portal::block,
            Direction.STREAM_CODEC, Portal::face,
            Portal::new);

    /** How far off its face a portal's face is measured, so it covers the block. */
    private static final double FACE_LIFT = 0.5;

    /**
     * @return the cell in front of the face, where a traveller steps in and comes out
     */
    public BlockPos mouth() {
        return block.pos().relative(face);
    }

    /**
     * @return the box a traveller's body touches to step into the portal
     */
    public AABB mouthBox() {
        return new AABB(mouth());
    }

    /**
     * @return the middle of the face the portal covers
     */
    public Vec3 faceCenter() {
        return Vec3.atCenterOf(block.pos()).add(face.getUnitVec3().scale(FACE_LIFT));
    }
}
