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
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
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
 * frost's fog ring lies whole about the player's middle, out to the radius
 * the hold's charge resolves, growing as the hold goes on
 * (decision nova-ring-grows-with-the-hold).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class NovaChargeGhost {

    /** The progress at which frost's fog ring stands fully spread and whole. */
    private static final float WHOLE_FOG = (float) FrostExplosionVisual.SPREAD_TICKS
            / FrostExplosionVisual.DURATION_TICKS;
    private static final double HALF_BLOCK = 0.5;
    private static final double HALF_HEIGHT = 0.5;

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
        BurnoutFrame frame = new BurnoutFrame(event.getPoseStack(), mc.renderBuffers().bufferSource(),
                mc.gameRenderer.getMainCamera().position(), mc.level.getGameTime() + partialTick);
        FrostExplosionVisual.drawRing(frame, middle.subtract(HALF_BLOCK, HALF_BLOCK, HALF_BLOCK), Direction.UP, 0f,
                (float) reach.getAsDouble(), WHOLE_FOG);
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
