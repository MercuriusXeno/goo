package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.Optional;
import java.util.UUID;

/**
 * Links creatures' fates by a player's casts in turn: the first creature the
 * player's link strikes waits for its pair, and the next one struck shares
 * its fate for the link's time.
 * shared-fate-pools-health-and-status
 */
public final class FateLinks {

    private FateLinks() {
    }

    /**
     * Strikes a creature with a player's link: it waits for its pair, or
     * pairs with the creature already waiting.
     *
     * @param level    the creature's level
     * @param caster   the player casting the link
     * @param struck   the creature struck
     * @param duration the ticks the link stands once paired
     * @return true when the strike paired two creatures
     */
    public static boolean strike(ServerLevel level, ServerPlayer caster, LivingEntity struck, int duration) {
        Optional<UUID> waiting = caster.getData(GooAttachments.FATE_WAITING);
        Entity first = waiting.map(level::getEntity).orElse(null);
        if (first instanceof LivingEntity partner && partner.isAlive() && partner != struck) {
            link(level, partner, struck, duration);
            caster.setData(GooAttachments.FATE_WAITING, Optional.empty());
            return true;
        }
        caster.setData(GooAttachments.FATE_WAITING, Optional.of(struck.getUUID()));
        return false;
    }

    /**
     * Links two creatures' fates for a count of ticks.
     *
     * @param level    the level
     * @param first    one creature
     * @param second   the other
     * @param duration the ticks the link stands
     */
    public static void link(ServerLevel level, LivingEntity first, LivingEntity second, int duration) {
        long expiresAt = level.getGameTime() + duration;
        first.setData(GooAttachments.SHARED_FATE, new SharedFate(second.getUUID(), expiresAt, first.getHealth()));
        second.setData(GooAttachments.SHARED_FATE, new SharedFate(first.getUUID(), expiresAt, second.getHealth()));
    }

    /**
     * The creature a creature shares a standing fate with, where its level
     * holds it; a faded or broken link is ended here.
     *
     * @param creature the creature
     * @return the partner, empty for none
     */
    public static Optional<LivingEntity> partnerOf(LivingEntity creature) {
        if (!(creature.level() instanceof ServerLevel level) || !creature.hasData(GooAttachments.SHARED_FATE)) {
            return Optional.empty();
        }
        SharedFate fate = creature.getData(GooAttachments.SHARED_FATE);
        if (!fate.standsAt(level.getGameTime())) {
            creature.removeData(GooAttachments.SHARED_FATE);
            return Optional.empty();
        }
        return livingPartner(level, fate);
    }

    private static Optional<LivingEntity> livingPartner(ServerLevel level, SharedFate fate) {
        return level.getEntity(fate.partner()) instanceof LivingEntity partner && partner.isAlive()
                ? Optional.of(partner) : Optional.empty();
    }
}
