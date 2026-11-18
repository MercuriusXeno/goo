package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.GooHitCredit;
import com.mercuriusxeno.goo.ability.program.MoltenStep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

/**
 * Blaze's Molten cell: the volume cell bound to Blaze's molten goo, hot as
 * lava. A living thing touching it is set alight and hit with fire as lava
 * hits it, by the damage and burn seconds the molten step names, the hit
 * waiting out the thing's invulnerability frames so it lands at lava's pace;
 * each hit that lands takes a layer off the cell. A thing fire cannot hurt
 * takes no hit and costs no layer, and an item is never touched, so drops
 * and the items a cook rests survive. The hit names the player who placed
 * the cell. On the client it sizzles, spits a little fire and smokes, now
 * and then.
 * molten-burns-mobs-as-hard-as-lava-and-a-hit-spends-a-layer
 * molten-sizzles-spits-sparse-fire-particles-and-smokes
 * molten-deals-no-fire-damage-to-items
 * standing-goo-thing-credits-whoever-placed-it
 */
public class MoltenCellBlock extends VolumeCellBlock {

    /** How fast a spat flame rises, in blocks a tick. */
    private static final double FLAME_RISE = 0.04;
    /** How fast a smoke puff rises, in blocks a tick. */
    private static final double SMOKE_RISE = 0.02;
    /** The sizzle's volume, a soft hiss. */
    private static final float SIZZLE_VOLUME = 0.2f;
    /** The sizzle's lowest pitch, vanilla's fizz pitched up. */
    private static final float SIZZLE_PITCH = 2.0f;
    /** How far above its lowest pitch a sizzle may play. */
    private static final float SIZZLE_PITCH_SPREAD = 0.6f;

    /**
     * Creates a Molten cell.
     *
     * @param properties the block properties
     * @param binding    the Molten binding
     */
    public MoltenCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
    }

    /**
     * A Molten cell's burn hurts, so the cell remembers who placed it.
     * standing-goo-thing-credits-whoever-placed-it
     */
    @Override
    protected boolean creditsItsPlacer() {
        return true;
    }

    /**
     * Burns a living thing standing on the cell.
     */
    @Override
    public void stepOn(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state,
                       @NonNull Entity entity) {
        burn(level, pos, entity);
        super.stepOn(level, pos, state, entity);
    }

    /**
     * Burns a living thing inside the cell's space, wading in it or standing
     * on a cell too thin to be the block under its feet.
     */
    @Override
    protected void entityInside(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                @NonNull Entity entity, @NonNull InsideBlockEffectApplier effects,
                                boolean pastEdges) {
        burn(level, pos, entity);
        super.entityInside(state, level, pos, entity, effects, pastEdges);
    }

    /**
     * Sets a living thing fire can hurt alight and hits it with lava's fire,
     * naming the cell's placer, and takes a layer off the cell where the hit
     * lands.
     *
     * @param level  the level the cell stands in
     * @param pos    the cell
     * @param entity the entity touching the cell
     */
    private void burn(Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof LivingEntity living)
                || living.fireImmune()) {
            return;
        }
        MoltenStep.in(layingProgram(level)).ifPresent(molten -> {
            living.igniteForSeconds(molten.burnSeconds());
            DamageSource fire = GooHitCredit.credit(living.damageSources().lava(), living,
                    creditedPlayerAt(server, pos));
            if (living.hurtServer(server, fire, molten.damage())) {
                spendALayer(server, pos);
            }
        });
    }

    /**
     * Shows the cell's heat on the client: now and then a little flame, a
     * puff of smoke or a sizzle from its top surface, as MoltenSizzle picks.
     * The server sends nothing for it.
     * molten-sizzles-spits-sparse-fire-particles-and-smokes
     */
    @Override
    public void animateTick(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                            @NonNull RandomSource random) {
        super.animateTick(state, level, pos, random);
        MoltenSizzle.pick(random, state.getValue(LAYERS)).ifPresent(puff -> {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + puff.height();
            double z = pos.getZ() + random.nextDouble();
            switch (puff.emission()) {
                case FLAME -> level.addParticle(ParticleTypes.SMALL_FLAME, x, y, z, 0, FLAME_RISE, 0);
                case SMOKE -> level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, SMOKE_RISE, 0);
                case SIZZLE -> level.playLocalSound(x, y, z, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS,
                        SIZZLE_VOLUME, SIZZLE_PITCH + random.nextFloat() * SIZZLE_PITCH_SPREAD, false);
            }
        });
    }

    /**
     * Takes one layer off the cell, which is gone when its last layer goes.
     *
     * @param level the server level
     * @param pos   the cell
     */
    private void spendALayer(ServerLevel level, BlockPos pos) {
        BlockState cell = level.getBlockState(pos);
        if (!cell.is(this)) {
            return;
        }
        int left = cell.getValue(LAYERS) - 1;
        level.setBlock(pos, left > 0 ? cell.setValue(LAYERS, left) : Blocks.AIR.defaultBlockState(), UPDATE_ALL);
    }
}
