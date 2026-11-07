package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
                carry(target, entity, landing(PortalTransform.between(near, far), middle,
                        entity.getBbHeight() * HALF, entity.getDeltaMovement(), entity.getYRot(), entity.getXRot()));
            }
            seen.put(entity.getUUID(), middle);
        }
    }

    /**
     * Whether a point crossed a gate's face, from before it to just behind
     * it, within its oval.
     *
     * @param gate   the gate
     * @param before the point before
     * @param now    the point now
     * @return true where it went through
     */
    public static boolean crossed(Gate gate, Vec3 before, Vec3 now) {
        double out = gate.out(now);
        return gate.out(before) >= 0 && out < 0 && out > -DEEPEST && gate.withinOval(now);
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
        Vec3 eye = feet.add(0, player.getEyeHeight(), 0);
        Optional<Gate> far = partnerOf(near, player.level().dimension());
        if (far.isEmpty() || !behindTheFace(near, eye) || feet.distanceTo(player.position()) > TRUSTED_STEP) {
            return;
        }
        Landing landing = landing(PortalTransform.between(near, far.get()), eye, player.getEyeHeight(), motion,
                yaw, pitch);
        player.absSnapTo(landing.feet().x(), landing.feet().y(), landing.feet().z(), landing.yaw(), landing.pitch());
        player.setDeltaMovement(landing.motion());
        player.resetFallDistance();
        player.connection.resetPosition();
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
        return out < 0 && out > -DEEPEST && gate.withinOval(point);
    }

    /**
     * @return the gates the client knows linked
     */
    public static List<Pair> clientPairs() {
        return clientPairs;
    }
}

