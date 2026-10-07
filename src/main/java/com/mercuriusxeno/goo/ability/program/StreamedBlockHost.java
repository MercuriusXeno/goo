package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.ChunkViewerSends;
import com.mercuriusxeno.goo.network.UnmakePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.OptionalDouble;

/**
 * The {@link StepHost} over one block a stream holds, built each tick of the
 * hold for each block in the stream's block pass: world actions anchor at
 * the block's center, and an unmake works the block itself.
 * decision unmake-waves-dissolve-by-crucible-cost
 *
 * @param level     the server level
 * @param pos       the held block
 * @param heldTicks the ticks the stream has held this block without a break, 1 on the first
 */
public record StreamedBlockHost(ServerLevel level, BlockPos pos, int heldTicks)
        implements AnchoredWorldHost, UnmakeHost {

    @Override
    public HostKind kind() {
        return HostKind.STREAMED_BLOCK;
    }

    @Override
    public OptionalDouble read(String name) {
        return OptionalDouble.empty();
    }

    @Override
    public BlockPos position() {
        return pos;
    }

    @Override
    public Vec3 anchor() {
        return Vec3.atCenterOf(pos);
    }

    @Override
    public Direction.Axis burstAxis() {
        return Direction.Axis.Y;
    }

    @Override
    public @Nullable GooValue unmadeValue() {
        return ValuedBlocks.valueAt(level, pos);
    }

    @Override
    public int unmakeProgress() {
        return heldTicks;
    }

    @Override
    public void showUnmaking(float fraction) {
        ChunkViewerSends.send(level, pos, new UnmakePayload(pos, fraction), null);
    }

    @Override
    public void unmake(GooContents yield) {
        level.removeBlock(pos, false);
        GooStacks.dropAll(yield, level, pos);
    }
}
