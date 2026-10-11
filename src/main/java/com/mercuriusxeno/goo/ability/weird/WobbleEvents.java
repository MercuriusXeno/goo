package com.mercuriusxeno.goo.ability.weird;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Softens a wobbled mob's attacks: while its wobble stands, a hit it lands
 * deals no damage and instead knocks its target back by the damage it would
 * have dealt.
 * weird-bounces-and-softens-harm
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class WobbleEvents {

    /** The knockback strength each point of withheld damage buys; vanilla's own hit knocks back 0.4. */
    static final double KNOCKBACK_PER_DAMAGE = 0.2;

    private WobbleEvents() {
    }

    /**
     * Turns a wobbled attacker's hit from damage into knockback.
     *
     * @param event the incoming damage event, before the damage lands
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && wobbles(attacker)) {
            LivingEntity struck = event.getEntity();
            event.setCanceled(true);
            struck.knockback(knockbackFor(event.getAmount()), attacker.getX() - struck.getX(),
                    attacker.getZ() - struck.getZ());
            struck.hurtMarked = true;
        }
    }

    /**
     * The knockback a withheld hit buys.
     *
     * @param damage the damage the hit would have dealt
     * @return the knockback strength
     */
    static double knockbackFor(float damage) {
        return damage * KNOCKBACK_PER_DAMAGE;
    }

    /**
     * Whether a mob's wobble stands on the server; a faded wobble is ended here.
     *
     * @param attacker the attacking mob
     * @return true while its wobble stands
     */
    private static boolean wobbles(LivingEntity attacker) {
        if (attacker.level().isClientSide() || !attacker.hasData(GooAttachments.WOBBLED)) {
            return false;
        }
        if (attacker.getData(GooAttachments.WOBBLED).standsAt(attacker.level().getGameTime())) {
            return true;
        }
        attacker.removeData(GooAttachments.WOBBLED);
        return false;
    }
}
