package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.ExplosionMarch;
import com.mercuriusxeno.goo.ability.program.ExplosionMode;
import com.mercuriusxeno.goo.ability.program.GooExplosion;
import com.mercuriusxeno.goo.ability.program.NitroStep;
import com.mercuriusxeno.goo.registry.GooServerState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Nitro: the volume cell bound to Unstable's goo. Anything that touches
 * it, heats it, shocks it, pulses it or strikes it sets it off, as
 * NitroContact rules, Vacuum and an unstable goo item sparing it: the cell is spent and explodes with
 * Blast's explosion at the power its layers give, and every Nitro cell the
 * blast reaches goes off in turn a beat later, as a blast lights TNT, so a
 * field of it detonates outward and the more goo lies there the deadlier
 * the whole blast.
 * nitro-is-unstables-goo-that-explodes-in-a-chain-by-its-quantity
 * nitro-explodes-when-touched-heated-shocked-pulsed-or-struck
 * vacuum-and-unstable-goo-items-do-not-set-nitro-off
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
        setOffBy(level, pos, entity);
        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected void entityInside(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                @NonNull Entity entity, @NonNull InsideBlockEffectApplier effects,
                                boolean pastEdges) {
        setOffBy(level, pos, entity);
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

    /** A redstone signal or heat reaching the cell as a neighbor changes sets it off. */
    @Override
    protected void neighborChanged(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                   @NonNull Block neighbor, @Nullable Orientation orientation, boolean movedByPiston) {
        senseAround(level, pos);
        super.neighborChanged(state, level, pos, neighbor, orientation, movedByPiston);
    }

    /** A cell laid where a signal or heat already reaches goes off as it lands. */
    @Override
    protected void onPlace(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                           @NonNull BlockState old, boolean movedByPiston) {
        super.onPlace(state, level, pos, old, movedByPiston);
        senseAround(level, pos);
    }

    /** A hit with a tool or a fist strikes it. */
    @Override
    protected void attack(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                          @NonNull Player player) {
        actOn(level, pos, NitroContact.STRIKE);
        super.attack(state, level, pos, player);
    }

    /** An attempt to break it strikes it, so it goes off rather than giving up its goo. */
    @Override
    public @NonNull BlockState playerWillDestroy(@NonNull Level level, @NonNull BlockPos pos,
                                                 @NonNull BlockState state, @NonNull Player player) {
        actOn(level, pos, NitroContact.STRIKE);
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** A projectile landing on it strikes it. */
    @Override
    protected void onProjectileHit(@NonNull Level level, @NonNull BlockState state, @NonNull BlockHitResult hit,
                                   @NonNull Projectile projectile) {
        actOn(level, hit.getBlockPos(), NitroContact.STRIKE);
        super.onProjectileHit(level, state, hit, projectile);
    }

    /**
     * Answers something acting on the cell as the contact table rules: a
     * contact that detonates spends the cell and explodes it at once, and
     * one that holds leaves it be.
     *
     * @param level   the level the cell stands in
     * @param pos     the cell
     * @param contact what acts on it
     */
    public void actOn(Level level, BlockPos pos, NitroContact contact) {
        if (contact.reaction() != NitroReaction.DETONATE || !(level instanceof ServerLevel server)) {
            return;
        }
        BlockState state = server.getBlockState(pos);
        if (state.is(this)) {
            NitroStep.in(layingProgram(server)).ifPresent(nitro -> {
                ServerPlayer placer = creditedPlayerAt(server, pos);
                server.removeBlock(pos, false);
                detonate(server, pos, nitro.powerAt(layersOf(state)), placer);
            });
        }
    }

    /**
     * Sets the cell off when an entity in the world touches it, as the
     * contact it makes rules.
     *
     * @param level  the level the cell stands in
     * @param pos    the cell
     * @param entity the entity touching it
     */
    private void setOffBy(Level level, BlockPos pos, Entity entity) {
        if (entity.isAlive() && !entity.isSpectator()) {
            actOn(level, pos, NitroContact.madeBy(entity));
        }
    }

    /**
     * Pulses the cell where a redstone signal reaches it and heats it where
     * a block beside it burns.
     *
     * @param level the level the cell stands in
     * @param pos   the cell
     */
    private void senseAround(Level level, BlockPos pos) {
        if (level.hasNeighborSignal(pos)) {
            actOn(level, pos, NitroContact.PULSE);
        } else if (Arrays.stream(Direction.values()).anyMatch(side ->
                NitroContact.heats(level.getBlockState(pos.relative(side))))) {
            actOn(level, pos, NitroContact.HEAT);
        }
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
