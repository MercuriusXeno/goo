package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.program.BlinkLanding;
import com.mercuriusxeno.goo.ability.program.TeleportStep;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.ability.AfterimageRenderer;
import com.mercuriusxeno.goo.client.ability.Afterimages;
import com.mercuriusxeno.goo.client.ability.PortalView;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.BlinkAim;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.type.GooColors;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/**
 * The blink cursor: the player's silhouette ripple standing where blink
 * would land them. Each frame it resolves the landing from the local
 * player's look and the ability's range through
 * the resolver the server's teleport calls
 * (decision blink-lands-safely-costed-by-distance), shows nothing where
 * the blink would land nowhere, captures the player's pose, and draws a ripple there that
 * restarts every pulse period, so the silhouettes keep leaving while the
 * cursor shows. The ability's JSON names when it shows: while right click
 * is held, or whenever the ability is selected.
 * Decision ripple-outline-is-the-blink-cursor.
 */
public final class BlinkCursor {

    /** Game ticks between one ripple of the cursor starting and the next. */
    static final int PULSE_PERIOD_TICKS = Afterimages.PULSES * Afterimages.PULSE_GAP_TICKS;

    /** Game ticks each cursor silhouette grows and fades over, the blink's own ripple life. */
    static final int LIFE_TICKS = 12;

    private BlinkCursor() {
    }

    /**
     * Hands the shared path the cursor's ripples while the selected ability
     * shows it, sorted by where the blink would land, in the main frame
     * alone.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     * decision ripple-outline-is-the-blink-cursor
     *
     * @param frame the frame being drawn
     * @param draws where the ripples' draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        // It drew only at AfterLevel, which a portal view's pass never posts; the path runs AfterLevel routes there.
        if (PortalView.drawing()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        collectRipples(frame, mc, player, draws);
    }

    private static void collectRipples(LevelFrame frame, Minecraft mc, LocalPlayer player,
                                       Consumer<SortedDraw> draws) {
        ClientAbility ability = selectedAbility(player);
        ResourceKey<GooTypeDefinition> type = GloveAim.selectedGooType(player);
        if (type == null || ability == null || !showsCursor(ability, GloveUseTracker.showsArea())) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Optional<BlinkLanding> trip = BlinkAim.trip(player, ability, partialTick);
        List<Afterimages.Afterimage<EntityRenderState>> ripples = ripplesAt(mc, player, trip, type, partialTick);
        if (!ripples.isEmpty()) {
            draws.accept(SortedDraw.nearestOf(ripples.stream().map(Afterimages.Afterimage::position),
                    mc.gameRenderer.getMainCamera().position(), () -> AfterimageRenderer.drawRipples(frame, ripples)));
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
     * The cursor's ripples this frame: the player's pose standing where blink
     * would land them, one ripple per start still standing.
     *
     * @param mc          the client, its level loaded
     * @param player      the local player
     * @param trip        the trip the blink would make this frame, empty where it lands nowhere
     * @param type        the selected goo type, whose color the ripple wears
     * @param partialTick the frame's partial tick
     * @return the ripples to draw
     */
    private static List<Afterimages.Afterimage<EntityRenderState>> ripplesAt(Minecraft mc, LocalPlayer player,
            Optional<BlinkLanding> trip, ResourceKey<GooTypeDefinition> type, float partialTick) {
        if (trip.isEmpty()) {
            return List.of();
        }
        Vec3 destination = trip.get().feet();
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
     * look, and its indicator's rule holds for the press.
     *
     * @param ability the selected ability's synced copy, or null when none
     * @param useHeld whether right click holds a live press
     * @return true while the cursor shows
     */
    static boolean showsCursor(@Nullable ClientAbility ability, boolean useHeld) {
        if (ability == null) {
            return false;
        }
        OptionalDouble range = TeleportStep.lookRange(ability.behaviors());
        return range.isPresent() && ability.indicator().shows(useHeld);
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
