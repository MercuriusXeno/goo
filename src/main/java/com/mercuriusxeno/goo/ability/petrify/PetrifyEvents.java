package com.mercuriusxeno.goo.ability.petrify;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.network.AilmentPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Keeps a petrified mob a statue: the tick it fills its gauge its AI stops
 * and its motion zeroes, and every tick after its motion stays zero and its
 * watchers keep drawing it encased in stone, a client that starts watching
 * later picking the encasement up within one refresh
 * (decision petrify-stone-encasement-and-calcify-map).
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class PetrifyEvents {

    /** Ticks between one encasement send and the next. */
    static final int ENCASEMENT_REFRESH_TICKS = 20;
    /** Each encasement send lasts past the next, so the stone never fades between them. */
    static final int ENCASEMENT_TICKS = ENCASEMENT_REFRESH_TICKS * 2;

    private PetrifyEvents() {
    }

    /**
     * Turns a mob into a statue: no AI, no motion, encased in stone.
     *
     * @param mob the mob whose gauge filled
     */
    public static void becomeStatue(Mob mob) {
        mob.setNoAi(true);
        mob.setDeltaMovement(Vec3.ZERO);
        encase(mob);
    }

    /**
     * Holds each statue still and refreshes its encasement on its watchers.
     *
     * @param event the entity tick event
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || !(entity instanceof Mob mob)
                || !mob.hasData(GooAttachments.PETRIFICATION) || !mob.getData(GooAttachments.PETRIFICATION).statue()) {
            return;
        }
        mob.setDeltaMovement(Vec3.ZERO);
        if (refreshesOn(mob.tickCount)) {
            encase(mob);
        }
    }

    /**
     * Whether a statue's encasement resends on a tick.
     *
     * @param tickCount the statue's tick count
     * @return true once every refresh period
     */
    static boolean refreshesOn(int tickCount) {
        return tickCount % ENCASEMENT_REFRESH_TICKS == 0;
    }

    private static void encase(Mob mob) {
        EntityVisuals.sendToWatchers(mob, new AilmentPayload(mob.getId(), AilmentKind.PETRIFY, ENCASEMENT_TICKS));
    }
}
