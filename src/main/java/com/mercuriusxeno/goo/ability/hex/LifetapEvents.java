package com.mercuriusxeno.goo.ability.hex;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.LeechPayload;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Runs a held lifetap: every hit its player lands heals them by the
 * fraction of the damage dealt, the life flowing from the victim to them as
 * dark purple wisps. Its block on food regeneration lives in
 * FoodDataLifetapMixin, which reads {@link #blocksFoodRegen}.
 * lifetap-trades-regen-for-leech
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class LifetapEvents {

    private LifetapEvents() {
    }

    /**
     * Heals a lifetapped attacker by its fraction of the damage its hit dealt.
     *
     * @param event the damage event, after the damage landed
     */
    @SubscribeEvent
    public static void onDamageDealt(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)
                || !attacker.hasData(GooAttachments.LIFETAP)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        float heal = attacker.getData(GooAttachments.LIFETAP)
                .leechFor(event.getNewDamage(), attacker.level().getGameTime());
        if (heal > 0f && victim != attacker) {
            attacker.heal(heal);
            EntityVisuals.sendToWatchers(victim, new LeechPayload(victim.getId(), attacker.getId()));
        }
    }

    /**
     * Whether a player's food is held from regenerating their health: while
     * a lifetap stands on them.
     *
     * @param player the player
     * @return true while a lifetap stands
     */
    public static boolean blocksFoodRegen(Player player) {
        return player.hasData(GooAttachments.LIFETAP)
                && player.getData(GooAttachments.LIFETAP).standsAt(player.level().getGameTime());
    }
}
