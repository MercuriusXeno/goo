package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Marks an entity a minion's hit lands on as its player's, as vanilla marks
 * an entity a tamed wolf hits as the wolf's owner's: a kill within the mark
 * drops experience and player-only loot and counts in that player's
 * statistics and advancements. The hit's source stays the minion, so the
 * struck entity fights the minion, and nothing clears the mark when the
 * minion is released, downed or dies.
 * minion-hit-marks-the-struck-mob-as-its-players
 * charmed-mobs-take-the-rallied-kill-rule
 * minion-kill-counts-in-its-players-stats-and-advancements
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class MinionKillCredit {

    /** Ticks the mark stands, the 5 seconds vanilla's own hit and a tamed wolf's leave. */
    public static final int MARK_TICKS = 100;

    private MinionKillCredit() {
    }

    /**
     * Marks the struck entity as the minion's player's once the hit lands,
     * which NeoForge reports before the hit's death, so a killing blow
     * carries the mark. A rally or charm naming no player in the level
     * leaves no mark.
     *
     * @param event the damage event, after the damage landed
     */
    @SubscribeEvent
    public static void onDamageLanded(LivingDamageEvent.Post event) {
        LivingEntity struck = event.getEntity();
        Entity minion = event.getSource().getEntity();
        if (struck.level() instanceof ServerLevel level && Minions.isMinion(minion)) {
            OwnMinions.ownerOf(minion).map(level::getPlayerByUUID)
                    .ifPresent(player -> struck.setLastHurtByPlayer(player, MARK_TICKS));
        }
    }
}
