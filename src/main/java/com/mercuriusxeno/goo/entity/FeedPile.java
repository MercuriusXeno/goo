package com.mercuriusxeno.goo.entity;

import com.mercuriusxeno.goo.ability.feed.Feeding;
import com.mercuriusxeno.goo.registry.GooEntities;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The feed Jelly's Feed lays where it lands on the ground: a goo-owned
 * pseudo-item that falls to the ground and never enters an inventory.
 * Animals and mobs within its radius path to it; the first animal to reach
 * it eats it as its food, a lone hostile reaching it eats it, and hostiles
 * that reach it together turn on each other for the fight time before
 * trying again (decision feed-blob-feeds-and-draws-mobs).
 */
public class FeedPile extends Entity {

    /** Blocks from the feed a mob reaches it within. */
    static final double REACH = 1.5;
    /** Hostiles that must reach the feed together before they fight over it. */
    private static final int FIGHTERS_TO_FIGHT = 2;
    /** Ticks between the feed drawing the mobs about it toward it. */
    static final int LURE_INTERVAL = 10;
    /** The speed a lured mob walks toward the feed at, its navigation's own multiple. */
    private static final double LURE_SPEED = 1.0;
    /** Ticks the feed lies before it spoils, a few minutes. */
    static final int LIFETIME = 6000;
    private static final String TAG_RADIUS = "Radius";
    private static final String TAG_FIGHT_TICKS = "FightTicks";
    private static final String TAG_FIGHT_UNTIL = "FightUntil";
    private static final double GRAVITY = 0.04;
    private static final double AIR_DRAG = 0.98;
    private static final double GROUND_FRICTION = 0.6;

    private double radius;
    private int fightTicks;
    private long fightUntil;
    private final List<Mob> fighters = new ArrayList<>();

    /**
     * The constructor the entity type builds a loaded or synced feed with.
     *
     * @param type  the entity type
     * @param level the level
     */
    public FeedPile(EntityType<? extends FeedPile> type, Level level) {
        super(type, level);
    }

    /**
     * Lays a feed at a point, still, so it falls straight down to the ground below.
     *
     * @param level      the server level
     * @param at         where the feed appears
     * @param radius     the blocks out to which the feed draws mobs
     * @param fightTicks the ticks hostiles reaching it together fight for
     * @return the feed laid
     */
    public static FeedPile lay(ServerLevel level, Vec3 at, double radius, int fightTicks) {
        FeedPile feed = new FeedPile(GooEntities.FEED_PILE.get(), level);
        feed.setPos(at);
        feed.radius = radius;
        feed.fightTicks = fightTicks;
        level.addFreshEntity(feed);
        return feed;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        // the client draws the feed and reads nothing the server holds
    }

    @Override
    protected double getDefaultGravity() {
        return GRAVITY;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    public void tick() {
        super.tick();
        applyGravity();
        move(MoverType.SELF, getDeltaMovement());
        double friction = onGround() ? GROUND_FRICTION : AIR_DRAG;
        setDeltaMovement(getDeltaMovement().multiply(friction, AIR_DRAG, friction));
        if (level() instanceof ServerLevel server) {
            tickServer(server);
        }
    }

    private void tickServer(ServerLevel server) {
        if (tickCount >= LIFETIME) {
            discard();
            return;
        }
        long now = server.getGameTime();
        if (tickCount % LURE_INTERVAL == 0) {
            lure();
        }
        if (now >= fightUntil) {
            endFight();
            obtain(server, now);
        }
    }

    private void lure() {
        for (Mob mob : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(radius), Mob::isAlive)) {
            if (mob instanceof PathfinderMob && mob.getTarget() == null && !mob.isNoAi()) {
                mob.getNavigation().moveTo(this, LURE_SPEED);
            }
        }
    }

    private void obtain(ServerLevel server, long now) {
        List<Mob> reaching = level().getEntitiesOfClass(Mob.class, new AABB(position(), position()).inflate(REACH),
                Mob::isAlive);
        List<Mob> hostiles = reaching.stream().filter(Enemy.class::isInstance).toList();
        if (hostiles.size() >= FIGHTERS_TO_FIGHT) {
            startFight(hostiles, now);
            return;
        }
        reaching.stream().filter(mob -> mob instanceof Animal || mob instanceof Enemy).findFirst()
                .ifPresent(eater -> eat(server, eater));
    }

    private void eat(ServerLevel server, Mob eater) {
        if (eater instanceof Animal animal) {
            Feeding.feed(animal, null);
        }
        Feeding.crumbsAndHearts(server, eater);
        discard();
    }

    private void startFight(List<Mob> hostiles, long now) {
        for (int index = 0; index < hostiles.size(); index++) {
            Mob fighter = hostiles.get(index);
            fighter.setTarget(hostiles.get((index + 1) % hostiles.size()));
            fighters.add(fighter);
        }
        fightUntil = now + fightTicks;
    }

    private void endFight() {
        for (Mob fighter : fighters) {
            if (fighter.getTarget() != null && fighters.contains(fighter.getTarget())) {
                fighter.setTarget(null);
            }
        }
        fighters.clear();
    }

    /**
     * @return the blocks out to which the feed draws mobs
     */
    public double radius() {
        return radius;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        radius = input.getDoubleOr(TAG_RADIUS, 0);
        fightTicks = input.getIntOr(TAG_FIGHT_TICKS, 0);
        fightUntil = input.getLongOr(TAG_FIGHT_UNTIL, 0L);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putDouble(TAG_RADIUS, radius);
        output.putInt(TAG_FIGHT_TICKS, fightTicks);
        output.putLong(TAG_FIGHT_UNTIL, fightUntil);
    }
}
