package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.LeechGooStep;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.LeechPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Drained life through Leech goo: damage dealt to a living thing standing
 * in Leech goo heals the one who dealt it by a share of the damage dealt,
 * the drained life fraction the goo's laying ability names, where that
 * one is a living entity or a player striking directly; damage with no
 * entity source heals nobody. The life splashes off the victim as hex wisps,
 * with a soul's escape, and each drained strike rolls the chance to take a
 * layer from the Leech cell under its victim, a cell losing its last layer
 * gone.
 * damage-dealt-to-a-mob-in-leech-goo-becomes-drained-life-for-its-source
 * leech-goo-has-a-chance-to-deplete-when-the-spirits-damage
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class LeechDrain {

    private static final double BODY_CENTER = 0.5;
    private static final float DRAIN_VOLUME = 0.9f;
    private static final float DRAIN_PITCH = 0.8f;
    private static final float DRAIN_PITCH_SPREAD = 0.4f;

    private LeechDrain() {
    }

    /**
     * What dealt a hit, as far as drained life asks.
     */
    public enum DrainSource {
        /** No entity dealt it: a fall, a fire, magic. */
        NONE,
        /** An entity dealt it through something else, a projectile or a summoned thing. */
        INDIRECT,
        /** A living entity or a player struck it directly. */
        DIRECT_LIVING;

        /**
         * What dealt a damage source's hit.
         *
         * @param source the damage source
         * @return a direct living striker, an indirect one, or none
         */
        static DrainSource of(DamageSource source) {
            if (source.getEntity() == null) {
                return NONE;
            }
            return source.isDirect() && source.getEntity() instanceof LivingEntity ? DIRECT_LIVING : INDIRECT;
        }
    }

    /**
     * The life a hit drains to its source: the damage dealt times the drained
     * life fraction where a living source struck a victim in Leech goo
     * directly, nothing otherwise, with no cap on what a hit drains.
     *
     * @param damage        the damage the hit dealt
     * @param victimInLeech whether the victim stands in Leech goo
     * @param source        what dealt the hit
     * @param fraction      the share of the damage that drains, as the goo's laying ability names it
     * @return the health its source heals, 0 where it drains none
     */
    static float drainedLife(float damage, boolean victimInLeech, DrainSource source, float fraction) {
        return victimInLeech && source == DrainSource.DIRECT_LIVING ? Math.max(0f, damage) * fraction : 0f;
    }

    /**
     * The layers a Leech cell holds after a strike on a thing on it drains
     * life: one fewer where the draw falls under the deplete chance, as many
     * otherwise.
     * leech-goo-has-a-chance-to-deplete-when-the-spirits-damage
     *
     * @param layers the layers the cell held
     * @param draw   a random draw in [0, 1)
     * @param chance the chance a drained strike takes a layer
     * @return the layers left, 0 where the cell is gone
     */
    static int layersAfterStrike(int layers, double draw, double chance) {
        return draw < chance ? layers - 1 : layers;
    }

    /**
     * The Leech cell a living thing stands in: a slowing cell filling the
     * block its feet are in, or else one holding it up.
     *
     * @param living the living thing
     * @return the cell, empty where no Leech goo caught it
     */
    static Optional<BlockPos> leechCellOf(LivingEntity living) {
        return Stream.of(living.blockPosition(), living.getOnPos())
                .filter(pos -> VolumeCellBlock.hasSurface(living.level().getBlockState(pos).getBlock(),
                        CellSurface.SLOW))
                .findFirst();
    }

    /**
     * The leech goo step the ability that laid a Leech cell runs, naming its
     * drained life fraction and its deplete chance.
     *
     * @param level the level
     * @param cell  the Leech cell
     * @return the step, empty where the cell is gone or its ability is not loaded
     */
    private static Optional<LeechGooStep> leechStepAt(ServerLevel level, BlockPos cell) {
        return level.getBlockState(cell).getBlock() instanceof VolumeCellBlock leechCell
                ? LeechGooStep.in(leechCell.layingProgram(level)) : Optional.empty();
    }

    /**
     * Rolls the deplete chance the cell's laying ability names on the Leech
     * cell a drained strike's victim stands in, taking a layer where the roll
     * falls under it and leaving air where that was the last.
     * leech-goo-has-a-chance-to-deplete-when-the-spirits-damage
     *
     * @param level the level
     * @param cell  the Leech cell under the victim
     * @param leech the cell's laying ability's leech goo step
     */
    private static void depleteAt(ServerLevel level, BlockPos cell, LeechGooStep leech) {
        BlockState state = level.getBlockState(cell);
        int layers = state.getValue(VolumeCellBlock.LAYERS);
        int left = layersAfterStrike(layers, level.getRandom().nextDouble(), leech.depleteChance());
        if (left != layers) {
            level.setBlock(cell, left > 0 ? state.setValue(VolumeCellBlock.LAYERS, left)
                    : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Heals the direct living source of a hit on a victim in Leech goo by the
     * share of the damage the cell's laying ability names, and rolls the
     * deplete on the cell under the victim.
     *
     * @param event the damage event, after the damage landed
     */
    @SubscribeEvent
    public static void onDamageDealt(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)
                || !(event.getSource().getEntity() instanceof LivingEntity source) || source == victim) {
            return;
        }
        Optional<BlockPos> cell = leechCellOf(victim);
        Optional<LeechGooStep> leech = cell.flatMap(pos -> leechStepAt(level, pos));
        float drained = drainedLife(event.getNewDamage(), leech.isPresent(), DrainSource.of(event.getSource()),
                leech.map(LeechGooStep::drainFraction).orElse(0f));
        if (drained > 0f) {
            depleteAt(level, cell.orElseThrow(), leech.orElseThrow());
            source.heal(drained);
            EntityVisuals.sendToWatchers(victim, new LeechPayload(victim.getId(), source.getId(), drained, true));
            victim.level().playSound(null, victim.getX(), victim.getY(BODY_CENTER), victim.getZ(),
                    SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, DRAIN_VOLUME,
                    DRAIN_PITCH + victim.getRandom().nextFloat() * DRAIN_PITCH_SPREAD);
        }
    }
}
