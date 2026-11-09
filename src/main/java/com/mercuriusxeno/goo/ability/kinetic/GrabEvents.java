package com.mercuriusxeno.goo.ability.kinetic;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.ability.program.TerrakinesisHolds;
import com.mercuriusxeno.goo.ability.pulse.StunEvents;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.entity.BlockHunk;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs Grab on the entities a player's channel holds: a held entity hangs
 * where the hold puts it, its AI and gravity suspended and its limbs
 * flailing, until the channel lets go a few ticks later or a left click
 * throws it along the look. An item entity is pulled to the player instead
 * of held.
 * decision grab-holds-and-throws-a-physics-body
 * decision grab-pulls-an-item-to-you-and-picks-it-up
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class GrabEvents {

    /**
     * Ticks a held entity stays held past the last channel tick that reached
     * it, so the hold stands across a channel tick that arrives late.
     */
    public static final int HOLD_GRACE_TICKS = 3;
    /** Ticks each channel tick's flail overlay lasts, past the grace so it never gaps. */
    public static final int FLAIL_OVERLAY_TICKS = HOLD_GRACE_TICKS + 2;

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
        moveHeld(player, held, at.subtract(held.position()), throwSpeed);
    }

    /**
     * Draws a held entity one step toward a linked gate this channel tick,
     * so the client is seen to slide it into the gate rather than jumping it
     * there in one tick it never shows arriving. The step leaves the body no
     * motion, so its own tick moves it no further and it crosses the face
     * where the pair's own check sees it cross.
     * aim-and-shots-carry-through-linked-gates
     *
     * @param player     the holding player
     * @param held       the held entity
     * @param step       this tick's step toward the gate, no longer than Grab's pull speed
     * @param throwSpeed the speed a throw launches it at, in blocks per tick
     */
    public static void drawToward(ServerPlayer player, Entity held, Vec3 step, double throwSpeed) {
        moveHeld(player, held, step, throwSpeed);
    }

    /**
     * Binds a player's grab of a body to the gate the grab reached it
     * through, the gate on the body's side, until the pair carries the body
     * out; a grab reaching it straight binds none.
     * aim-and-shots-carry-through-linked-gates
     *
     * @param player the grabbing player
     * @param body   the grabbed body
     * @param gate   the gate on the body's side the grab reached through, empty for a straight grab
     */
    public static void bindThrough(ServerPlayer player, Entity body, Optional<Gate> gate) {
        gate.ifPresentOrElse(through -> THROUGH.put(player.getUUID(), new Through(body.getId(), through)),
                () -> THROUGH.remove(player.getUUID()));
    }

    /**
     * The gate a player's grab of a body still draws it through.
     *
     * @param player the grabbing player
     * @param body   the held body
     * @return the gate on the body's side, empty once carried out or for a straight grab
     */
    public static Optional<Gate> boundThrough(ServerPlayer player, Entity body) {
        Through through = THROUGH.get(player.getUUID());
        return through != null && through.bodyId() == body.getId() ? Optional.of(through.gate()) : Optional.empty();
    }

    /**
     * Frees every grab bound through a gate of a body the pair carried out.
     *
     * @param body the carried body
     */
    public static void carriedOut(Entity body) {
        THROUGH.values().removeIf(through -> through.bodyId() == body.getId());
    }

    /** A grab bound through a gate: the body, and the gate on its side. */
    private record Through(int bodyId, Gate gate) {
    }

    private static final Map<UUID, Through> THROUGH = new HashMap<>();

    /**
     * Whether a Grab holds an entity now.
     *
     * @param entity the entity
     * @return true while a channel holds it
     */
    public static boolean isHeld(Entity entity) {
        return entity.hasData(GooAttachments.GRABBED)
                && !entity.getData(GooAttachments.GRABBED).releasedAt(entity.level().getGameTime());
    }

    private static void moveHeld(ServerPlayer player, Entity held, Vec3 step, double throwSpeed) {
        long until = player.level().getGameTime() + HOLD_GRACE_TICKS;
        suspend(held, until);
        // Moved rather than placed, so the held body collides with the blocks it is pulled against.
        held.move(MoverType.SELF, step);
        held.setDeltaMovement(Vec3.ZERO);
        held.fallDistance = 0;
        held.hurtMarked = true;
        player.setData(GooAttachments.GRAB_HOLD, new GrabHold(held.getId(), throwSpeed, until, false));
        EntityVisuals.sendToWatchers(held, new AilmentPayload(held.getId(), AilmentKind.GRABBED, FLAIL_OVERLAY_TICKS));
    }

    /**
     * Pulls an item entity toward a player's eye this channel tick, by its
     * motion rather than its position, so the client interpolates the flight,
     * and clears its pickup delay so the player's own tick picks it up on
     * arrival. The item stands under no hold, so a left click throws nothing.
     * decision grab-pulls-an-item-to-you-and-picks-it-up
     *
     * @param player    the pulling player
     * @param item      the item entity
     * @param pullSpeed the speed the item flies at, in blocks per tick
     * @param toward    where the item flies for: the eye, or the eye as seen through a linked pair the way runs
     */
    public static void pull(ServerPlayer player, ItemEntity item, double pullSpeed, Vec3 toward) {
        Vec3 toEye = toward.subtract(item.position());
        // Capped at the distance left, so the last tick lands on the player rather than past it.
        item.setDeltaMovement(toEye.normalize().scale(Math.min(pullSpeed, toEye.length())));
        item.setNoPickUpDelay();
        item.hurtMarked = true;
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
     * Whether a throw spent the player's press, stretching the spent press
     * across this channel tick so it stands until the channel lets go.
     *
     * @param player the channeling player
     * @return true where the press is spent and lifts nothing
     */
    public static boolean extendSpentPress(ServerPlayer player) {
        long now = player.level().getGameTime();
        if (!player.getData(GooAttachments.GRAB_HOLD).spentAt(now)) {
            return false;
        }
        player.setData(GooAttachments.GRAB_HOLD, GrabHold.spentUntil(now + HOLD_GRACE_TICKS));
        return true;
    }

    /**
     * Throws what a player holds along the player's look at the hold's
     * throw speed, freeing it first so it flies, and spends the press so
     * the channel ticks right click still sends lift nothing until it lets
     * go; a player holding nothing throws nothing and keeps a spent press spent.
     *
     * @param player the throwing player
     * @return true where the player held an entity and threw it
     */
    public static boolean throwHeld(ServerPlayer player) {
        Entity held = heldBy(player);
        double speed = player.getData(GooAttachments.GRAB_HOLD).throwSpeed();
        if (held == null) {
            // decision diagnose-and-correct-grabs-left-click-throw: a second click must not clear the spent press
            return false;
        }
        // decision diagnose-and-correct-grabs-left-click-throw: a cleared hold let the next channel tick lift the thrown body again
        player.setData(GooAttachments.GRAB_HOLD,
                GrabHold.spentUntil(player.level().getGameTime() + HOLD_GRACE_TICKS));
        release(held);
        if (held instanceof BlockHunk hunk) {
            // A thrown Terrakinesis chunk breaks where it strikes, and holds off the next grab while it flies.
            hunk.markThrown();
            TerrakinesisHolds.threw(player.getUUID(), player.level().getGameTime());
        }
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
