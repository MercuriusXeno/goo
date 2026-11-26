package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.GooHitCredit;
import com.mercuriusxeno.goo.ability.blockmap.BlockMap;
import com.mercuriusxeno.goo.ability.blockmap.BlockMaps;
import com.mercuriusxeno.goo.ability.program.InfestStep;
import com.mercuriusxeno.goo.ability.program.UndeadInversion;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * Zombie's Infest cell: the volume cell bound to Infest's goo, the decay
 * effect left standing. On each tick its cadence falls on it gnaws the block
 * under it and the block behind each side it coats a stage along the block
 * map Infest's step names, Decay's, and bites each living thing standing in
 * it for the step's magic damage, healing an undead one by it instead, as
 * Decay's bite does. Each block stage gnawed and each bite landed costs the
 * cell a layer; a cell losing its last layer is gone, and a block the map
 * leaves alone costs nothing. The cell spares the player who
 * placed it, as Decay spares its caster, and credits that player for what
 * its bite kills. The client draws Decay's gnats drifting over it.
 * infest-is-nethers-goo-the-decay-effect-localized-around-the-goo
 * infest-gnaws-the-blocks-it-lies-on-and-bites-what-stands-in-it
 * infest-has-a-chance-to-deplete-as-it-gnaws-and-bites
 * decay-and-hive-heal-an-undead-target
 * heal-and-decay-never-harm-their-caster
 * standing-goo-thing-credits-whoever-placed-it
 */
public class InfestCellBlock extends VolumeCellBlock {

    /** The chance each client tick draws a gnat over the cell. */
    private static final float GNAT_CHANCE = 0.2f;
    /** The most a drawn gnat drifts each tick, in blocks. */
    private static final double GNAT_DRIFT = 0.03;
    /** How far over the cell's top a gnat starts. */
    private static final double GNAT_LIFT = 0.15;
    private static final double HALF = 0.5;

    /**
     * Creates an Infest cell.
     *
     * @param properties the block properties
     * @param binding    the Infest binding
     */
    public InfestCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
    }

    /**
     * Pours as any cell does, then, where the cell still stands, gnaws and
     * bites on a tick its cadence falls on and keeps a tick coming, a
     * pending pour's or the next gnaw's.
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        super.tick(state, level, pos, random);
        if (!level.getBlockState(pos).is(this)) {
            return;
        }
        InfestStep.in(layingProgram(level)).ifPresent(infest -> {
            if (CellReach.actsAt(level.getGameTime(), infest.gnawTicks())) {
                gnawAndBite(level, pos, infest);
            }
            if (level.getBlockState(pos).is(this) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
                level.scheduleTick(pos, this, CellReach.ticksToNextAct(level.getGameTime(), infest.gnawTicks()));
            }
        });
    }

    /**
     * Gnaws what the cell lies on and bites what stands in it in full, then
     * stands the cell a layer lower for each block stage gnawed and each bite
     * landed, gone where they total its layers or more.
     * infest-has-a-chance-to-deplete-as-it-gnaws-and-bites
     *
     * @param level  the server level
     * @param pos    the cell
     * @param infest Infest's step
     */
    private void gnawAndBite(ServerLevel level, BlockPos pos, InfestStep infest) {
        int costs = gnaw(level, pos, level.getBlockState(pos), infest) + bite(level, pos, infest);
        BlockState after = level.getBlockState(pos);
        if (costs == 0 || !after.is(this)) {
            return;
        }
        int left = LayerDeplete.layersAfterCosts(after.getValue(LAYERS), costs);
        level.setBlock(pos, left == 0 ? Blocks.AIR.defaultBlockState() : after.setValue(LAYERS, left), UPDATE_ALL);
    }

    /**
     * Steps each block the cell gnaws one stage along Infest's map.
     *
     * @param level  the server level
     * @param pos    the cell
     * @param state  the cell's state
     * @param infest Infest's step
     * @return the block stages gnawed
     */
    private static int gnaw(ServerLevel level, BlockPos pos, BlockState state, InfestStep infest) {
        Optional<BlockMap> map = BlockMaps.get(infest.map());
        if (map.isEmpty()) {
            return 0;
        }
        int gnawed = 0;
        for (BlockPos block : CellReach.reachedBlocks(pos, side -> coatOn(state, side) != SideCoat.BARE)) {
            Optional<Block> next = map.get().next(level.getBlockState(block).getBlock());
            if (next.isPresent()) {
                level.setBlockAndUpdate(block, next.get().defaultBlockState());
                gnawed++;
            }
        }
        return gnawed;
    }

    /**
     * Bites each living thing standing in the cell.
     *
     * @param level  the server level
     * @param pos    the cell
     * @param infest Infest's step
     * @return the bites landed
     */
    private int bite(ServerLevel level, BlockPos pos, InfestStep infest) {
        Entity placer = creditedPlayerAt(level, pos);
        int landed = 0;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).expandTowards(0, 1, 0),
                living -> living.isAlive() && CellReach.standsIn(pos, living.blockPosition(), living.getOnPos()))) {
            if (biteOnce(level, living, infest.damage(), placer)) {
                landed++;
            }
        }
        return landed;
    }

    /**
     * Heals a wounded undead thing by the bite, spares the cell's placer, or
     * hurts anything else with magic damage credited to the placer.
     * decay-and-hive-heal-an-undead-target
     * undead-player-counts-as-undead-for-heal-and-decay
     * heal-and-decay-never-harm-their-caster
     *
     * @param level  the server level
     * @param living the living thing standing in the cell
     * @param damage the bite's damage
     * @param placer the player who placed the cell, or null for none
     * @return true where the bite healed or hurt
     */
    private static boolean biteOnce(ServerLevel level, LivingEntity living, float damage, @Nullable Entity placer) {
        if (UndeadInversion.inverts(living)) {
            boolean wounded = living.getHealth() < living.getMaxHealth();
            UndeadInversion.healUndead(living, damage, false);
            return wounded;
        }
        return !UndeadInversion.sparesCaster(living, placer)
                && living.hurtServer(level, GooHitCredit.credit(level.damageSources().magic(), living, placer), damage);
    }

    /**
     * An Infest cell's bite hurts, so the cell remembers who placed it.
     * standing-goo-thing-credits-whoever-placed-it
     */
    @Override
    protected boolean creditsItsPlacer() {
        return true;
    }

    /**
     * Draws now and then one of Decay's gnats drifting up off the cell's top.
     * infest-is-nethers-goo-the-decay-effect-localized-around-the-goo
     */
    @Override
    public void animateTick(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                            @NonNull RandomSource random) {
        if (random.nextFloat() >= GNAT_CHANCE) {
            return;
        }
        double top = (double) layersOf(state) / MAX_LAYERS + GNAT_LIFT;
        level.addParticle(GooParticles.GNAT.get(), pos.getX() + random.nextDouble(), pos.getY() + top,
                pos.getZ() + random.nextDouble(), (random.nextDouble() - HALF) * GNAT_DRIFT,
                random.nextDouble() * GNAT_DRIFT, (random.nextDouble() - HALF) * GNAT_DRIFT);
    }
}
