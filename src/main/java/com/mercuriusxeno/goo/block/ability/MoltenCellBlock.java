package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.GooHitCredit;
import com.mercuriusxeno.goo.ability.program.MoltenStep;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
 * the cell.
 * molten-burns-mobs-as-hard-as-lava-and-a-hit-spends-a-layer
 * molten-deals-no-fire-damage-to-items
 * standing-goo-thing-credits-whoever-placed-it
 */
public class MoltenCellBlock extends VolumeCellBlock {

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
