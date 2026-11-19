package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.Goo;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Holds every hit a minion deals off every other minion, so harm that needs
 * no target kills none either: a charmed skeleton's stray arrow, a slime's
 * touch, a charmed creeper's blast. A canceled hit lands no damage, so it
 * ends no charm and no lead; a minion's hit on a mob that is no minion lands.
 * diagnose-and-correct-minions-killing-each-other
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class MinionHits {

    private MinionHits() {
    }

    /**
     * Cancels a hit before it lands where its source entity, the owner of a
     * projectile or a blast, and its victim are both minions.
     *
     * @param event the incoming damage event, before the damage lands
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!event.getEntity().level().isClientSide()
                && cancels(Minions.isMinion(GooHitCredit.causeOf(event.getSource())), Minions.isMinion(event.getEntity()))) {
            event.setCanceled(true);
        }
    }

    /**
     * Whether a hit is canceled before it lands.
     *
     * @param sourceIsMinion whether the hit's source entity holds a charm or a lead
     * @param victimIsMinion whether the entity hit holds a charm or a lead
     * @return true for a hit to cancel
     */
    public static boolean cancels(boolean sourceIsMinion, boolean victimIsMinion) {
        return sourceIsMinion && victimIsMinion;
    }
}
