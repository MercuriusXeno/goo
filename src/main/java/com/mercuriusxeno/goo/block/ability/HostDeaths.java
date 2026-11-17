package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.HostStep;
import com.mercuriusxeno.goo.registry.GooServerState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Host goo taking a death's charge as a sculk catalyst does: a mob carrying
 * experience that dies on or near Host goo, within Host's radius of a cell,
 * and that no catalyst claimed, starts a sculk charge at the nearest Host
 * cell worth the experience it would drop, and drops none.
 * mob-dying-on-or-near-host-goo-spreads-sculk-from-the-goo
 */
public final class HostDeaths {

    private HostDeaths() {
    }

    /**
     * Gives a death's charge to the Host goo near it, run after the death's
     * game event reached every catalyst and before its experience drops.
     *
     * @param level  the level the mob died in
     * @param mob    the mob that died
     * @param source what killed it
     */
    public static void claim(ServerLevel level, LivingEntity mob, DamageSource source) {
        if (mob.wasExperienceConsumed() || !mob.shouldDropExperience()) {
            return;
        }
        int charge = mob.getExperienceReward(level, source.getEntity());
        Optional<HostStep> host = Optional.ofNullable(AbilityRegistry.of(level).getAbility(CellBinding.HOST_ABILITY))
                .map(AbilityDefinition::behaviors).flatMap(HostStep::in);
        if (charge <= 0 || host.isEmpty()) {
            return;
        }
        BlockPos death = mob.blockPosition();
        int radius = host.get().radius();
        HostSpread.startingCell(death, hostCellsAround(level, death, radius), radius).ifPresent(cell -> {
            GooServerState.of(level.getServer()).hostSpreaders().start(level, cell, charge);
            mob.skipDropExperience();
        });
    }

    /**
     * The Host cells in the cube a radius spans around a death.
     *
     * @param level  the level
     * @param death  where the mob died
     * @param radius how far from the death to look
     * @return the cells
     */
    private static List<BlockPos> hostCellsAround(ServerLevel level, BlockPos death, int radius) {
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(death.offset(-radius, -radius, -radius),
                death.offset(radius, radius, radius))) {
            if (level.getBlockState(pos).getBlock() instanceof HostCellBlock) {
                cells.add(pos.immutable());
            }
        }
        return cells;
    }
}
