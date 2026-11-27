package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.EnumSet;
import java.util.Set;

/**
 * Lunar's seam on the server: every tick a body past the seam is carried across it, and once a second a player within
 * view of the seam holds the other side loaded.
 * decision moon-wraps-at-its-border-like-the-zone
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class LunarSeamEvents {

    /** A crossing keeps the body's facing and motion, moving it alone. */
    private static final Set<Relative> KEEPS_FACING_AND_MOTION = EnumSet.of(Relative.Y_ROT, Relative.X_ROT,
            Relative.DELTA_X, Relative.DELTA_Y, Relative.DELTA_Z, Relative.ROTATE_DELTA);
    /** How often, in ticks, a player holds the other side loaded again, well inside a portal ticket's lapse. */
    private static final int HOLD_INTERVAL = 20;

    private LunarSeamEvents() {
    }

    /**
     * Carries a lunar body past the seam across it; a rider crosses with what it rides.
     *
     * @param event the entity tick event
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        carryAcross(event.getEntity());
    }

    private static void carryAcross(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level) || level.dimension() != Lunar.LEVEL
                || entity.isPassenger() || entity.isRemoved()) {
            return;
        }
        LunarCrossing.across(entity.position(), entity.getDeltaMovement(), entity.getYRot(), entity.getXRot())
                .ifPresent(carry -> entity.teleportTo(level, carry.position().x, carry.position().y,
                        carry.position().z, KEEPS_FACING_AND_MOTION, 0.0F, 0.0F, false));
    }

    /**
     * Carries a lunar player past the seam across it, as their own move reaches it, and holds the other side loaded
     * for one within view of the seam, once a second.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            carryAcross(player);
            if (player.tickCount % HOLD_INTERVAL == 0) {
                LunarMirror.holdTheOtherSideLoaded(player);
            }
        }
    }
}
