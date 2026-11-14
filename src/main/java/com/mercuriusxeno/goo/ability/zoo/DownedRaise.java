package com.mercuriusxeno.goo.ability.zoo;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

/**
 * Raises a downed mob any heal reaches, Jelly's Heal stream or any other:
 * the fall comes off it, its AI comes back as it stood before the fall, and
 * it hops up off the ground, a short rise rather than the sideways fall run
 * backward. Once up its immunity and death span are gone and it can be
 * targeted again; a rally it still holds stays, so its next lethal blow
 * downs it again.
 * healed-downed-mob-hops-back-up
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class DownedRaise {

    /** The upward speed of the hop a raised mob stands with, short of a jump's. */
    static final double HOP_SPEED = 0.3;

    private DownedRaise() {
    }

    /**
     * Raises a downed mob a heal reaches.
     *
     * @param event the heal event, before the health is restored
     */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide() && event.getAmount() > 0
                && mob.hasData(GooAttachments.DOWNED)) {
            raise(mob);
        }
    }

    /**
     * Takes the fall off a downed mob, gives back its AI and hops it up.
     *
     * @param mob the downed mob
     */
    static void raise(Mob mob) {
        Downed downed = mob.getData(GooAttachments.DOWNED);
        mob.removeData(GooAttachments.DOWNED);
        mob.setNoAi(downed.wasNoAi());
        Vec3 motion = mob.getDeltaMovement();
        mob.setDeltaMovement(motion.x, HOP_SPEED, motion.z);
        mob.hurtMarked = true;
    }
}
