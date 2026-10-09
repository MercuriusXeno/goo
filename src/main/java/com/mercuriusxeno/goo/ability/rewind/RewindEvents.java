package com.mercuriusxeno.goo.ability.rewind;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.DropItemStep;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.ModelShrinkPayload;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Runs Rewind on the mobs its stream holds: a held mob stays frozen while
 * the stream reaches it and goes free a few ticks after it lets go, its
 * progress kept; a rewound adult shrinks into its baby, and a rewound baby,
 * or a mob with no baby form, shrinks to nothing, frozen meanwhile, then
 * drops its spawn egg and is gone.
 * rewind-fills-while-held
 * rewind-shrinks-adult-to-baby-to-egg
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class RewindEvents {

    /**
     * Ticks a held mob stays frozen past the last stream tick that reached
     * it, so the freeze holds across a stream tick that arrives late.
     */
    static final int HOLD_GRACE_TICKS = 3;
    private static final float NOTHING = 0f;
    private static final float WHOLE = 1f;
    private static final int ONE_EGG = 1;

    private RewindEvents() {
    }

    /**
     * Freezes a mob the stream reaches this tick.
     *
     * @param mob the held mob
     * @param now the game time
     */
    public static void hold(Mob mob, long now) {
        mob.setData(GooAttachments.REWINDING, rewindingOf(mob).heldTo(now + HOLD_GRACE_TICKS));
        mob.setNoAi(true);
    }

    /**
     * Answers whether a mob is shrinking into its egg, which no further
     * rewind changes.
     *
     * @param mob the mob
     * @return true once its rewind into an egg has begun
     */
    public static boolean vanishing(Mob mob) {
        return rewindingOf(mob).vanishing();
    }

    private static Rewinding rewindingOf(Mob mob) {
        return mob.hasData(GooAttachments.REWINDING) ? mob.getData(GooAttachments.REWINDING) : Rewinding.NONE;
    }

    /**
     * Turns an adult into a baby, its model shrinking from the adult's size
     * to the baby's over the shrink.
     *
     * @param mob   the adult
     * @param ticks the game ticks the shrink takes
     */
    public static void shrinkIntoBaby(Mob mob, int ticks) {
        float adultScale = mob.getScale();
        mob.setBaby(true);
        EntityVisuals.sendToWatchers(mob,
                new ModelShrinkPayload(mob.getId(), adultScale / mob.getScale(), WHOLE, true, ticks));
    }

    /**
     * Starts a mob shrinking to nothing; once the shrink ends it drops its
     * spawn egg and is gone.
     *
     * @param mob   the mob
     * @param now   the game time
     * @param ticks the game ticks the shrink takes
     */
    public static void shrinkIntoEgg(Mob mob, long now, int ticks) {
        long vanishAt = now + ticks;
        mob.setData(GooAttachments.REWINDING, new Rewinding(vanishAt, vanishAt));
        mob.setNoAi(true);
        EntityVisuals.sendToWatchers(mob, new ModelShrinkPayload(mob.getId(), WHOLE, NOTHING, false, ticks));
    }

    /**
     * Frees a mob the stream has let go, and turns a mob whose shrink has
     * ended into its egg.
     *
     * @param event the entity tick event
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || !(entity instanceof Mob mob)
                || !mob.hasData(GooAttachments.REWINDING)) {
            return;
        }
        Rewinding rewinding = mob.getData(GooAttachments.REWINDING);
        long now = mob.level().getGameTime();
        if (rewinding.vanishedAt(now)) {
            DropItemStep.dropOwnSpawnEgg(mob, ONE_EGG);
            mob.discard();
        } else if (rewinding.releasedAt(now)) {
            mob.removeData(GooAttachments.REWINDING);
            mob.setNoAi(false);
        }
    }
}
