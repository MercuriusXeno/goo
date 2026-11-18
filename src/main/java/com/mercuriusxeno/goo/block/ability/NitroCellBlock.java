package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.ExplosionMarch;
import com.mercuriusxeno.goo.ability.program.ExplosionMode;
import com.mercuriusxeno.goo.ability.program.GooExplosion;
import com.mercuriusxeno.goo.ability.program.NitroStep;
import com.mercuriusxeno.goo.registry.GooServerState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Nitro: the volume cell bound to Unstable's goo. A living thing stepping
 * on or touching it sets it off: the cell is spent and explodes with
 * Blast's explosion at the power its layers give, and every Nitro cell the
 * blast reaches goes off in turn a beat later, as a blast lights TNT, so a
 * field of it detonates outward and the more goo lies there the deadlier
 * the whole blast.
 * nitro-is-unstables-goo-that-explodes-in-a-chain-by-its-quantity
 * nitro-explodes-when-touched-heated-shocked-pulsed-or-struck
 * lurker-blob-brightens-then-detonates
 */
public class NitroCellBlock extends VolumeCellBlock {

    /**
     * @param properties the block's properties
     * @param binding    Unstable's Nitro binding
     */
    public NitroCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
    }

    @Override
    public void stepOn(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state,
                       @NonNull Entity entity) {
        setOffBy(level, pos, state, entity);
        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected void entityInside(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                @NonNull Entity entity, @NonNull InsideBlockEffectApplier effects,
                                boolean pastEdges) {
        setOffBy(level, pos, state, entity);
        super.entityInside(state, level, pos, entity, effects, pastEdges);
    }

    /**
     * A blast reaching the cell lights it, so it goes off a beat later in
     * place of dropping its goo.
     */
    @Override
    protected void onExplosionHit(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                                  @NonNull Explosion explosion, @NonNull BiConsumer<ItemStack, BlockPos> drops) {
        light(level, pos, state);
    }

    /**
     * The blast credits the player who placed the cell.
     * standing-goo-thing-credits-whoever-placed-it
     */
    @Override
    protected boolean creditsItsPlacer() {
        return true;
    }

    /**
     * Sets the cell off at once when a living thing touches it.
     *
     * @param level  the level the cell stands in
     * @param pos    the cell
     * @param state  the cell's state
     * @param entity the entity touching it
     */
    private void setOffBy(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (level instanceof ServerLevel server && setsItOff(entity) && server.getBlockState(pos).is(this)) {
            NitroStep.in(layingProgram(server)).ifPresent(nitro -> {
                ServerPlayer placer = creditedPlayerAt(server, pos);
                server.removeBlock(pos, false);
                detonate(server, pos, nitro.powerAt(layersOf(state)), placer);
            });
        }
    }

    /**
     * Whether an entity touching the cell sets it off: a living thing, alive and in the world.
     *
     * @param entity the entity touching the cell
     * @return true where it sets the cell off
     */
    private static boolean setsItOff(Entity entity) {
        return entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator();
    }

    /**
     * Takes a lit cell from the world and lights its fuse, to go off its
     * laying step's beat later at the power its layers give.
     *
     * @param level the level the cell stands in
     * @param pos   the cell
     * @param state the cell's state
     */
    private void light(ServerLevel level, BlockPos pos, BlockState state) {
        GooServerState holder = GooServerState.of(level);
        NitroStep.in(layingProgram(level)).ifPresent(nitro -> {
            ServerPlayer placer = creditedPlayerAt(level, pos);
            level.removeBlock(pos, false);
            if (holder != null) {
                holder.nitroFuses().light(new NitroFuses.Fuse(level.getServer().getTickCount() + nitro.chainTicks(),
                        level, pos, nitro.powerAt(layersOf(state)), this, placer));
            }
        });
    }

    /**
     * Explodes a spent cell with Blast's explosion, lighting first every
     * Nitro cell the blast reaches, so each goes off in turn rather than
     * breaking under it.
     *
     * @param level  the level the cell stood in
     * @param pos    where it stood
     * @param power  the power its layers blast with
     * @param placer the player who placed it, whom the blast credits, or null
     */
    void detonate(ServerLevel level, BlockPos pos, float power, @Nullable ServerPlayer placer) {
        double reach = ExplosionMarch.maxReach(power);
        for (BlockPos next : NitroChain.setOffNext(pos, reach, nitroCellsAround(level, pos, reach))) {
            light(level, next, level.getBlockState(next));
        }
        GooExplosion.detonate(level, Vec3.atCenterOf(pos), power, new GooExplosion.Cut(reach, false),
                ExplosionMode.TNT, GooExplosion.Look.vanilla(), placer);
    }

    private List<BlockPos> nitroCellsAround(ServerLevel level, BlockPos pos, double reach) {
        int span = (int) Math.ceil(reach);
        return BlockPos.betweenClosedStream(pos.offset(-span, -span, -span), pos.offset(span, span, span))
                .filter(cell -> level.getBlockState(cell).is(this)).map(BlockPos::immutable).toList();
    }
}
