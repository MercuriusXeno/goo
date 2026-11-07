package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Going through a linked gate, a portal's or a hole's mouth: the blocks a
 * gate lies on stand open to a body in front of its oval, so the body walks
 * into the opening, and once it crosses the gate's face it carries on out of
 * the partner, at the matching point, with its momentum and its view turned
 * through the pair, as the portal gun does. Read on both sides:
 * the server's gates from the saved links and open holes, the client's from
 * what the server told it.
 * portals-link-by-looking-through-portal-vision
 * hole-closes-the-space-to-an-opening-ahead
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class GatePassage {

    /** How far before a gate's face a body may stand and still find its blocks open. */
    private static final double APPROACH = 1.0;
    /** How deep behind a gate's face a body's middle may be and still cross it this tick. */
    private static final double DEEPEST = 1.5;
    private static final double HALF = 0.5;
    private static final double UNIT = 1.0;
    /**
     * How far before a gate's face a body's eye or middle counts as through
     * it: past the camera's near plane, a twentieth of a block, and the face's
     * own lift, so the camera never comes near enough the face to clip into
     * the wall behind it, and lands that far before the partner's face.
     */
    private static final double CAMERA_MARGIN = 0.08;
    /**
     * How far before the partner's face a carried eye or middle lands: clear
     * of the crossing margin, so a step straight back counts as crossing the
     * partner rather than walking into the opened blocks behind its face.
     */
    private static final double LANDING_MARGIN = 0.12;
    /** A crawling body's height. */
    private static final double CRAWL_HEIGHT = 0.6;
    /** How far before a low wall gate's face a player drops into a crawl. */
    private static final double CRAWL_REACH = 1.5;
    /** The players this passage put in a crawl, on each side. */
    private static final Set<UUID> SERVER_CRAWLED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Set<UUID> CLIENT_CRAWLED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** How far from where the server last held a player its client may say it crossed a gate, in blocks. */
    private static final double TRUSTED_STEP = 4.0;
    private static final float DEGREES = (float) (180.0 / Math.PI);

    private static volatile List<Pair> serverPairs = List.of();
    private static volatile List<Pair> clientPairs = List.of();
    /** Each body near a gate's middle at the last tick, which a crossing is read against. */
    private static Map<UUID, Vec3> lastMiddles = Map.of();

    private GatePassage() {
    }

    /**
     * Two gates linked, each opening onto the other.
     *
     * @param one   a gate
     * @param other its partner
     */
    public record Pair(Gate one, Gate other) {
    }

    /**
     * Sets the gates the client knows linked.
     *
     * @param pairs the pairs
     */
    public static void setClientPairs(List<Pair> pairs) {
        clientPairs = List.copyOf(pairs);
    }

    /**
     * Whether a block stands open to a body: it is one a linked gate lies on,
     * and the body stands at the gate, before its face and within its oval.
     *
     * @param entity the body
     * @param pos    the block
     * @return true where the body passes into the block
     */
    public static boolean passable(Entity entity, BlockPos pos) {
        List<Pair> pairs = entity.level().isClientSide() ? clientPairs : serverPairs;
        if (pairs.isEmpty()) {
            return false;
        }
        Vec3 middle = entity.getBoundingBox().getCenter();
        for (Pair pair : pairs) {
            for (Gate gate : List.of(pair.one(), pair.other())) {
                if (opensTo(gate, entity, pos, middle)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether one gate opens a block to a body: the gate lies on the block
     * in the body's level, and the body's middle stands at its face, within
     * its oval.
     *
     * @param gate   the gate
     * @param entity the body
     * @param pos    the block
     * @param middle the body's middle
     * @return true where the gate opens the block to the body
     */
    private static boolean opensTo(Gate gate, Entity entity, BlockPos pos, Vec3 middle) {
        return gate.dimension().equals(entity.level().dimension()) && gate.covers(pos) && atTheFace(gate, entity,
                middle);
    }

    /**
     * Whether a body is passing through a gate: its middle at a linked
     * gate's face in its level, within the oval, where the gate's blocks stand
     * open to it, so it is neither in a wall nor suffocating there.
     *
     * @param entity the body
     * @return true while it passes
     */
    public static boolean passing(Entity entity) {
        List<Pair> pairs = entity.level().isClientSide() ? clientPairs : serverPairs;
        Vec3 middle = entity.getBoundingBox().getCenter();
        return pairs.stream().flatMap(pair -> Stream.of(pair.one(), pair.other()))
                .anyMatch(gate -> gate.dimension().equals(entity.level().dimension())
                        && atTheFace(gate, entity, middle));
    }

    /**
     * Whether a player must crawl to pass a gate before it: one on a wall
     * shorter than the player stands, its mouth before the player's feet.
     *
     * @param player the player
     * @return true where it crawls
     */
    public static boolean crawlsThrough(Player player) {
        List<Pair> pairs = player.level().isClientSide() ? clientPairs : serverPairs;
        double standing = player.getDimensions(Pose.STANDING).height();
        Vec3 crawling = player.position().add(0, CRAWL_HEIGHT * HALF, 0);
        return pairs.stream().flatMap(pair -> Stream.of(pair.one(), pair.other()))
                .anyMatch(gate -> gate.face().getAxis().isHorizontal()
                        && gate.dimension().equals(player.level().dimension())
                        && gate.blocks().getYsize() < standing
                        && gate.blocks().expandTowards(gate.face().getUnitVec3().scale(CRAWL_REACH))
                                .contains(crawling));
    }

    /**
     * Holds each player in a crawl while it stands before or in a wall gate
     * too low to stand through, and lets it stand again once clear; a pose
     * something else forced stands untouched.
     *
     * @param event the player's tick
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        Set<UUID> crawled = player.level().isClientSide() ? CLIENT_CRAWLED : SERVER_CRAWLED;
        if (crawlsThrough(player)) {
            if (player.getForcedPose() == null || crawled.contains(player.getUUID())) {
                player.setForcedPose(Pose.SWIMMING);
                crawled.add(player.getUUID());
            }
        } else if (crawled.remove(player.getUUID()) && player.getForcedPose() == Pose.SWIMMING) {
            player.setForcedPose(null);
        }
    }

    private static boolean atTheFace(Gate gate, Entity entity, Vec3 middle) {
        double out = gate.out(middle);
        return out > -DEEPEST && out < APPROACH + entity.getBbWidth() && gate.withinOval(middle);
    }

    /**
     * Gathers the server's linked gates and carries every body but a player
     * whose middle crossed one's face this tick out of its partner. A
     * player's own client carries it, on the frame its eye crosses, and
     * tells the server through {@link #announced}.
     *
     * @param event the post-tick event
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        serverPairs = gather(server);
        Map<UUID, Vec3> seen = new HashMap<>();
        for (Pair pair : serverPairs) {
            crossThrough(server, pair.one(), pair.other(), seen);
            crossThrough(server, pair.other(), pair.one(), seen);
        }
        lastMiddles = seen;
    }

    private static List<Pair> gather(MinecraftServer server) {
        List<Pair> pairs = new ArrayList<>();
        QuantumPortals.get(server.overworld()).links().values()
                .forEach(link -> pairs.add(new Pair(link.entry().gate(), link.exit().gate())));
        OpenHoles.open().forEach(hole -> pairs.add(new Pair(hole.entry(), hole.exit())));
        return pairs;
    }

    private static void crossThrough(MinecraftServer server, Gate near, Gate far, Map<UUID, Vec3> seen) {
        ServerLevel level = server.getLevel(near.dimension());
        ServerLevel target = server.getLevel(far.dimension());
        if (level == null || target == null) {
            return;
        }
        for (Entity entity : level.getEntities((Entity) null, near.blocks().inflate(APPROACH + 1),
                body -> body.isAlive() && !(body instanceof Player))) {
            Vec3 middle = entity.getBoundingBox().getCenter();
            Vec3 before = lastMiddles.get(entity.getUUID());
            if (before != null && crossed(near, before, middle)) {
                carry(target, entity, landingThrough(near, far, new Held(middle, entity.getBbHeight() * HALF),
                        entity.getBbHeight(), entity.getDeltaMovement(), entity.getYRot(), entity.getXRot()));
            }
            seen.put(entity.getUUID(), middle);
        }
    }

    /**
     * Whether a point crossed a gate's face: moving in, it now stands past
     * the margin before the face, no deeper than a body can be, within the
     * oval, wherever it stood the tick before, so a body stepping straight
     * back into the gate it just came out of crosses it again rather than
     * walking into the opened blocks behind it.
     *
     * @param gate   the gate
     * @param before the point before
     * @param now    the point now
     * @return true where it went through
     */
    public static boolean crossed(Gate gate, Vec3 before, Vec3 now) {
        double out = gate.out(now);
        return out < gate.out(before) && out < CAMERA_MARGIN && out > -DEEPEST && gate.withinOval(now);
    }

    /**
     * The gate a point crossed among linked pairs in one level, and the
     * partner it comes out of.
     *
     * @param pairs  the linked pairs
     * @param level  the level the point moves in
     * @param before the point before
     * @param now    the point now
     * @return the crossed gate and its partner, empty where it crossed none
     */
    public static Optional<Pair> crossing(List<Pair> pairs, ResourceKey<Level> level, Vec3 before, Vec3 now) {
        for (Pair pair : pairs) {
            for (Pair way : List.of(pair, new Pair(pair.other(), pair.one()))) {
                if (way.one().dimension().equals(level) && crossed(way.one(), before, now)) {
                    return Optional.of(way);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * How a body comes out of a pair: where its feet stand, how it moves and
     * where it looks.
     *
     * @param feet   its feet
     * @param motion its momentum
     * @param yaw    its yaw, in degrees
     * @param pitch  its pitch, in degrees
     */
    public record Landing(Vec3 feet, Vec3 motion, float yaw, float pitch) {
    }

    /**
     * How a body crossing a pair comes out: the point it is held by carried
     * to the matching point past the partner, its feet the same drop below,
     * its momentum and its look turned through the pair. A player is held by
     * its eye, so the camera carries on unbroken; the client carrying itself
     * and the server checking it read the same answer.
     *
     * @param through the transform through the pair
     * @param held    the point the body is held by as it crossed
     * @param drop    how far its feet stand below that point
     * @param motion  its momentum
     * @param yaw     its yaw, in degrees
     * @param pitch   its pitch, in degrees
     * @return the landing
     */
    public static Landing landing(PortalTransform through, Vec3 held, double drop, Vec3 motion, float yaw,
            float pitch) {
        Vec3 look = through.direction(Vec3.directionFromRotation(pitch, yaw));
        float turnedYaw = (float) Math.atan2(-look.x(), look.z()) * DEGREES;
        float turnedPitch = (float) Math.asin(Math.clamp(-look.y(), -UNIT, UNIT)) * DEGREES;
        return new Landing(through.point(held).subtract(0, drop, 0), through.direction(motion), turnedYaw,
                turnedPitch);
    }

    private static void carry(ServerLevel target, Entity entity, Landing landing) {
        entity.teleport(new TeleportTransition(target, landing.feet(), landing.motion(), landing.yaw(),
                landing.pitch(), Set.of(), TeleportTransition.DO_NOTHING));
        entity.setDeltaMovement(landing.motion());
        entity.hurtMarked = true;
        entity.resetFallDistance();
    }

    /**
     * Takes a player's word that its client carried it through a gate, from
     * where its feet stood, how it moved and where it looked as its eye
     * crossed. Where the server finds the crossing true, the gate linked to
     * a partner in the same level, the eye just behind its face within its
     * oval, the feet near where the server holds the player, the server sets
     * the player where the client did, sending it nothing back, so the
     * client's own carry stands unbroken.
     *
     * @param player the player
     * @param near   the gate its client says it crossed
     * @param feet   where its feet stood as its eye crossed
     * @param motion how it moved
     * @param yaw    its yaw, in degrees
     * @param pitch  its pitch, in degrees
     */
    public static void announced(ServerPlayer player, Gate near, Vec3 feet, Vec3 motion, float yaw, float pitch) {
        Optional<Gate> far = partnerOf(near, player.level().dimension());
        Optional<Held> held = heldBehind(near, feet, player.getEyeHeight(), player.getBbHeight());
        if (far.isEmpty() || held.isEmpty() || feet.distanceTo(player.position()) > TRUSTED_STEP) {
            return;
        }
        Landing landing = landingThrough(near, far.get(), held.get(), player.getBbHeight(), motion, yaw, pitch);
        player.absSnapTo(landing.feet().x(), landing.feet().y(), landing.feet().z(), landing.yaw(), landing.pitch());
        player.setDeltaMovement(landing.motion());
        player.resetFallDistance();
        player.connection.resetPosition();
    }

    /**
     * The point a body crossing a gate is carried by: its eye where the eye
     * stands just behind the face, so the camera carries on unbroken, else its
     * middle, as when a body drops into a floor gate and sinks no deeper than
     * the gate's one open block, its eye still above the face.
     *
     * @param point the point
     * @param drop  how far the feet stand below it
     */
    public record Held(Vec3 point, double drop) {
    }

    /**
     * The point a body standing with its feet at a spot crossed a gate by, if any.
     *
     * @param gate      the gate
     * @param feet      the body's feet
     * @param eyeHeight its eye's height over its feet
     * @param height    its height
     * @return the point it is carried by, empty where neither eye nor middle stands just behind the face
     */
    public static Optional<Held> heldBehind(Gate gate, Vec3 feet, double eyeHeight, double height) {
        Vec3 eye = feet.add(0, eyeHeight, 0);
        if (behindTheFace(gate, eye)) {
            return Optional.of(new Held(eye, eyeHeight));
        }
        Vec3 middle = feet.add(0, height * HALF, 0);
        return behindTheFace(gate, middle) ? Optional.of(new Held(middle, height * HALF)) : Optional.empty();
    }

    /**
     * How a body crossing one gate of a pair comes out of the other: carried
     * by the point it crossed by, and set clear of the partner's face where
     * that face is a floor or a ceiling, so an upright body never comes out
     * with its feet below a floor or its head above a ceiling.
     *
     * @param near   the gate crossed
     * @param far    its partner
     * @param held   the point the body crossed by
     * @param height the body's height
     * @param motion its momentum
     * @param yaw    its yaw, in degrees
     * @param pitch  its pitch, in degrees
     * @return the landing
     */
    public static Landing landingThrough(Gate near, Gate far, Held held, double height, Vec3 motion, float yaw,
            float pitch) {
        Landing landing = landing(PortalTransform.between(near, far), held.point(), held.drop(), motion, yaw, pitch);
        // The held point lands mirrored, as far behind the partner's face as it stood before this one; it is set
        // the landing margin before the partner's face instead, so the camera never stands where it would clip.
        double short_ = LANDING_MARGIN - far.out(landing.feet().add(0, held.drop(), 0));
        Vec3 ahead = short_ > 0 ? landing.feet().add(far.face().getUnitVec3().scale(short_)) : landing.feet();
        return new Landing(clearOf(far, ahead, height), landing.motion(), landing.yaw(), landing.pitch());
    }

    /**
     * Feet set clear of a floor or ceiling gate's face: on a floor no lower
     * than the face, under a ceiling low enough that a crawling body's head
     * stands below it, so a body dropping through a 1x1 hole into a cell one
     * block tall comes out crawling in it rather than pressed into its floor.
     *
     * @param gate   the gate come out of
     * @param feet   where the feet would stand
     * @param height the body's height
     * @return where they stand
     */
    static Vec3 clearOf(Gate gate, Vec3 feet, double height) {
        double plane = gate.center().y();
        return switch (gate.face()) {
            case UP -> new Vec3(feet.x(), Math.max(feet.y(), plane), feet.z());
            case DOWN -> new Vec3(feet.x(), Math.min(feet.y(), plane - Math.min(height, CRAWL_HEIGHT)), feet.z());
            default -> feet;
        };
    }

    private static Optional<Gate> partnerOf(Gate near, ResourceKey<Level> level) {
        return serverPairs.stream()
                .flatMap(pair -> Stream.of(pair, new Pair(pair.other(), pair.one())))
                .filter(way -> way.one().equals(near) && way.other().dimension().equals(level))
                .map(Pair::other)
                .findFirst();
    }

    private static boolean behindTheFace(Gate gate, Vec3 point) {
        double out = gate.out(point);
        return out < CAMERA_MARGIN && out > -DEEPEST && gate.withinOval(point);
    }

    /**
     * @return the gates the client knows linked
     */
    public static List<Pair> clientPairs() {
        return clientPairs;
    }
}

