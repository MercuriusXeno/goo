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
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * The reach a charging Nova will pulse to, shown and heard while right click
 * holds it: frost's fog ring lies faint and pulsing about the player's
 * middle out to the radius the hold's charge resolves, growing as the hold
 * goes on, while a rush of white noise rises in pitch with the charge and
 * holds once the charge is full (decision nova-ring-grows-with-the-hold).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class NovaChargeGhost {

    /** The fog's opacity at the low and the high of its pulse: faint, an indicator rather than the nova. */
    static final float PULSE_FLOOR = 0.15f;
    static final float PULSE_CEILING = 0.4f;
    /** Radians the pulse turns each tick: a beat a little under a second. */
    static final double PULSE_PER_TICK = 0.4;
    /** The charging rush's pitch with no charge and at a full one. */
    static final float PITCH_LOW = 0.6f;
    static final float PITCH_HIGH = 1.6f;
    private static final float RUSH_VOLUME = 0.5f;
    /** The progress at which frost's fog ring stands fully spread. */
    private static final float WHOLE_SPREAD = (float) FrostExplosionVisual.SPREAD_TICKS
            / FrostExplosionVisual.DURATION_TICKS;
    /** One seed for the ghost, so its fog holds one look while it grows. */
    private static final float GHOST_SEED = 1.3f;
    private static final double HALF_HEIGHT = 0.5;
    private static final double HALF_BLOCK = 0.5;
    private static final double HALF = 0.5;

    /** The rush playing while a nova charges, empty while none does. */
    private static final AtomicReference<ChargingRush> RUSH = new AtomicReference<>();

    private NovaChargeGhost() {
    }

    /**
     * What a charging nova has reached.
     *
     * @param share the share of a full charge, 0 to 1
     * @param reach the nova's radius at that charge
     */
    record Charging(float share, double reach) {
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
     * The fog's opacity at a moment: swelling and ebbing between its floor
     * and its faint ceiling.
     *
     * @param gameTime the game time including the partial tick
     * @return the opacity, 0 to 1
     */
    static float pulseAlpha(double gameTime) {
        double swell = (Math.sin(gameTime * PULSE_PER_TICK) + 1) * HALF;
        return (float) (PULSE_FLOOR + (PULSE_CEILING - PULSE_FLOOR) * swell);
    }

    /**
     * The charging rush's pitch: rising with the charge, holding at its top once full.
     *
     * @param share the share of a full charge
     * @return the pitch
     */
    static float chargePitch(float share) {
        return PITCH_LOW + (PITCH_HIGH - PITCH_LOW) * Math.clamp(share, 0f, 1f);
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
        Optional<Charging> charging = player == null || mc.level == null ? Optional.empty() : charging(player);
        if (charging.isEmpty()) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float gameTime = mc.level.getGameTime() + partialTick;
        Vec3 middle = player.getPosition(partialTick).add(0, player.getBbHeight() * HALF_HEIGHT, 0);
        BurnoutFrame frame = new BurnoutFrame(event.getPoseStack(), mc.renderBuffers().bufferSource(),
                mc.gameRenderer.getMainCamera().position(), gameTime);
        FrostExplosionVisual.drawDisc(frame, middle.subtract(HALF_BLOCK, HALF_BLOCK, HALF_BLOCK), Direction.UP, 0f,
                (float) charging.get().reach(), WHOLE_SPREAD, pulseAlpha(gameTime), GHOST_SEED);
    }

    /**
     * Keeps the charging rush in step with the hold: started as a nova begins
     * to charge, its pitch following the charge, stopped when the hold ends.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        Optional<Charging> charging = player == null ? Optional.empty() : charging(player);
        ChargingRush playing = RUSH.get();
        if (charging.isEmpty()) {
            if (playing != null) {
                playing.end();
                RUSH.set(null);
            }
            return;
        }
        if (playing == null || playing.isStopped()) {
            playing = new ChargingRush(player);
            RUSH.set(playing);
            Minecraft.getInstance().getSoundManager().play(playing);
        }
        playing.charge(charging.get().share());
    }

    /**
     * What the local player's charging nova has reached, while the glove
     * previews a charged ability that holds one.
     *
     * @param player the local player
     * @return the charge and reach, or empty while no nova charges
     */
    private static Optional<Charging> charging(LocalPlayer player) {
        int held = GloveUseTracker.heldTicks();
        GloveSelection selection = GloveThrowSender.heldSelection(player);
        if (held <= 0 || selection == null) {
            return Optional.empty();
        }
        Delivery delivery = GloveThrowSender.selectedDelivery(selection.abilityId());
        AbilitySyncHandler.ClientAbility ability = AbilitySyncHandler.findAbility(selection.abilityId());
        if (!delivery.charges() || ability == null) {
            return Optional.empty();
        }
        float share = delivery.chargeShare(held);
        OptionalDouble reach = reachAt(ability.behaviors(), share);
        return reach.isPresent() ? Optional.of(new Charging(share, reach.getAsDouble())) : Optional.empty();
    }

    /** The rush of white noise a charging nova makes, following the player. */
    private static final class ChargingRush extends AbstractTickableSoundInstance {

        private final LocalPlayer player;

        ChargingRush(LocalPlayer player) {
            super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = RUSH_VOLUME;
            this.pitch = PITCH_LOW;
            follow();
        }

        void charge(float share) {
            this.pitch = chargePitch(share);
        }

        void end() {
            stop();
        }

        @Override
        public void tick() {
            if (player.isRemoved()) {
                stop();
            } else {
                follow();
            }
        }

        private void follow() {
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
        }
    }
}
