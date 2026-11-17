package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.NectarGooStep;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Nectar's nerf, the sticky goo's: a thing touching it, on a wall or on a
 * top, keeps the shares of its jump and its horizontal speed the laying
 * ability's nectar goo step names, as honey slows and holds down what is
 * on it, and jumps and moves at full strength once clear of it. Each tick
 * a touching thing's horizontal motion scales by the speed share once; its
 * next jump, in the tick after it last touched the goo, scales by the jump
 * share. Both run on either side, since the client moves its own player.
 * nectar-is-the-sticky-goo-you-climb-that-nerfs-jumping-and-speed
 */
public final class NectarStick {

    /** Full strength: the share of its jump and speed a thing clear of Nectar keeps. */
    private static final double FULL_STRENGTH = 1.0;
    /** How many ticks after its last touch a jump still pushes off the goo: the touch lands after the jump's tick. */
    private static final long JUMP_TOUCH_TICKS = 1;
    /** How many ticks after it was last stuck a thing still hangs: the stick lands after the tick's own check. */
    private static final long STUCK_TOUCH_TICKS = 1;
    /** The gravity modifier a thing stuck on a Nectar coated wall wears. */
    private static final Identifier STUCK = Identifier.fromNamespaceAndPath(Goo.MODID, "nectar_stuck");
    /** How far past its sides a thing reaches for Nectar goo to stick to: a hair. */
    private static final double TOUCH = 0.02;
    /** The share of its gravity taken from a stuck thing: all of it. */
    private static final double NO_GRAVITY = -1.0;
    /**
     * A walking thing's speed in blocks a tick for each point of its movement
     * speed: a player at 0.1 walks 0.2158 blocks a tick on ordinary ground.
     */
    private static final double WALK_PER_SPEED = 2.158;
    /** The share of its horizontal motion a thing in the air keeps each tick, which the wall's motion makes up. */
    private static final double AIR_DRAG = 0.91;

    private NectarStick() {
    }

    /**
     * The shares of its jump and its speed an entity keeps.
     *
     * @param jump  the share of its jump
     * @param speed the share of its horizontal speed
     */
    public record Multipliers(double jump, double speed) {
    }

    /**
     * @param touching whether the entity touches Nectar
     * @param nectar   Nectar's step, naming the two shares
     * @return the step's jump and speed shares for an entity touching Nectar, full strength for one clear of it
     */
    public static Multipliers multipliersFor(boolean touching, NectarGooStep nectar) {
        return touching ? new Multipliers(nectar.jumpFactor(), nectar.speedFactor())
                : new Multipliers(FULL_STRENGTH, FULL_STRENGTH);
    }

    /**
     * @param jumping    whether the stuck thing jumps
     * @param sneaking   whether it sneaks
     * @param climbSpeed the speed it climbs or sinks at
     * @return its vertical velocity
     */
    public static double climbVelocity(boolean jumping, boolean sneaking, double climbSpeed) {
        if (jumping == sneaking) {
            return 0;
        }
        return jumping ? climbSpeed : -climbSpeed;
    }

    /**
     * The speed a thing stuck on a Nectar coated wall climbs or sinks at:
     * the step's climb speed nerfed by the same share as its walking.
     * operator ruling 2026-11-17: climbing is nerfed to match the speed nerf
     *
     * @param nectar Nectar's step
     * @return the climb speed, blocks per tick
     */
    public static double wallClimbSpeed(NectarGooStep nectar) {
        return nectar.climbSpeed() * nectar.speedFactor();
    }

    /**
     * A stuck thing's horizontal motion along a Nectar coated wall: its
     * movement keys turned by its facing, at its walking speed nerfed by
     * Nectar's speed share, as it would walk on Nectar underfoot; a
     * diagonal goes no faster than straight.
     * operator ruling 2026-11-17: on the wall you move at walking speed, nerfed by the speed factor alone
     *
     * @param strafe      the thing's sideways input, left positive
     * @param forward     its forward input
     * @param yaw         its facing, in degrees
     * @param walkSpeed   its walking speed, blocks per tick
     * @param speedFactor the share of its speed Nectar leaves it
     * @return its horizontal motion along the wall
     */
    public static Vec3 wallMotion(double strafe, double forward, float yaw, double walkSpeed, double speedFactor) {
        Vec3 input = new Vec3(strafe, 0, forward);
        if (input.lengthSqr() == 0) {
            return Vec3.ZERO;
        }
        Vec3 heading = input.lengthSqr() > 1 ? input.normalize() : input;
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        Vec3 turned = new Vec3(heading.x * cos - heading.z * sin, 0, heading.z * cos + heading.x * sin);
        return turned.scale(walkSpeed * speedFactor);
    }

    /**
     * Sticks a living thing to a Nectar coated wall it stands in, once a
     * tick: its gravity off, its fall forgotten, and its vertical motion the
     * climb its jump or sneak asks for, hanging where it is otherwise; on
     * both sides, since the client moves its own player.
     * operator UAT 2026-11-17: on Nectar you are temporarily Spider-Man; gravity is off while you are stuck
     *
     * @param level  the level the cell stands in
     * @param living the thing stuck to the wall
     * @param nectar the cell's laying ability's nectar goo step
     */
    static void stick(Level level, LivingEntity living, NectarGooStep nectar) {
        long now = level.getGameTime();
        if (living.getData(GooAttachments.NECTAR_STUCK_AT) == now) {
            return;
        }
        living.setData(GooAttachments.NECTAR_STUCK_AT, now);
        AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
        if (gravity != null && !gravity.hasModifier(STUCK)) {
            gravity.addTransientModifier(new AttributeModifier(STUCK, NO_GRAVITY, Operation.ADD_MULTIPLIED_TOTAL));
        }
        living.resetFallDistance();
        // operator ruling 2026-11-17: on the wall you move at walking speed and climb, nerfed by the speed factor alone
        Vec3 along = wallMotion(living.xxa, living.zza, living.getYRot(), living.getSpeed() * WALK_PER_SPEED,
                nectar.speedFactor()).scale(1 / AIR_DRAG);
        living.setDeltaMovement(along.x, climbVelocity(living.jumping, living.isShiftKeyDown(),
                wallClimbSpeed(nectar)), along.z);
    }

    /**
     * Whether Nectar goo beside a thing counts as a wall it sticks to: goo
     * rising more than a step over its feet does, so a thin layer it walks
     * onto never sticks it.
     * operator UAT 2026-11-17: touching the side of Nectar goo taller than a step counts as Nectar on a wall
     *
     * @param gooTop     the top of a block of Nectar goo beside a thing
     * @param feet       the thing's feet
     * @param stepHeight the height the thing steps up
     * @return true where the goo counts as a wall
     */
    public static boolean towersOverStep(double gooTop, double feet, double stepHeight) {
        return gooTop > feet + stepHeight;
    }

    /**
     * Sticks a living thing whose sides touch Nectar goo rising more than a
     * step over its feet, as a coat on a wall sticks it: a column of Nectar
     * blocks is climbed as a coated wall is.
     * operator UAT 2026-11-17: touching the side of Nectar goo taller than a step counts as Nectar on a wall
     *
     * @param level  the level it stands in
     * @param living the living thing
     */
    public static void stickToTallGoo(Level level, LivingEntity living) {
        AABB sides = living.getBoundingBox().inflate(TOUCH, 0, TOUCH);
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(sides.minX), Mth.floor(sides.minY), Mth.floor(sides.minZ),
                Mth.floor(sides.maxX), Mth.floor(sides.maxY), Mth.floor(sides.maxZ))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof NectarCellBlock cell && towersBeside(level, pos, state, sides, living)) {
                cell.nectarIn(level).ifPresent(nectar -> stick(level, living, nectar));
                return;
            }
        }
    }

    private static boolean towersBeside(Level level, BlockPos pos, BlockState state, AABB sides, LivingEntity living) {
        return state.getCollisionShape(level, pos, CollisionContext.empty()).toAabbs().stream()
                .map(part -> part.move(pos))
                .anyMatch(part -> part.intersects(sides) && towersOverStep(part.maxY, living.getY(), living.maxUpStep()));
    }

    /**
     * @param living a living thing
     * @return true while it is stuck on Nectar, its gravity off
     */
    public static boolean isStuck(LivingEntity living) {
        AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
        return gravity != null && gravity.hasModifier(STUCK);
    }

    /**
     * Gives a living thing its gravity back once it is no longer stuck to a
     * Nectar coated wall: the tick after the last one it was stuck.
     *
     * @param level  the level it stands in
     * @param living the living thing
     */
    public static void unstickIfClear(Level level, LivingEntity living) {
        AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
        if (gravity != null && gravity.hasModifier(STUCK)
                && level.getGameTime() - living.getData(GooAttachments.NECTAR_STUCK_AT) > STUCK_TOUCH_TICKS) {
            gravity.removeModifier(STUCK);
        }
    }

    /**
     * Slows a thing touching Nectar by the speed share, once a tick however
     * many of the goo's hooks reach it, and marks the tick for its next jump.
     *
     * @param level  the level the cell stands in
     * @param entity the thing touching the goo
     * @param nectar the cell's laying ability's nectar goo step
     */
    static void touch(Level level, Entity entity, NectarGooStep nectar) {
        long now = level.getGameTime();
        if (entity.getData(GooAttachments.NECTAR_TOUCHED_AT) != now) {
            entity.setData(GooAttachments.NECTAR_TOUCHED_AT, now);
            double speed = multipliersFor(true, nectar).speed();
            entity.setDeltaMovement(entity.getDeltaMovement().multiply(speed, 1.0, speed));
        }
    }

    /**
     * Holds down a jump pushing off Nectar: the jump's rise scales by the
     * jump share of a thing that touched the goo this tick or the last.
     *
     * @param level  the level the jumper stands in
     * @param jumper the jumping thing
     * @param nectar the nectar goo step Nectar's ability ships
     */
    public static void holdDown(Level level, Entity jumper, NectarGooStep nectar) {
        boolean touching = level.getGameTime() - jumper.getData(GooAttachments.NECTAR_TOUCHED_AT) <= JUMP_TOUCH_TICKS;
        Vec3 motion = jumper.getDeltaMovement();
        jumper.setDeltaMovement(motion.x, motion.y * multipliersFor(touching, nectar).jump(), motion.z);
    }
}
