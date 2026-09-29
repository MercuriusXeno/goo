package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.item.*;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.List;

/**
 * Static helpers for crucible right-click interactions: the flint-and-steel spark,
 * goo insertion, and goo extraction. Keeps framework overrides in CrucibleBlock.
 */
final class CrucibleInteraction {

    /** Lowest pitch of the spark sound, matching vanilla's flint and steel. */
    private static final float SPARK_PITCH_BASE = 0.8f;
    /** Pitch spread of the spark sound, matching vanilla's flint and steel. */
    private static final float SPARK_PITCH_SPREAD = 0.4f;

    private CrucibleInteraction() {
    }

    /**
     * Sparks a cold, unfueled crucible with a flint and steel: adds the spark's heat, costs the
     * tool one durability and starts the ignition spray (decision flint-and-steel-sparks-the-crucible).
     * A crucible holding heat or fuel goo consumes the click and nothing happens, so the item's
     * own use never sets fire beside it (decision spark-gate-consumes-the-click).
     *
     * @param stack    the held flint and steel
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @param hand     the hand holding the stack
     * @return SUCCESS when the spark lit the crucible, CONSUME when it holds heat or fuel goo
     */
    static InteractionResult spark(ItemStack stack, CrucibleBlockEntity crucible,
                                   Player player, InteractionHand hand) {
        if (!sparkLights(crucible.heat, FuelGrade.configured(), crucible.fuelStock)) {
            return InteractionResult.CONSUME;
        }
        crucible.addHeat(GooConfig.SPARK_HEAT_TICKS.get());
        stack.hurtAndBreak(1, player, hand);
        Level level = crucible.getLevel();
        if (level != null) {
            level.playSound(null, crucible.getBlockPos(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS,
                    1.0f, level.getRandom().nextFloat() * SPARK_PITCH_SPREAD + SPARK_PITCH_BASE);
        }
        CrucibleMelting.beginIgnitionSpray(crucible);
        return InteractionResult.SUCCESS;
    }

    /**
     * Returns true when a spark would light the crucible: it holds no heat ticks and no
     * fuel goo (decision spark-gate-consumes-the-click).
     *
     * @param heat   the crucible's heat
     * @param grades the fuel grades
     * @param stock  the reservoir
     * @return true if the spark lights
     */
    static boolean sparkLights(CrucibleHeat heat, List<FuelGrade> grades, CrucibleHeat.FuelStock stock) {
        return !heat.canHeat(grades, stock);
    }

    /**
     * Pours a goo directly into the reservoir (bypass, no fuel needed) through the shared
     * goo insert (decision block-insert-shared).
     *
     * @param stack    the held goo
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @return SUCCESS for a goo holding goo, inserted or refused at the cap, so a refused
     *         goo never falls through to goo extraction; TRY_WITH_EMPTY_HAND for an empty one
     */
    static InteractionResult pourGoo(ItemStack stack, CrucibleBlockEntity crucible, Player player) {
        if (GooStacks.volumeOf(stack) <= 0) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        GooInsert.pour(stack, player, (type, volume) -> acceptWholeUnits(crucible, type, volume));
        return InteractionResult.SUCCESS;
    }

    /**
     * Inserts the mB of the offered volume that fit the reservoir under the cap
     * (decision diagnose-then-fix-crucible-goo-duplication).
     *
     * @param crucible the crucible block entity to insert into
     * @param type     the goo type offered
     * @param volume   the volume offered, in mB
     * @return the volume inserted
     */
    private static int acceptWholeUnits(CrucibleBlockEntity crucible, ResourceKey<GooTypeDefinition> type,
                                        int volume) {
        int units = CrucibleInsertion.reservoirUnitsThatFit(crucible, type, 1, volume);
        return units > 0 ? crucible.insertGoo(type, units) : 0;
    }

    /**
     * Drains every type in the reservoir into the player's inventory; what finds no home
     * stays in the crucible (decision drained-goo-fills-carried-containers-first).
     *
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @return SUCCESS if any goo moved, else PASS
     */
    static InteractionResult tryExtractGoo(CrucibleBlockEntity crucible, Player player) {
        return extractInto(crucible.getReservoir(), crucible::extractGoo,
                GooDeposit.intoInventory(player, ItemStack.EMPTY));
    }

    /**
     * Drains a reservoir snapshot through a depositor, drawing only what found a home.
     *
     * @param reservoir the reservoir snapshot
     * @param drawer    removes goo from the reservoir
     * @param depositor where the goo goes
     * @return SUCCESS if any goo moved, else PASS
     */
    static InteractionResult extractInto(GooContents reservoir, GooDeposit.GooDrawer drawer,
                                         GooDeposit.Depositor depositor) {
        return GooDeposit.drainEveryType(reservoir.getAll(), drawer, depositor)
                ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
