package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.BroilStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mojang.blaze3d.buffers.Std140Builder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

/**
 * Broil's heat wave: while right click holds a broil, the view ripples like
 * air over a fire around the crosshair the cone runs along, easing in as the
 * hold starts and out after it ends, through {@code goo:heat_wave}.
 * decision broil-heat-wave-ring-cooks-ahead
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class HeatWaveEffect {

    /** The chain at {@code assets/goo/post_effect/heat_wave.json}. */
    static final Identifier POST_EFFECT_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "heat_wave");
    private static final String HEAT_CONFIG_BLOCK = "HeatConfig";
    /** Std140 size of {@code HeatConfig}: one vec4. */
    private static final int HEAT_CONFIG_SIZE = 16;
    /** The ripple's reach in UV at full heat. */
    static final float MAX_STRENGTH = 0.004f;
    /** How far from the crosshair the ripple fades out, in screen heights. */
    private static final float REACH_UV = 0.45f;
    /** The share of the gap to full or no heat the wave closes each tick. */
    static final float EASE_PER_TICK = 0.25f;
    private static final float TICKS_PER_SECOND = 20f;
    /** Below this heat the wave is gone and the slot is released. */
    static final float COLD = 0.01f;

    private static final OwnedPostEffect SLOT = new OwnedPostEffect(POST_EFFECT_ID, HEAT_CONFIG_BLOCK);
    private static final Vector4f HEAT_PARAMS = new Vector4f();
    private static float heat;

    private HeatWaveEffect() {
    }

    /**
     * Eases the heat toward full while the broil runs and toward none
     * otherwise, then draws or releases the wave.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean running = player != null && broils(runningAbility(player), GloveUseTracker.runsHeld(player));
        heat = easedHeat(heat, running, mc.getDeltaTracker().getRealtimeDeltaTicks());
        if (heat < COLD || mc.level == null) {
            SLOT.hide(mc.gameRenderer);
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float seconds = ((mc.level.getGameTime() % Integer.MAX_VALUE) + partialTick) / TICKS_PER_SECOND;
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        HEAT_PARAMS.set(seconds, MAX_STRENGTH * heat, REACH_UV, aspect);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, HEAT_CONFIG_SIZE);
            builder.putVec4(HEAT_PARAMS);
            SLOT.show(mc, builder.get());
        }
    }

    /**
     * The heat after a frame: it closes a share of the gap to full heat
     * while the broil runs, and to none otherwise.
     *
     * @param heat       the heat before the frame, 0 to 1
     * @param running    whether the broil runs
     * @param deltaTicks the frame's length in ticks
     * @return the heat after the frame
     */
    static float easedHeat(float heat, boolean running, float deltaTicks) {
        float target = running ? 1f : 0f;
        return heat + (target - heat) * Math.min(1f, deltaTicks * EASE_PER_TICK);
    }

    private static @Nullable ClientAbility runningAbility(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        return abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
    }

    /**
     * Whether a broil runs: the selected ability broils and right click holds it.
     *
     * @param ability the selected ability's synced copy, or null when none
     * @param useHeld whether right click holds a live press
     * @return true while the broil runs
     */
    static boolean broils(@Nullable ClientAbility ability, boolean useHeld) {
        return useHeld && ability != null && ability.behaviors().stream().anyMatch(BroilStep.class::isInstance);
    }
}
