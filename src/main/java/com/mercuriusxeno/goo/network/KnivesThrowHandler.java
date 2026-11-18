package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.OwnMinions;
import com.mercuriusxeno.goo.ability.ThrowRange;
import com.mercuriusxeno.goo.ability.crystal.KnifeFan;
import com.mercuriusxeno.goo.ability.crystal.KnifeSplit;
import com.mercuriusxeno.goo.ability.program.DamageStep;
import com.mercuriusxeno.goo.registry.GooServerState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-side handler for a Knives throw: during a standing hold, each
 * painted mob still alive, in reach and not the caster's own minion takes
 * its share of the knives in the fan, split evenly in painted order and
 * capped at the knives that kill it, and each thrown knife leaves its fan
 * slot homing on its mob. Knives no mob takes stay in the fan, so a throw
 * with nothing painted throws nothing; thrown knives leave the hold, their
 * prices spent.
 * decision knives-form-on-right-click-and-throw-on-left-click
 * decision knives-split-evenly-capped-at-the-kill
 * decision knives-with-no-target-stay-in-the-fan
 * decision knives-home-on-their-painted-mob
 */
public final class KnivesThrowHandler {

    private KnivesThrowHandler() {
    }

    /**
     * Handles the throw payload on the server thread.
     *
     * @param payload the throw payload
     * @param context the network context
     */
    public static void handle(KnivesThrowPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                throwKnives(player, payload);
            }
        });
    }

    /**
     * Throws the knives of the player's standing hold at the mobs it painted.
     *
     * @param player  the throwing player
     * @param payload the throw payload
     */
    public static void throwKnives(ServerPlayer player, KnivesThrowPayload payload) {
        GooServerState state = GooServerState.of(player.level().getServer());
        KnifeHolds holds = state.knifeHolds();
        holds.heldBy(player).ifPresent(held -> {
            int inTheFan = holds.formed(player);
            List<LivingEntity> mobs = targetsOf(player, payload.painted());
            DamageStep damage = held.ability().behaviors().stream().filter(DamageStep.class::isInstance)
                    .map(DamageStep.class::cast).findFirst().orElse(null);
            KnifeSplit.Split split = KnifeSplit.split(inTheFan,
                    mobs.stream().map(mob -> knivesToKill(mob, player, damage)).toList());
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getViewVector(1f);
            int slot = 0;
            for (int mob = 0; mob < mobs.size(); mob++) {
                for (int knife = 0; knife < split.perMob().get(mob); knife++) {
                    state.homingKnives().launch(player, held.gooType(), held.ability().id().toString(),
                            mobs.get(mob), KnifeFan.pointOf(eye, look, KnifeFan.slotOf(slot, inTheFan)));
                    slot++;
                }
            }
            holds.threw(player, split.thrown());
        });
    }

    /**
     * The painted mobs a throw may strike, in painted order: alive, in reach,
     * and not the caster's own minion.
     *
     * @param player  the throwing player
     * @param painted the painted mobs' entity ids
     * @return the mobs
     */
    private static List<LivingEntity> targetsOf(ServerPlayer player, List<Integer> painted) {
        List<LivingEntity> mobs = new ArrayList<>();
        for (int id : painted) {
            if (player.level().getEntity(id) instanceof LivingEntity mob && strikable(player, mob)
                    && !mobs.contains(mob)) {
                mobs.add(mob);
            }
        }
        return mobs;
    }

    // A mob a knife may strike: alive, not the thrower, in reach, and not the thrower's own minion.
    private static boolean strikable(ServerPlayer player, LivingEntity mob) {
        return mob.isAlive() && mob != player && mob.distanceTo(player) <= ThrowRange.KNIVES_AND_DART_REACH
                && !OwnMinions.ownMinionOf(player, mob);
    }

    /**
     * The fewest knives whose damage, as the Knives damage step lands it on
     * the mob, takes its health to zero; a mob no knife hurts takes every knife offered.
     *
     * @param mob     the mob
     * @param thrower the throwing player
     * @param damage  the ability's damage step, or null for none
     * @return the knives-to-kill, at least 1
     */
    static int knivesToKill(LivingEntity mob, ServerPlayer thrower, @Nullable DamageStep damage) {
        float landed = damage == null ? 0f : damage.landedOn(mob, thrower);
        return landed <= 0f ? Integer.MAX_VALUE : Math.max(1, (int) Math.ceil(mob.getHealth() / landed));
    }
}
