package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.ShiftStep;
import com.mercuriusxeno.goo.ability.program.TeleportStep;
import com.mercuriusxeno.goo.client.ability.AfterimageRenderer;
import com.mercuriusxeno.goo.client.ability.Afterimages;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.type.GooColors;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The shift cursor: the player's silhouette ripple standing where a blink
 * or a fungal shift would land them. Each frame it resolves the destination
 * through the function the server's step calls, by the ability's own rule:
 * a blink lands along the look at its range, a fungal shift on the fungus
 * block the look meets within its range and nowhere when the look meets
 * none. It captures the player's pose and draws a ripple there that
 * restarts every pulse period, so the silhouettes keep leaving while the
 * cursor shows. The ability's JSON names when it shows: while right click
 * is held, or whenever the ability is selected.
 * Decisions ripple-outline-is-the-blink-cursor and fungal-shift-blinks-to-the-aimed-fungus.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ShiftCursor {

    /** Game ticks between one ripple of the cursor starting and the next. */
    static final int PULSE_PERIOD_TICKS = Afterimages.PULSES * Afterimages.PULSE_GAP_TICKS;

    /** Game ticks each cursor silhouette grows and fades over, the blink's own ripple life. */
    static final int LIFE_TICKS = 12;

    private ShiftCursor() {
    }

    /**
     * Draws the cursor's ripple after the level while the selected ability shows it.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        ClientAbility ability = selectedAbility(player);
        ResourceKey<GooTypeDefinition> type = GloveAim.selectedGooType(player);
        if (type != null && ability != null && showsCursor(ability, GloveUseTracker.showsArea())) {
            float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            destination(player, ability, partialTick).ifPresent(destination -> AfterimageRenderer.drawRipples(event,
                    ripplesAt(mc, player, destination, type, partialTick)));
        }
    }

    /**
     * The synced copy of the ability the player's glove has selected.
     *
     * @param player the local player
     * @return the ability, or null with no glove or no synced copy
     */
    private static @Nullable ClientAbility selectedAbility(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        return abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
    }

    /**
     * Where the selected ability would land the player: along the look for a
     * blink, on the aimed fungus for a fungal shift.
     *
     * @param player      the local player
     * @param ability     the selected ability, which shows the cursor
     * @param partialTick the frame's partial tick
     * @return the destination, or empty for a shift aimed at no fungus
     */
    private static Optional<Vec3> destination(LocalPlayer player, ClientAbility ability, float partialTick) {
        OptionalDouble blinkRange = TeleportStep.lookRange(ability.behaviors());
        if (blinkRange.isPresent()) {
            return Optional.of(TeleportStep.lookDestination(player.getPosition(partialTick),
                    player.getViewVector(partialTick), blinkRange.getAsDouble()));
        }
        return ShiftStep.aimedFungus(player.level(), player,
                ShiftStep.reachOf(player, ShiftStep.fungusRange(ability.behaviors()).orElseThrow()));
    }

    /**
     * The cursor's ripples this frame: the player's pose standing at the
     * destination, one ripple per start still standing.
     *
     * @param mc          the client, its level loaded
     * @param player      the local player
     * @param destination where the ability would land the player
     * @param type        the selected goo type, whose color the ripple wears
     * @param partialTick the frame's partial tick
     * @return the ripples to draw
     */
    private static List<Afterimages.Afterimage<EntityRenderState>> ripplesAt(Minecraft mc, LocalPlayer player,
            Vec3 destination, ResourceKey<GooTypeDefinition> type, float partialTick) {
        EntityRenderState pose = mc.getEntityRenderDispatcher().extractEntity(player, partialTick);
        int rgb = GooColors.get(player.level().registryAccess(), type);
        List<Afterimages.Afterimage<EntityRenderState>> ripples = new ArrayList<>();
        for (long start : rippleStarts(player.level().getGameTime())) {
            ripples.add(new Afterimages.Afterimage<>(pose, destination, rgb, start, LIFE_TICKS));
        }
        return ripples;
    }

    /**
     * Whether the cursor shows for the selected ability: it blinks along the
     * look or shifts to a fungus, and its indicator's rule holds for the press.
     *
     * @param ability the selected ability's synced copy, or null when none
     * @param useHeld whether right click holds a live press
     * @return true while the cursor shows
     */
    static boolean showsCursor(@Nullable ClientAbility ability, boolean useHeld) {
        if (ability == null) {
            return false;
        }
        boolean landsThePlayer = TeleportStep.lookRange(ability.behaviors()).isPresent()
                || ShiftStep.fungusRange(ability.behaviors()).isPresent();
        return landsThePlayer && ability.indicator().shows(useHeld);
    }

    /**
     * The start ticks of every cursor ripple with a silhouette standing now,
     * newest first. A ripple starts on each multiple of the pulse period, so
     * the cursor restarts every period and the one before still fades out.
     *
     * @param now the game time
     * @return the start ticks
     */
    static List<Long> rippleStarts(long now) {
        long lastPulseFadedAfter = (long) (Afterimages.PULSES - 1) * Afterimages.PULSE_GAP_TICKS + LIFE_TICKS;
        List<Long> starts = new ArrayList<>();
        for (long start = now - Math.floorMod(now, PULSE_PERIOD_TICKS); now - start < lastPulseFadedAfter;
                start -= PULSE_PERIOD_TICKS) {
            starts.add(start);
        }
        return starts;
    }
}
