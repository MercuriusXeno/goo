package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.program.HostVariables;
import com.mercuriusxeno.goo.ability.program.NovaStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Stream;

/**
 * The reach a charging Nova will pulse to, shown while right click holds it:
 * a plain white disc, faint and pulsing so it reads as an indicator rather
 * than frost, lies flat about the player's middle out to the radius the
 * hold's charge resolves, growing as the hold goes on
 * (decision nova-ring-grows-with-the-hold).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class NovaChargeGhost {

    /** The disc's opacity at the low and the high of its pulse. */
    static final float PULSE_FLOOR = 0.12f;
    static final float PULSE_CEILING = 0.3f;
    /** Radians the pulse turns each tick: a beat a little under a second. */
    static final double PULSE_PER_TICK = 0.4;
    private static final int DISC_SEGMENTS = 48;
    private static final int WHITE = 0xFFFFFF;
    private static final double TWO_PI = 2 * Math.PI;
    private static final double HALF_HEIGHT = 0.5;
    private static final int MAX_CHANNEL = 255;
    private static final double HALF = 0.5;

    private NovaChargeGhost() {
    }

    /**
     * The reach a program's nova pulses to at a share of a full charge.
     *
     * @param behaviors the program
     * @param charge    the share of a full charge, 0 to 1
     * @return the nova's radius, or empty where the program holds no nova
     */
    static OptionalDouble reachAt(List<Step> behaviors, float charge) {
        Variables charged = name -> HostVariables.CHARGE.equals(name) ? OptionalDouble.of(charge)
                : OptionalDouble.of(0);
        return behaviors.stream().flatMap(NovaChargeGhost::withDescendants)
                .filter(NovaStep.class::isInstance).map(NovaStep.class::cast).findFirst()
                .map(nova -> OptionalDouble.of(nova.radius().evaluate(charged))).orElse(OptionalDouble.empty());
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(NovaChargeGhost::withDescendants));
    }

    /**
     * Draws the ghost while the local player holds a charging ability with a nova.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        OptionalDouble reach = player == null || mc.level == null ? OptionalDouble.empty() : chargingReach(player);
        if (reach.isEmpty()) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 middle = player.getPosition(partialTick).add(0, player.getBbHeight() * HALF_HEIGHT, 0);
        drawDisc(event.getPoseStack(), mc.renderBuffers().bufferSource(), mc.gameRenderer.getMainCamera().position(),
                middle, (float) reach.getAsDouble(), pulseAlpha(mc.level.getGameTime() + partialTick));
    }

    /**
     * The disc's opacity at a moment: swelling and ebbing between its floor
     * and its ceiling, never solid.
     *
     * @param gameTime the game time including the partial tick
     * @return the opacity, 0 to 1
     */
    static float pulseAlpha(double gameTime) {
        double swell = (Math.sin(gameTime * PULSE_PER_TICK) + 1) * HALF;
        return (float) (PULSE_FLOOR + (PULSE_CEILING - PULSE_FLOOR) * swell);
    }

    private static void drawDisc(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 camera, Vec3 middle,
                                 float radius, float alpha) {
        RenderType type = RenderTypes.debugQuads();
        VertexConsumer c = buffers.getBuffer(type);
        PoseStack.Pose pose = poseStack.last();
        int color = ARGB.color(Math.round(alpha * MAX_CHANNEL), WHITE);
        float cx = (float) (middle.x - camera.x);
        float cy = (float) (middle.y - camera.y);
        float cz = (float) (middle.z - camera.z);
        for (int i = 0; i < DISC_SEGMENTS; i++) {
            double a0 = TWO_PI * i / DISC_SEGMENTS;
            double a1 = TWO_PI * (i + 1) / DISC_SEGMENTS;
            c.addVertex(pose, cx, cy, cz).setColor(color);
            c.addVertex(pose, cx, cy, cz).setColor(color);
            c.addVertex(pose, cx + radius * (float) Math.cos(a1), cy, cz + radius * (float) Math.sin(a1)).setColor(color);
            c.addVertex(pose, cx + radius * (float) Math.cos(a0), cy, cz + radius * (float) Math.sin(a0)).setColor(color);
        }
        buffers.endBatch(type);
    }

    /**
     * The reach the local player's charging nova has reached, while the
     * glove previews a charged ability that holds one.
     *
     * @param player the local player
     * @return the reach, or empty while no nova charges
     */
    private static OptionalDouble chargingReach(LocalPlayer player) {
        int held = GloveUseTracker.heldTicks();
        GloveSelection selection = GloveThrowSender.heldSelection(player);
        if (held <= 0 || selection == null) {
            return OptionalDouble.empty();
        }
        Delivery delivery = GloveThrowSender.selectedDelivery(selection.abilityId());
        AbilitySyncHandler.ClientAbility ability = AbilitySyncHandler.findAbility(selection.abilityId());
        return delivery.charges() && ability != null
                ? reachAt(ability.behaviors(), delivery.chargeShare(held)) : OptionalDouble.empty();
    }
}
