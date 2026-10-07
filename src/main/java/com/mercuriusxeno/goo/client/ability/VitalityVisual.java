package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.program.LeafStep;
import com.mercuriusxeno.goo.ability.program.LeafSteps;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.particle.VitalMoteParticle;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.network.StreamHealedPayload;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * What a player sees while Vitality is held: a soft undulating fog filling
 * the cone from the glove along the aim, motes of vital goo homing from the
 * glove onto each thing being healed, and pink healing stars on it, like
 * bonemeal's green ones. The fog stands in for any wireframe area indicator
 * (decision right-click-held-previews-release-throws); the server names
 * what each tick healed.
 * vitality-waves-regenerate-and-court
 */
public final class VitalityVisual {

    /** Fog puffs spawned into the cone each held tick. */
    static final int FOG_PER_TICK = 6;
    /** How far the cone's rim swells and shrinks with the undulation, as a share of its radius. */
    static final double UNDULATION = 0.25;
    /** Undulation waves along the cone's length, and how fast they roll out, in radians a tick. */
    static final double WAVES_ALONG = 1.5;
    static final double ROLL_PER_TICK = 0.35;
    /** Speed a fog puff drifts out along the aim, in blocks per tick. */
    private static final double FOG_DRIFT = 0.04;
    /** Goo motes homing onto each healed thing per healed tick. */
    static final int MOTES_PER_HEAL = 1;
    /** Stars per healed tick on each healed thing. */
    static final int STARS_PER_HEAL = 1;
    /** Blocks a homing mote bows out from its straight line. */
    private static final double MOTE_BOW = 0.5;
    private static final double TWO_PI = 2 * Math.PI;
    private static final double HALF = 0.5;
    /** An axis this close to straight up or down takes the world's x as its side. */
    private static final double NEAR_VERTICAL = 0.99;

    private VitalityVisual() {
    }

    /**
     * A point inside the fog cone. The cone opens from the apex along the
     * axis; its rim radius at each distance swells and shrinks by the
     * undulation, a wave rolling out from the glove over time.
     *
     * @param apex        the glove the cone opens from
     * @param axis        the aim, a unit vector
     * @param range       the cone's length in blocks
     * @param coneDegrees the cone's full angle, rim to rim
     * @param along       the share of the length the point sits at, zero to one
     * @param radial      the share of the rim radius the point sits at, zero to one
     * @param theta       the angle around the axis, in radians
     * @param time        the game time, in ticks
     * @return the point
     */
    static Vec3 fogPoint(Vec3 apex, Vec3 axis, double range, double coneDegrees, double along, double radial,
                         double theta, double time) {
        double distance = along * range;
        double rim = distance * Math.tan(Math.toRadians(coneDegrees * HALF));
        double radius = rim * radial * undulation(along, time);
        Vec3 side = sideOf(axis);
        Vec3 up = axis.cross(side).normalize();
        return apex.add(axis.scale(distance)).add(side.scale(radius * Math.cos(theta)))
                .add(up.scale(radius * Math.sin(theta)));
    }

    /**
     * The undulation's scale on the rim at a share of the cone's length: one
     * plus a wave rolling outward from the glove.
     *
     * @param along the share of the length, zero to one
     * @param time  the game time, in ticks
     * @return the rim's scale, within one less and one more the undulation
     */
    static double undulation(double along, double time) {
        return 1 + UNDULATION * Math.sin(TWO_PI * WAVES_ALONG * along - ROLL_PER_TICK * time);
    }

    private static Vec3 sideOf(Vec3 axis) {
        Vec3 reference = Math.abs(axis.y) > NEAR_VERTICAL ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return axis.cross(reference).normalize();
    }

    /**
     * Fills this held tick's share of the fog cone for the local player's
     * stream when its ability heals.
     *
     * @param player    the streaming player
     * @param abilityId the streamed ability
     * @param area      the ability's area, whose size and angle the fog fills
     * @param apex      the glove hand the stream leaves from
     */
    public static void drawFog(Player player, String abilityId, AbilityArea area, Vec3 apex) {
        if (!heals(abilityId) || area.shape() != AbilityArea.Shape.CONE) {
            return;
        }
        RandomSource random = player.getRandom();
        Vec3 axis = player.getLookAngle();
        double time = player.level().getGameTime();
        for (int puff = 0; puff < FOG_PER_TICK; puff++) {
            // a square root spreads the puffs evenly over the cone's widening volume
            Vec3 at = fogPoint(apex, axis, area.size(), area.angle(), Math.sqrt(random.nextDouble()),
                    Math.sqrt(random.nextDouble()), random.nextDouble() * TWO_PI, time);
            Vec3 drift = axis.scale(FOG_DRIFT);
            player.level().addParticle(GooParticles.VITAL_FOG.get(), at.x, at.y, at.z, drift.x, drift.y, drift.z);
        }
    }

    /**
     * Plays a healed tick on the client: goo homes from the caster's glove
     * onto each healed thing, and pink stars rise on it.
     *
     * @param payload the healed tick
     * @param context the network context
     */
    public static void handleHealed(StreamHealedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> playHealed(payload));
    }

    private static void playHealed(StreamHealedPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        Supplier<Vec3> glove = gloveOf(mc, payload);
        RandomSource random = level.getRandom();
        for (int id : payload.healedIds()) {
            if (level.getEntity(id) instanceof LivingEntity healed) {
                homeMotes(mc, glove, healed, random);
                for (int star = 0; star < STARS_PER_HEAL; star++) {
                    Vec3 at = pointIn(healed.getBoundingBox(), random);
                    level.addParticle(GooParticles.VITAL_STAR.get(), at.x, at.y, at.z, 0, 0, 0);
                }
            }
        }
    }

    private static void homeMotes(Minecraft mc, Supplier<Vec3> glove, LivingEntity healed, RandomSource random) {
        Vec3 from = glove.get();
        for (int mote = 0; mote < MOTES_PER_HEAL; mote++) {
            Vec3 landing = healed.getBoundingBox().getCenter();
            Vec3 bowed = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()), random.nextGaussian())
                    .normalize().scale(MOTE_BOW);
            if (mc.particleEngine.createParticle(GooParticles.VITAL_MOTE.get(), from.x, from.y, from.z, 0, 0, 0)
                    instanceof VitalMoteParticle homing) {
                homing.homeTo(from.add(landing).scale(HALF).add(bowed), () -> healed.getBoundingBox().getCenter());
            }
        }
    }

    /**
     * Where the caster's glove stands: the local player's glove as drawn this
     * frame, or the glove hand another caster's stream left from.
     *
     * @param mc      the client
     * @param payload the healed tick
     * @return where the glove stands, read each time it is asked
     */
    private static Supplier<Vec3> gloveOf(Minecraft mc, StreamHealedPayload payload) {
        Entity caster = mc.player;
        if (caster != null && caster.getId() == payload.casterId()) {
            return () -> GloveAim.handPosition(mc.gameRenderer.getMainCamera());
        }
        return payload::glove;
    }

    private static Vec3 pointIn(AABB box, RandomSource random) {
        return new Vec3(box.minX + random.nextDouble() * box.getXsize(), box.minY + random.nextDouble()
                * box.getYsize(), box.minZ + random.nextDouble() * box.getZsize());
    }

    /**
     * Whether the streamed ability heals, the abilities that draw this visual.
     *
     * @param abilityId the streamed ability
     * @return true where a heal step runs in its program
     */
    static boolean heals(String abilityId) {
        AbilitySyncHandler.ClientAbility ability = AbilitySyncHandler.findAbility(abilityId);
        return ability != null && ability.behaviors().stream().flatMap(VitalityVisual::withDescendants)
                .anyMatch(step -> step instanceof LeafStep<?> leaf && leaf.leaf() == LeafSteps.HEAL);
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(VitalityVisual::withDescendants));
    }
}
