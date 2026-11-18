package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.EntityHost;
import com.mercuriusxeno.goo.ability.program.EntityScan;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.SpikesStep;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.SpikesRisePayload;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import java.util.Set;

/**
 * The strike Spikes goo makes on a living thing standing on it: a spike
 * runs through the thing and the spikes step's strike runs on it, once the
 * step's cooldown has passed since the goo last struck it, and each strike
 * rolls the step's spend chance of taking one layer from the cell under it,
 * the cell gone with its last layer. A strike credits the player who placed
 * the cell, and nobody where no player did, and never lands on that player
 * (operator ruling 2026-11-17: an ability never attacks its caster).
 * spikes-is-a-coat-of-metal-goo-that-impales-what-walks-on-it
 * metal-spends-charge-by-chance
 * urchin-rests-bristled-and-spears
 * standing-goo-thing-credits-whoever-placed-it
 */
final class SpikesImpale {

    private SpikesImpale() {
    }

    /**
     * Impales a thing standing on a Spikes cell, where the step strikes it,
     * it is not the player who sprayed the cell and its cooldown has passed,
     * and rolls the cell's spend.
     *
     * @param level  the server level
     * @param pos    the cell
     * @param state  the cell's state
     * @param entity the thing on the cell
     * @param step   the spikes step the cell's laying ability runs
     * @param placer the player the strike credits, null for nobody
     */
    static void impale(ServerLevel level, BlockPos pos, BlockState state, Entity entity, SpikesStep step,
                       @Nullable ServerPlayer placer) {
        if (!(entity instanceof LivingEntity living) || !dueToStrike(level, pos, living, step)) {
            return;
        }
        living.setData(GooAttachments.SPIKES_STRUCK_AT, level.getGameTime());
        EntityVisuals.sendToWatchers(living,
                new SpikesRisePayload(pos, living.getId(), step.retractShink(), step.retractFrame()));
        new ProgramBehavior(step.strike()).tick(EntityHost.struckByField(level, living, placer));
        int layers = state.getValue(VolumeCellBlock.LAYERS);
        int left = layersAfterStrike(layers, level.getRandom().nextDouble(), step.spendChance());
        if (left < layers) {
            level.setBlock(pos, left > 0 ? state.setValue(VolumeCellBlock.LAYERS, left)
                    : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Whether a cell strikes a living thing standing on it now: the step
     * strikes it, it is not the player who sprayed the cell, and the
     * cooldown since the goo last struck it has passed.
     *
     * @param level  the server level
     * @param pos    the cell
     * @param living the thing on the cell
     * @param step   the spikes step
     * @return true where the cell strikes the thing this tick
     */
    private static boolean dueToStrike(ServerLevel level, BlockPos pos, LivingEntity living, SpikesStep step) {
        return strikes(living, step) && !SprayerSpared.spares(living, CellPlacers.of(level).placerAt(pos))
                && due(level.getGameTime(), living.getData(GooAttachments.SPIKES_STRUCK_AT), step.cooldown());
    }

    /**
     * Whether the goo strikes a thing walking on it: a living thing every
     * filter of the step keeps, so a sneaking player is spared.
     *
     * @param entity the thing on the cell
     * @param step   the spikes step
     * @return true where the thing is struck
     */
    static boolean strikes(Entity entity, SpikesStep step) {
        return entity instanceof LivingEntity && EntityScan.passes(entity, Set.copyOf(step.where()), null);
    }

    /**
     * Whether the cooldown since the goo last struck a thing has passed.
     *
     * @param now          the game time
     * @param lastStruckAt the game time the goo last struck the thing, negative for never
     * @param cooldown     the ticks between two strikes on one thing
     * @return true where the thing may be struck now
     */
    static boolean due(long now, long lastStruckAt, int cooldown) {
        return lastStruckAt < 0 || now - lastStruckAt >= cooldown;
    }

    /**
     * The layers a cell holds after one strike: one fewer where the strike's
     * draw falls under the spend chance, as many where it does not.
     *
     * @param layers the layers the cell holds before the strike
     * @param draw   the strike's random draw in [0, 1)
     * @param chance the spend chance in [0, 1]
     * @return the layers left, 0 where the strike takes the last
     */
    static int layersAfterStrike(int layers, double draw, double chance) {
        return draw < chance ? layers - 1 : layers;
    }
}
