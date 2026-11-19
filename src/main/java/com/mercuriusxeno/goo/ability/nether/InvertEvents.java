package com.mercuriusxeno.goo.ability.nether;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.held.HeldEffectsEvents;
import com.mercuriusxeno.goo.ability.held.LaidState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Runs Invert against the world for a player its gravity modifier stands on:
 * the held effect ends the tick the player meets open sky, the eye sits for a
 * body standing on the ceiling, and a pose change keeps the body's top on the
 * ceiling as an upright body's change keeps its feet on the floor. The eye
 * and the pose run on both sides, since the client moves its own player.
 * invert-flips-your-gravity-and-lets-you-walk-on-ceilings
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class InvertEvents {

    private InvertEvents() {
    }

    /**
     * Ends Invert on a player exposed to the sky, and takes a modifier no held
     * effect lays any longer off the player.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Invert.isInverted(player)) {
            return;
        }
        int roofHeight = player.level().getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX(),
                player.getBlockZ());
        if (InvertSky.verdict(player.getBoundingBox().maxY, roofHeight) == InvertSky.Verdict.END) {
            HeldEffectsEvents.endLaying(player, LaidState.INVERT);
        }
        if (!HeldEffectsEvents.laysState(player, LaidState.INVERT)) {
            Invert.clear(player);
        }
    }

    /**
     * Moves the player's eye when its gravity turns over or back, so it sits
     * for the side the body stands on.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTickStart(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        boolean eyeOnTheCeilingSide = CeilingGround.eyeOnTheCeilingSide(player.getBbHeight(), player.getEyeHeight());
        if (eyeOnTheCeilingSide != Invert.isInverted(player)) {
            player.refreshDimensions();
        }
    }

    /**
     * Sizes an inverted player's body for the ceiling: its eye as far from the
     * bottom as an upright eye from the top, and a body changing height kept
     * with its top where it stood.
     *
     * @param event the size event
     */
    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player player) || !Invert.isInverted(player)) {
            return;
        }
        EntityDimensions upright = event.getNewSize();
        event.setNewSize(upright.withEyeHeight(CeilingGround.eyeHeight(upright.height(), upright.eyeHeight())));
        float shrunk = event.getOldSize().height() - upright.height();
        if (shrunk != 0) {
            player.setPos(player.getX(), player.getY() + shrunk, player.getZ());
        }
    }
}
