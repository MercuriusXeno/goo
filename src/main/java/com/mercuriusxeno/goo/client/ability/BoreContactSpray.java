package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.BoreDrillShape;
import com.mercuriusxeno.goo.ability.program.BorePoint;
import com.mercuriusxeno.goo.ability.program.BoreStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.network.KnifeRainSling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.List;
import java.util.Optional;

/**
 * Where Bore's drawn drill makes contact with a breakable block, particles
 * of the block spew across its cross section on the crosshair's plane, the
 * ellipse the break picks with, each tick the bore runs, each flying back
 * toward the holder and out from its place on the rim, so the drill reads as
 * tearing into the block while the block clips it.
 * decision bore-shows-a-big-drill-from-the-glove
 * decision bore-drill-dimensions-drive-the-visuals-and-the-break
 * decision bore-drill-point-floats-along-the-crosshair-to-the-aimed-block
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class BoreContactSpray {

    /** Particles spewed a tick where the vortex meets a breakable block. */
    static final int SPITS_PER_TICK = 8;
    /** How fast a particle flies back toward the holder, in blocks a tick. */
    static final double BACK_SPEED = 0.28;
    /** How fast a particle fans out round the drill's axis, at most, in blocks a tick. */
    static final double RING_SPEED = 0.18;
    /** How much each particle's speed back varies either way, as a share. */
    private static final double SPEED_VARY = 0.3;
    /** How far back toward the holder a particle starts, so it never starts inside the block. */
    private static final double BACK_OFF = 0.1;
    private static final double HALF = 0.5;
    private static final double BOTH_WAYS = 2;

    private BoreContactSpray() {
    }

    /**
     * A spewed particle's velocity: back toward the holder from the cross
     * section, fanned out from its place on the rim, so the spew flies back in
     * the ellipse's shape rather than a line.
     *
     * @param towardHolder the unit direction from the cross section back toward the holder
     * @param outward      the particle's place on the rim, as an offset from the cross section's center
     * @param flare        how far it fans out, 0 to 1
     * @return the velocity, in blocks a tick
     */
    static Vec3 spewVelocity(Vec3 towardHolder, Vec3 outward, double flare) {
        return towardHolder.scale(BACK_SPEED).add(outward.normalize().scale(RING_SPEED * (HALF + HALF * flare)));
    }

    /**
     * Where a spewed particle starts: on the cross section's rim, a little
     * back toward the holder so it never starts inside the block.
     *
     * @param center       the cross section's center, on the crosshair's plane
     * @param towardHolder the unit direction from the cross section back toward the holder
     * @param rimOffset    the particle's place on the rim, as an offset from the center
     * @return the start, in world space
     */
    static Vec3 spewOrigin(Vec3 center, Vec3 towardHolder, Vec3 rimOffset) {
        return center.add(rimOffset).add(towardHolder.scale(BACK_OFF));
    }

    /**
     * Where a tick's particles spew from round the drill's cross section on
     * the crosshair's plane: points round the ellipse the break picks with,
     * one a particle.
     * decision bore-drill-dimensions-drive-the-visuals-and-the-break
     *
     * @param shape      the drill cone
     * @param look       the unit look
     * @param gloveRight whether the glove is in the right hand
     * @return the rim offsets, from the cross section's center
     */
    static List<Vec3> spreadOf(BoreDrillShape shape, Vec3 look, boolean gloveRight) {
        return shape.rimOffsets(look, gloveRight, SPITS_PER_TICK, 1);
    }

    /**
     * Spits the tick's particles while the local player's bore runs and its
     * line of sight meets a breakable block within reach.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.isPaused()) {
            return;
        }
        ClientAbility bore = BoreDrill.runningBore(player);
        BoreStep step = bore == null ? null : BoreDrill.stepOf(bore).orElse(null);
        if (step != null) {
            // bore-reaches-as-far-as-your-block-reach
            spitAtContact(level, player, step, bore.delivery().reachingTo(player.blockInteractionRange()).range());
        }
    }

    /**
     * Spews particles across the drill's cross section where it lands on a
     * block the bore breaks within reach.
     *
     * @param level  the client level
     * @param player the local player
     * @param step   the running bore's step
     * @param reach  the bore's reach
     */
    private static void spitAtContact(ClientLevel level, LocalPlayer player, BoreStep step, double reach) {
        Minecraft mc = Minecraft.getInstance();
        CameraEye.Sight sight = CameraEye.of(mc, player, 1f);
        boolean gloveRight = KnifeRainSling.gloveOnTheRight(player);
        Optional<BorePoint> landing = BoreDrill.landingAhead(level, sight, step, gloveRight, reach);
        if (landing.isEmpty()) {
            return;
        }
        BlockState state = level.getBlockState(landing.get().block());
        Vec3 towardHolder = sight.look().reverse();
        RandomSource random = level.getRandom();
        for (Vec3 rimOffset : spreadOf(step.shape(), sight.look(), gloveRight)) {
            Vec3 at = spewOrigin(landing.get().at(), towardHolder, rimOffset);
            double speed = 1 + SPEED_VARY * signed(random);
            Vec3 velocity = spewVelocity(towardHolder, rimOffset, random.nextDouble()).scale(speed);
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z,
                    velocity.x, velocity.y, velocity.z);
        }
    }

    private static double signed(RandomSource random) {
        return (random.nextDouble() - HALF) * BOTH_WAYS;
    }
}
