package com.mercuriusxeno.goo.ability.revert;

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
 * Runs Revert on the mobs its stream holds: a held mob stays frozen while
 * the stream reaches it and goes free a few ticks after it lets go, its
 * progress kept; a rewound adult shrinks into its baby, and a rewound baby,
 * or a mob with no baby form, shrinks to nothing, frozen meanwhile, then
 * drops its spawn egg and is gone.
 * rewind-fills-while-held
 * rewind-shrinks-adult-to-baby-to-egg
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class RevertEvents {

    /**
     * Ticks a held mob stays frozen past the last stream tick that reached
     * it, so the freeze holds across a stream tick that arrives late.
     */
    static final int HOLD_GRACE_TICKS = 3;
    private static final float NOTHING = 0f;
    private static final float WHOLE = 1f;
    private static final int ONE_EGG = 1;

    private RevertEvents() {
    }

    /**
     * Freezes a mob the stream reaches this tick.
     *
     * @param mob the held mob
     * @param now the game time
     */
    public static void hold(Mob mob, long now) {
        mob.setData(GooAttachments.REVERTING, revertingOf(mob).heldTo(now + HOLD_GRACE_TICKS));
        mob.setNoAi(true);
    }

    /**
     * Answers whether a mob is shrinking into its egg, which no further
     * Revert changes.
     *
     * @param mob the mob
     * @return true once its shrink into an egg has begun
     */
    public static boolean vanishing(Mob mob) {
        return revertingOf(mob).vanishing();
    }

    private static Reverting revertingOf(Mob mob) {
        return mob.hasData(GooAttachments.REVERTING) ? mob.getData(GooAttachments.REVERTING) : Reverting.NONE;
    }

    /**
     * Turns an adult into a baby, its model shrinking from the adult's size
     * to the baby's over the shrink.
     *
     * @param mob   the adult
     * @param ticks the game ticks the shrink takes
     */
    public static void shrinkIntoBaby(Mob mob, int ticks) {
        float adultHeight = mob.getBbHeight();
        mob.setBaby(true);
        mob.refreshDimensions();
        EntityVisuals.sendToWatchers(mob,
                new ModelShrinkPayload(mob.getId(), adultOverBaby(adultHeight, mob.getBbHeight()), WHOLE, true, ticks));
    }

    /**
     * The baby model's size that matches the adult it was, as a multiple of
     * the baby's own: the baby's model draws at its own size from the tick it
     * becomes one, so the shrink starts it at the adult's height.
     * rewind-shrinks-adult-to-baby-to-egg
     *
     * @param adultHeight the adult's height
     * @param babyHeight  the baby's height
     * @return the multiple, whole where either height is unknown
     */
    static float adultOverBaby(float adultHeight, float babyHeight) {
        return adultHeight > 0f && babyHeight > 0f ? adultHeight / babyHeight : WHOLE;
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
        mob.setData(GooAttachments.REVERTING, new Reverting(vanishAt, vanishAt));
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
                || !mob.hasData(GooAttachments.REVERTING)) {
            return;
        }
        Reverting reverting = mob.getData(GooAttachments.REVERTING);
        long now = mob.level().getGameTime();
        if (reverting.vanishedAt(now)) {
            DropItemStep.dropOwnSpawnEgg(mob, ONE_EGG);
            mob.discard();
        } else if (reverting.releasedAt(now)) {
            mob.removeData(GooAttachments.REVERTING);
            mob.setNoAi(false);
        }
    }
}
