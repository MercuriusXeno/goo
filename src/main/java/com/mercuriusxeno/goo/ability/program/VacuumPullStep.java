package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.kinetic.GrabEvents;
import com.mercuriusxeno.goo.throwing.StreamCone;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Void Vacuum's pull, run each held tick of the stream on what stands inside
 * its cone: each item entity speeds toward the player by acceleration with
 * its gravity off, rather than flying off at once, and lands in the inventory
 * as a pickup, the goo a vacuumed residual drops among them; each mob is dragged level toward the player at a steady pace
 * its knockback resistance slows, takes no damage, and stops about a block in
 * front. Bosses and players are not dragged.
 * vacuum-pulls-items-to-pickup-and-drags-mobs
 *
 * @param itemAcceleration the speed the pull adds toward the eye to an item's motion each held tick, in blocks a
 *                         tick per tick
 * @param itemTopSpeed     the fastest the pull flies an item, in blocks a tick
 * @param mobSpeed         the pace a mob resisting no knockback is dragged at, in blocks a tick
 * @param stopDistance     how near the player, level, a dragged mob stops, in blocks
 */
public record VacuumPullStep(double itemAcceleration, double itemTopSpeed, double mobSpeed, double stopDistance)
        implements TakesNoEntity {

    private static final String NAME = "vacuum_pull";
    private static final String FIELD_ITEM_ACCELERATION = "item_acceleration";
    private static final String FIELD_ITEM_TOP_SPEED = "item_top_speed";
    private static final String FIELD_MOB_SPEED = "mob_speed";
    private static final String FIELD_STOP_DISTANCE = "stop_distance";
    /** The share of an item's motion across the line to the eye the pull keeps each held tick. */
    private static final double ACROSS_MOTION_KEPT = 0.5;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<VacuumPullStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.doubleRange(0, Double.MAX_VALUE).fieldOf(FIELD_ITEM_ACCELERATION)
                    .forGetter(VacuumPullStep::itemAcceleration),
            Codec.doubleRange(0, Double.MAX_VALUE).fieldOf(FIELD_ITEM_TOP_SPEED).forGetter(VacuumPullStep::itemTopSpeed),
            Codec.doubleRange(0, Double.MAX_VALUE).fieldOf(FIELD_MOB_SPEED).forGetter(VacuumPullStep::mobSpeed),
            Codec.doubleRange(0, Double.MAX_VALUE).fieldOf(FIELD_STOP_DISTANCE)
                    .forGetter(VacuumPullStep::stopDistance)
    ).apply(inst, VacuumPullStep::new));

    /**
     * The registered type.
     */
    public static final StepType<VacuumPullStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<VacuumPullStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        if (!(host.breaker() instanceof ServerPlayer player)) {
            return true;
        }
        host.channelAim().ifPresent(aim -> {
            Vec3 eye = host.eye();
            Vec3 look = aim.aimPoint().subtract(eye);
            for (Entity entity : inCone(host, player, eye, look, aim.coneDegrees())) {
                if (entity instanceof ItemEntity item) {
                    pullItem(item, eye);
                } else if (drags(entity)) {
                    drag((Mob) entity, player);
                }
            }
        });
        return true;
    }

    private static List<Entity> inCone(ChannelHost host, ServerPlayer player, Vec3 eye, Vec3 look,
                                       double coneDegrees) {
        double range = look.length();
        return host.level().getEntities(player, new AABB(eye, eye).inflate(range),
                entity -> StreamCone.contains(eye, look, range, coneDegrees, entity.getBoundingBox().getCenter()));
    }

    private void drag(Mob mob, ServerPlayer player) {
        double resistance = mob.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        Vec3 step = dragStep(mob.position(), player.position(), pace(mobSpeed, resistance), stopDistance);
        // set even when zero, so a mob at the stop halts there rather than drifting on the last tick's drag
        mob.setDeltaMovement(step.x, mob.getDeltaMovement().y, step.z);
        mob.hurtMarked = true;
    }

    /**
     * Pulls an item one held tick toward the eye by acceleration, its gravity
     * off while the pull holds it, so the pull is seen to draw it in; the
     * entity tick turns its gravity back on a few ticks after the last held
     * tick that reached it. Its pickup delay clears, so the player's own tick
     * picks it up on arrival.
     * vacuum-pulls-items-to-pickup-and-drags-mobs
     */
    private void pullItem(ItemEntity item, Vec3 eye) {
        GrabEvents.holdAloft(item, item.level().getGameTime() + GrabEvents.HOLD_GRACE_TICKS);
        item.setDeltaMovement(pulledVelocity(item.getDeltaMovement(), item.position(), eye, itemAcceleration,
                itemTopSpeed));
        item.setNoPickUpDelay();
        item.hurtMarked = true;
    }

    /**
     * An item's motion after one held tick of the pull: the motion it had
     * along the line to the eye, half its motion across that line, plus the
     * acceleration toward the eye, no faster than the top speed and no longer
     * than the distance left, so it homes on the player rather than flying past.
     * vacuum-pulls-items-to-pickup-and-drags-mobs
     *
     * @param velocity     the item's motion before the tick, in blocks a tick
     * @param from         the item's position
     * @param toward       the player's eye
     * @param acceleration the speed added toward the eye, in blocks a tick
     * @param topSpeed     the fastest the item flies, in blocks a tick
     * @return the item's motion after the tick
     */
    static Vec3 pulledVelocity(Vec3 velocity, Vec3 from, Vec3 toward, double acceleration, double topSpeed) {
        Vec3 toEye = toward.subtract(from);
        Vec3 along = toEye.normalize();
        Vec3 alongMotion = along.scale(velocity.dot(along));
        // a drop's pop, kept whole with its gravity off, carries it out of the cone and over the player's head
        Vec3 acrossMotion = velocity.subtract(alongMotion).scale(ACROSS_MOTION_KEPT);
        Vec3 pulled = alongMotion.add(acrossMotion).add(along.scale(acceleration));
        double cap = Math.min(topSpeed, toEye.length());
        return pulled.length() > cap ? pulled.normalize().scale(cap) : pulled;
    }

    /**
     * The pace a mob is dragged at: the full pace slowed by the share of
     * knockback it resists.
     * vacuum-pulls-items-to-pickup-and-drags-mobs
     *
     * @param speed               the pace a mob resisting no knockback is dragged at
     * @param knockbackResistance the mob's knockback resistance, 0 to 1
     * @return the pace, in blocks a tick
     */
    static double pace(double speed, double knockbackResistance) {
        return speed * (1 - Math.clamp(knockbackResistance, 0, 1));
    }

    /**
     * One tick's level drag toward the player, cut short at the stop so the
     * mob halts about a block in front rather than on the player.
     * vacuum-pulls-items-to-pickup-and-drags-mobs
     *
     * @param from         the mob's position
     * @param toward       the player's position
     * @param pace         the drag's pace, in blocks a tick
     * @param stopDistance how near the player, level, the drag stops
     * @return the tick's motion, level, zero once within the stop
     */
    static Vec3 dragStep(Vec3 from, Vec3 toward, double pace, double stopDistance) {
        Vec3 level = new Vec3(toward.x - from.x, 0, toward.z - from.z);
        double left = level.length() - stopDistance;
        if (left <= 0) {
            return Vec3.ZERO;
        }
        return level.normalize().scale(Math.min(pace, left));
    }

    /**
     * Whether the pull drags an entity: a living mob that is no boss, and so
     * never a player.
     * vacuum-pulls-items-to-pickup-and-drags-mobs
     *
     * @param entity the entity in the cone
     * @return true for a mob the pull drags
     */
    static boolean drags(Entity entity) {
        return entity instanceof Mob mob && mob.isAlive() && !EntityScan.isBoss(mob);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL);
    }
}
