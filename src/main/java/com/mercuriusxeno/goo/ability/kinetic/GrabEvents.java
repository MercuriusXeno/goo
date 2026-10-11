package com.mercuriusxeno.goo.ability.kinetic;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.ability.pulse.StunEvents;
import com.mercuriusxeno.goo.network.AilmentPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Runs Grab on the entities a player's channel holds: a held entity hangs
 * where the hold puts it, its AI and gravity suspended and its limbs
 * flailing, until the channel lets go a few ticks later or a left click
 * throws it along the look.
 * grab-holds-and-throws-a-physics-body
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class GrabEvents {

    /**
     * Ticks a held entity stays held past the last channel tick that reached
     * it, so the hold stands across a channel tick that arrives late.
     */
    static final int HOLD_GRACE_TICKS = 3;
    /** Ticks each channel tick's flail overlay lasts, past the grace so it never gaps. */
    private static final int FLAIL_OVERLAY_TICKS = HOLD_GRACE_TICKS + 2;

    private GrabEvents() {
    }

    /**
     * The entity a player's hold stands on this tick.
     *
     * @param player the holding player
     * @return the held entity, or null where the player holds nothing
     */
    public static @Nullable Entity heldBy(ServerPlayer player) {
        GrabHold hold = player.getData(GooAttachments.GRAB_HOLD);
        if (!hold.standsAt(player.level().getGameTime())) {
            return null;
        }
        Entity held = player.level().getEntity(hold.entityId());
        return held != null && held.isAlive() ? held : null;
    }

    /**
     * Holds an entity at a point this channel tick: suspends it on its first
     * held tick, moves it to the point as far as blocks let it, leaves it
     * no motion, and stretches the
     * player's hold and the entity's.
     *
     * @param player     the holding player
     * @param held       the held entity
     * @param at         where the hold puts the entity's feet
     * @param throwSpeed the speed a throw launches it at, in blocks per tick
     */
    public static void hold(ServerPlayer player, Entity held, Vec3 at, double throwSpeed) {
        long until = player.level().getGameTime() + HOLD_GRACE_TICKS;
        suspend(held, until);
        // Moved rather than placed, so the held body collides with the blocks it is pulled against.
        held.move(MoverType.SELF, at.subtract(held.position()));
        held.setDeltaMovement(Vec3.ZERO);
        held.fallDistance = 0;
        held.hurtMarked = true;
        if (held instanceof ItemEntity item) {
            // A held item drawn in close would otherwise fall into the holder's pickup.
            item.setPickUpDelay(FLAIL_OVERLAY_TICKS);
        }
        player.setData(GooAttachments.GRAB_HOLD, new GrabHold(held.getId(), throwSpeed, until));
        EntityVisuals.sendToWatchers(held, new AilmentPayload(held.getId(), AilmentKind.GRABBED, FLAIL_OVERLAY_TICKS));
    }

    private static void suspend(Entity held, long until) {
        if (held.hasData(GooAttachments.GRABBED)) {
            held.setData(GooAttachments.GRABBED, held.getData(GooAttachments.GRABBED).heldTo(until));
            return;
        }
        boolean wasNoAi = held instanceof Mob mob && mob.isNoAi();
        held.setData(GooAttachments.GRABBED, new Grabbed(until, wasNoAi, held.isNoGravity()));
        held.setNoGravity(true);
        if (held instanceof Mob mob) {
            StunEvents.forget(mob);
            mob.setNoAi(true);
        }
    }

    /**
     * Throws what a player holds along the player's look at the hold's
     * throw speed, freeing it first so it flies; a player holding nothing
     * throws nothing.
     *
     * @param player the throwing player
     * @return true where the player held an entity and threw it
     */
    public static boolean throwHeld(ServerPlayer player) {
        Entity held = heldBy(player);
        double speed = player.getData(GooAttachments.GRAB_HOLD).throwSpeed();
        player.setData(GooAttachments.GRAB_HOLD, GrabHold.NONE);
        if (held == null) {
            return false;
        }
        release(held);
        held.setDeltaMovement(player.getLookAngle().scale(speed));
        held.hurtMarked = true;
        EntityVisuals.sendToWatchers(held, new AilmentPayload(held.getId(), AilmentKind.GRABBED, 0));
        return true;
    }

    /**
     * Frees a held entity, its AI and gravity back as they stood before the hold.
     *
     * @param held the held entity
     */
    static void release(Entity held) {
        Grabbed grabbed = held.getData(GooAttachments.GRABBED);
        held.removeData(GooAttachments.GRABBED);
        held.setNoGravity(grabbed.hadNoGravity());
        if (held instanceof Mob mob) {
            mob.setNoAi(grabbed.wasNoAi());
        }
    }

    /**
     * Frees each held entity the channel has let go.
     *
     * @param event the entity tick event
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || !entity.hasData(GooAttachments.GRABBED)) {
            return;
        }
        if (entity.getData(GooAttachments.GRABBED).releasedAt(entity.level().getGameTime())) {
            release(entity);
        }
    }
}
