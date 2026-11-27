package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.LeafStep;
import com.mercuriusxeno.goo.ability.program.LeafSteps;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.particle.VitalFogParticle;
import com.mercuriusxeno.goo.client.particle.VitalMoteParticle;
import com.mercuriusxeno.goo.network.StreamHealedPayload;
import com.mercuriusxeno.goo.registry.GooParticles;
import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * What a player sees while Heal is held: a soft undulating fog filling
 * the cone from the glove along the aim, motes of jelly goo homing from the
 * glove onto each thing being healed, and amber healing stars on it, like
 * bonemeal's green ones. The fog stands in for any wireframe area indicator
 * (decision right-click-held-previews-release-throws); the server names
 * what each tick healed, and each mob it touched shows its life overhead.
 * vitality-waves-regenerate-and-court
 * nourish-and-healing-ship-on-jelly
 * touched-mobs-show-hearts-or-a-count-over-max
 */
public final class HealVisual {

    /** Fog puffs spawned into the cone each held tick. */
    static final int FOG_PER_TICK = 6;
    /** How far the cone's rim swells and shrinks with the undulation, as a share of its radius. */
    static final double UNDULATION = 0.25;
    /** Undulation waves along the cone's length, and how fast they roll out, in radians a tick. */
    static final double WAVES_ALONG = 1.5;
    static final double ROLL_PER_TICK = 0.35;
    /** Speed a fog puff drifts out along the aim, in blocks per tick. */
    private static final double FOG_DRIFT = 0.04;
    /** The share of the room before the reach a puff's drift may cover, so rounding leaves it inside. */
    private static final double ROOM_SLACK = 0.999;
    /** Goo motes homing onto each healed thing per healed tick. */
    static final int MOTES_PER_HEAL = 1;
    /** Stars per healed tick on each healed thing. */
    static final int STARS_PER_HEAL = 1;
    /** How far before a first-person caster their own stars rise, in blocks, clear of the near clip. */
    static final double STAR_REACH_MIN = 0.7;
    static final double STAR_REACH_MAX = 1.2;
    /** Half the arc before a first-person caster their stars spread over, in radians. */
    static final double STAR_ARC_HALF = Math.toRadians(60);
    /** The heights a first-person caster's stars rise between, as shares of their height: waist to over the head. */
    static final double STAR_LOW_SHARE = 0.5;
    static final double STAR_HIGH_SHARE = 1.15;
    /** Blocks a homing mote bows out from its straight line. */
    private static final double MOTE_BOW = 0.5;
    private static final double TWO_PI = 2 * Math.PI;
    private static final double HALF = 0.5;

    /** The ability the fog is registered under in {@link ConeVisuals}. */
    static final String HEAL_ID = "goo:jelly_heal";

    private HealVisual() {
    }

    /**
     * A point inside the fog cone: out from the apex a share of the reach, leaning off the axis by a share of the
     * shape's half width there, which the undulation, a wave rolling out from the glove over time, swells and
     * shrinks; the swell never leans it past the cone's rim.
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param cone   the cone the fog fills
     * @param along  the share of the reach the point sits at, zero to one
     * @param radial the share of the half width the point leans out by, zero to one
     * @param around where around the axis it leans, zero to one for a full turn
     * @param time   the game time, in ticks
     * @return the point
     */
    static Vec3 fogPoint(SprayCone cone, double along, double radial, double around, double time) {
        double lean = Math.min(1, radial * undulation(along, time));
        Vec3 path = StreamCone.launchDirection(cone.axis(), cone.shape(), lean, around);
        Vec3 point = cone.apex().add(path.scale(along * cone.reach()));
        return StreamCone.pulledInside(cone.apex(), cone.axis(), cone.reach(), cone.shape(), point);
    }

    /**
     * The fog puffs a held tick spawns into a cone, spread evenly over its widening volume.
     *
     * @param cone   the cone the fog fills
     * @param random the random source
     * @param time   the game time, in ticks
     * @return each puff's point
     */
    static List<Vec3> fogPuffs(SprayCone cone, RandomSource random, double time) {
        List<Vec3> puffs = new ArrayList<>(FOG_PER_TICK);
        for (int puff = 0; puff < FOG_PER_TICK; puff++) {
            // a square root spreads the puffs evenly over the cone's widening volume
            puffs.add(fogPoint(cone, Math.sqrt(random.nextDouble()), Math.sqrt(random.nextDouble()),
                    random.nextDouble(), time));
        }
        return puffs;
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

    /**
     * Fills this held tick's share of the fog cone a player's stream pours through, the glove's or a nozzle's,
     * when its ability heals.
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param player    the streaming player
     * @param abilityId the streamed ability
     * @param cone      the cone the fog fills
     */
    public static void drawFog(Player player, String abilityId, SprayCone cone) {
        if (!heals(abilityId)) {
            return;
        }
        for (Vec3 at : fogPuffs(cone, player.getRandom(), player.level().getGameTime())) {
            Vec3 drift = fogDrift(cone, at);
            player.level().addParticle(GooParticles.VITAL_FOG.get(), at.x, at.y, at.z, drift.x, drift.y, drift.z);
        }
    }

    /**
     * The speed a fog puff drifts out along the aim at: FOG_DRIFT, slowed where the reach stands nearer than the
     * puff's whole drift would carry it, so it settles short of the reach. A drift along the axis leans the puff no
     * further off it, so the puff keeps inside the shape as it goes.
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param cone the cone the fog fills
     * @param at   where the puff spawns, inside the cone
     * @return its launch speed a tick
     */
    static Vec3 fogDrift(SprayCone cone, Vec3 at) {
        Vec3 axis = cone.axis().normalize();
        Vec3 out = at.subtract(cone.apex());
        double along = out.dot(axis);
        // how far along the axis the puff may go before it meets the reach
        double room = Math.max(0, Math.sqrt(Math.max(0, along * along - out.lengthSqr()
                + cone.reach() * cone.reach())) - along);
        // a drift's whole run is its speed over one less the friction, held a hair inside the room
        double speed = Math.min(FOG_DRIFT, room * (1 - VitalFogParticle.FRICTION) * ROOM_SLACK);
        return axis.scale(speed);
    }

    /**
     * Fills this held tick's share of Heal's fog for the local player through a cone, the glove's or a nozzle's.
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param cone the cone the fog fills
     */
    static void drawFogLocal(SprayCone cone) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            drawFog(player, HEAL_ID, cone);
        }
    }

    /**
     * Plays a healed tick on the client: goo homes from the caster's glove
     * onto each healed thing, and amber stars rise on it.
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
        for (int id : payload.touchedIds()) {
            TouchedMobs.CLIENT.touch(id, level.getGameTime());
        }
        Supplier<Vec3> glove = gloveOf(mc, payload);
        for (int id : payload.healedIds()) {
            if (level.getEntity(id) instanceof LivingEntity healed) {
                homeMotes(mc, glove, healed, level.getRandom());
                raiseStars(level, healed);
            }
        }
    }

    private static void raiseStars(ClientLevel level, LivingEntity healed) {
        Minecraft mc = Minecraft.getInstance();
        // vitality-waves-regenerate-and-court: a star inside the first-person camera's own body is never seen
        boolean seenFromWithin = healed == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        for (int star = 0; star < STARS_PER_HEAL; star++) {
            Vec3 at = seenFromWithin
                    ? starBeforeTheEyes(healed.position(), healed.getBbHeight(), healed.getYRot(), level.getRandom())
                    : pointIn(healed.getBoundingBox(), level.getRandom());
            level.addParticle(GooParticles.VITAL_STAR.get(), at.x, at.y, at.z, 0, 0, 0);
        }
    }

    /**
     * Where a healing star rises on a player healed in first person: in an arc
     * before them, out past the camera's near clip, from the waist to just over
     * the head, so it shows in their view as it would on anything else healed.
     *
     * @param feet   the player's feet
     * @param height the player's height
     * @param yaw    the way the player faces, in degrees
     * @param random the random source
     * @return the star's point
     */
    static Vec3 starBeforeTheEyes(Vec3 feet, double height, float yaw, RandomSource random) {
        return starAt(feet, height, yaw, random.nextDouble(), random.nextDouble(), random.nextDouble());
    }

    /**
     * Where a healing star rises before a first-person player for three rolls
     * from zero to one: the arc roll sweeps the star across the arc before
     * them, the reach roll from the near clip outward, the height roll from
     * the waist to just over the head.
     *
     * @param feet       the player's feet
     * @param height     the player's height
     * @param yaw        the way the player faces, in degrees
     * @param arcRoll    where across the arc, zero to one
     * @param reachRoll  how far out, zero to one
     * @param heightRoll how high up, zero to one
     * @return the star's point
     */
    static Vec3 starAt(Vec3 feet, double height, float yaw, double arcRoll, double reachRoll, double heightRoll) {
        double angle = Math.toRadians(yaw) + (arcRoll * (STAR_ARC_HALF + STAR_ARC_HALF) - STAR_ARC_HALF);
        double out = STAR_REACH_MIN + reachRoll * (STAR_REACH_MAX - STAR_REACH_MIN);
        double up = height * (STAR_LOW_SHARE + heightRoll * (STAR_HIGH_SHARE - STAR_LOW_SHARE));
        return feet.add(-Math.sin(angle) * out, up, Math.cos(angle) * out);
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
     * Where the caster's cast leaves: the lifted palm blob where it stands,
     * else the local player's glove as drawn this frame, or the glove hand
     * another caster's stream left from.
     * every-ability-casts-from-the-lifted-palm-blob
     *
     * @param mc      the client
     * @param payload the healed tick
     * @return where the cast leaves, read each time it is asked
     */
    private static Supplier<Vec3> gloveOf(Minecraft mc, StreamHealedPayload payload) {
        Entity caster = mc.player;
        if (caster != null && caster.getId() == payload.casterId()) {
            return () -> CastOrigin.ofLocal(mc);
        }
        return () -> CastOrigin.of(payload.casterId(), payload.glove(), 0f);
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
        return ability != null && ability.behaviors().stream().flatMap(HealVisual::withDescendants)
                .anyMatch(step -> step instanceof LeafStep<?> leaf && leaf.leaf() == LeafSteps.HEAL);
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(HealVisual::withDescendants));
    }
}
