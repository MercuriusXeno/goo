package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.FieldEffectState;
import com.mercuriusxeno.goo.ability.program.FieldEffectStep;
import com.mercuriusxeno.goo.ability.program.FieldStrike;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.block.ability.AbilityBlockEntity;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.ber.AbilityBlockRenderState;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Urchin's trap visual: the trap rests as its goo blob once the landing
 * flare retracts, and each strike in flight bristles the landing's chrome
 * spines out as it arms and shoots a telescoping spear ({@link UrchinSpear})
 * at its mob, the spines retracting with the spear. The strikes are the
 * metal_spikes field effect's, read from the marker's
 * {@link FieldEffectState}; each spear aims at its mob's interpolated body
 * center while the client holds the mob, so it stands in the mob on the
 * tick the damage lands.
 * urchin-rests-bristled-and-spears
 */
public final class MetalSpikeVisual {

    /** How far the windup shake swings the orb either side of resting size, at its peak. */
    static final float SHAKE_AMPLITUDE = 0.15f;
    /** Ticks per cycle of the windup shake. */
    static final float SHAKE_PERIOD = 1.5f;
    private static final double TWO_PI = 2 * Math.PI;
    /** Half a mob's height, where its body center stands above its feet. */
    private static final float HALF = 0.5f;
    /** Game ticks the bristle's specular band takes over one show's sweeps, the held ghost's two seconds. */
    private static final float SWEEP_TICKS = 40f;

    private MetalSpikeVisual() {
    }

    /**
     * Populates {@code state} with the metal trap's bristle and its spears
     * in flight, read from the marker's field-effect state, their aims
     * from the mobs the client holds, and their timing from the
     * field-effect step of the marker's synced ability; a marker of another
     * type draws neither.
     *
     * @param be    the ability block block entity
     * @param state the render state to populate
     */
    public static void extract(AbilityBlockEntity be, AbilityBlockRenderState state) {
        FieldEffectState field = be.getFieldEffect();
        Optional<FieldEffectStep> step = SyncedSteps.first(be, FieldEffectStep.class);
        boolean metal = GooTypes.METAL.equals(be.getGooType()) && be.getBehavior() != null && step.isPresent();
        Variables variables = Variables.NONE;
        state.spikeAnims = metal ? field.strikes() : List.of();
        state.spikeAims = aimsOf(be.getLevel(), state.spikeAnims, Vec3.atLowerCornerOf(be.getBlockPos()),
                state.partialTick);
        state.spikeStrikeTick = step.map(trap -> trap.strikeTick().evaluateInt(variables)).orElse(0);
        state.spikeLength = step.map(trap -> trap.strikeTicks().evaluateInt(variables)).orElse(0);
        state.urchinBristle = (float) state.spikeAnims.stream().mapToDouble(strike -> UrchinSpear.bristleShare(
                strike.age(), state.partialTick, state.spikeStrikeTick, state.spikeLength)).max().orElse(0);
    }

    /**
     * Each strike's aim, block-local: its mob's body center at the partial
     * tick where the level holds the mob, the strike's synced point where
     * it does not.
     *
     * @param level       the client level, or null
     * @param strikes     the strikes in flight
     * @param corner      the marker block's lower corner
     * @param partialTick the partial tick
     * @return the aims, parallel to the strikes
     */
    private static List<Vector3f> aimsOf(Level level, List<FieldStrike> strikes, Vec3 corner, float partialTick) {
        List<Vector3f> aims = new ArrayList<>(strikes.size());
        for (FieldStrike strike : strikes) {
            Entity mob = level == null ? null : level.getEntity(strike.entityId());
            Vec3 aim = mob == null ? new Vec3(strike.x(), strike.y(), strike.z())
                    : mob.getPosition(partialTick).add(0, mob.getBbHeight() * HALF, 0);
            aims.add(aim.subtract(corner).toVector3f());
        }
        return List.copyOf(aims);
    }

    /**
     * Computes the orb's shake for one spike (decision
     * orchestration-animation-per-ability): through the windup the orb
     * oscillates rapidly about resting size, building toward the strike,
     * and comes to rest on the tick the spike lands. Outside a windup the
     * orb is still.
     *
     * @param age         the spike's age in ticks
     * @param partialTick the partial tick for smooth interpolation
     * @param strikeTick  the age at which the spike lands
     * @return scale multiplier for the orb, within SHAKE_AMPLITUDE of 1
     */
    static float gooShake(int age, float partialTick, int strikeTick) {
        float t = age + partialTick;
        if (t < 0 || t >= strikeTick) {
            return 1f;
        }
        float buildUp = t / strikeTick;
        double phase = TWO_PI * (strikeTick - t) / SHAKE_PERIOD;
        return 1f + SHAKE_AMPLITUDE * buildUp * (float) Math.sin(phase);
    }

    /**
     * Submits the bristling spines and a spear for each strike in flight,
     * all on Metal's depth-writing chrome.
     *
     * @param state         the render state
     * @param poseStack     the pose stack
     * @param nodeCollector the node collector
     */
    public static void submit(AbilityBlockRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        if (state.spikeAnims.isEmpty()) {
            return;
        }
        float sweep = Mth.frac(state.gameTime / SWEEP_TICKS);
        int sweepByte = NetherDiscMesh.toByte(sweep);
        Vector3f base = MetalExplosionVisual.basePoint(state.placedFace);
        GooSubmitter.submitChrome(poseStack, nodeCollector, (pose, c) -> {
            if (state.urchinBristle > 0f) {
                MetalExplosionVisual.bristled(state.placedFace, sweep, state.urchinBristle).emit(pose, c);
            }
            for (int i = 0; i < state.spikeAnims.size(); i++) {
                FieldStrike strike = state.spikeAnims.get(i);
                Vector3f aim = state.spikeAims.get(i);
                float reach = aim.distance(base) + UrchinSpear.OVERSHOOT;
                float tip = UrchinSpear.tipDistance(strike.age(), state.partialTick, state.spikeStrikeTick,
                        state.spikeLength, reach);
                UrchinSpear.emit(pose, c, base, aim, tip, sweepByte);
            }
        });
    }
}
