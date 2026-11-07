package com.mercuriusxeno.goo.entity;

import com.mercuriusxeno.goo.ability.program.ExplosionMode;
import com.mercuriusxeno.goo.ability.program.GooExplosion;
import com.mercuriusxeno.goo.ability.world.MeteorFall;
import com.mercuriusxeno.goo.ability.world.MeteorMarks;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.MeteorScorchPayload;
import com.mercuriusxeno.goo.registry.GooEntities;
import com.mojang.serialization.Codec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Meteo's meteor: it appears high above its target and hangs there
 * smouldering through its wind-up, then drops straight down over its fall
 * time trailing fire and smoke, and explodes there through Goo's explosion
 * with a flash, breaking blocks into a crater every watching client
 * scorches. Its whole path is synced, so clients draw it smoothly down.
 * decision meteo-winds-up-then-drops-in-a-second-trailing-fire
 * decision meteo-needs-a-clear-sky
 */
public class Meteor extends Entity {

    private static final String TAG_TARGET = "Target";
    private static final String TAG_START = "Start";
    private static final String TAG_POWER = "Power";
    private static final String TAG_WIND_UP_TICKS = "WindUpTicks";
    private static final String TAG_FALL_TICKS = "FallTicks";
    private static final String TAG_AGE = "Age";
    /** How far past the explosion's power the scorch reaches, in blocks. */
    private static final float SCORCH_PAST_POWER = 1.5f;
    /** How far off a client still draws the meteor, in blocks. */
    private static final double SEEN_FROM = 512;
    private static final EntityDataAccessor<Vector3fc> DATA_START =
            SynchedEntityData.defineId(Meteor.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3fc> DATA_TARGET =
            SynchedEntityData.defineId(Meteor.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_WIND_UP_TICKS =
            SynchedEntityData.defineId(Meteor.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FALL_TICKS =
            SynchedEntityData.defineId(Meteor.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_CALLED_AT =
            SynchedEntityData.defineId(Meteor.class, EntityDataSerializers.LONG);

    private Vec3 start = Vec3.ZERO;
    private Vec3 target = Vec3.ZERO;
    private float power;
    private int windUpTicks;
    private int fallTicks = 1;
    private int age;

    /**
     * The constructor the entity type builds a loaded or synced meteor with.
     *
     * @param type  the entity type
     * @param level the level
     */
    public Meteor(EntityType<? extends Meteor> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /**
     * Calls a meteor down on a point: it appears above the point, as high as
     * the fall height or the world's top allows, hangs there through the
     * wind-up and lands after the fall time.
     *
     * @param level       the server level
     * @param at          the point it strikes
     * @param power       the explosion's power
     * @param windUpTicks the ticks it hangs before it drops
     * @param fallTicks   the ticks it falls for
     */
    public static void call(ServerLevel level, Vec3 at, float power, int windUpTicks, int fallTicks) {
        Meteor meteor = new Meteor(GooEntities.METEOR.get(), level);
        meteor.target = at;
        meteor.start = MeteorFall.startAbove(at);
        meteor.power = power;
        meteor.windUpTicks = Math.max(0, windUpTicks);
        meteor.fallTicks = Math.max(1, fallTicks);
        meteor.setPos(meteor.start);
        meteor.syncPath(level.getGameTime());
        level.addFreshEntity(meteor);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(DATA_START, new Vector3f());
        entityData.define(DATA_TARGET, new Vector3f());
        entityData.define(DATA_WIND_UP_TICKS, 0);
        entityData.define(DATA_FALL_TICKS, 0);
        entityData.define(DATA_CALLED_AT, 0L);
    }

    /**
     * Hands every watching client the meteor's whole path, so each frame
     * draws it where the path stands at that frame rather than stepping it
     * from one position packet to the next.
     *
     * @param calledAt the game time the meteor was called
     */
    private void syncPath(long calledAt) {
        entityData.set(DATA_START, start.toVector3f());
        entityData.set(DATA_TARGET, target.toVector3f());
        entityData.set(DATA_WIND_UP_TICKS, windUpTicks);
        entityData.set(DATA_FALL_TICKS, fallTicks);
        entityData.set(DATA_CALLED_AT, calledAt);
    }

    /**
     * Where the meteor's path stands at a moment, read from the path the
     * server synced: at its start through the wind-up, then on the straight
     * line down to its target. A meteor whose path has not arrived stands
     * where its last position put it.
     *
     * @param gameTime the game time with its partial tick
     * @return the point on the path
     */
    public Vec3 pathAt(double gameTime) {
        int fall = entityData.get(DATA_FALL_TICKS);
        if (fall <= 0) {
            return position();
        }
        double age = gameTime - entityData.get(DATA_CALLED_AT);
        return MeteorFall.positionAt(new Vec3(entityData.get(DATA_START)), new Vec3(entityData.get(DATA_TARGET)),
                Math.max(0, age - entityData.get(DATA_WIND_UP_TICKS)), fall);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        age++;
        int fallen = MeteorFall.fallenAt(age, windUpTicks);
        if (fallen == 0) {
            MeteorMarks.windUp(server, start, target, age);
            return;
        }
        if (fallen == 1) {
            MeteorMarks.drop(server, start);
        }
        Vec3 from = position();
        setPos(MeteorFall.positionAt(start, target, fallen, fallTicks));
        MeteorMarks.trail(server, from, position());
        if (fallen >= fallTicks) {
            strike(server);
        }
    }

    private void strike(ServerLevel server) {
        discard();
        GooExplosion.detonate(server, target, power, ExplosionMode.TNT, GooExplosion.Look.vanilla());
        MeteorMarks.impact(server, target);
        EntityVisuals.sendToWatchersOf(server, target, new MeteorScorchPayload(target, power + SCORCH_PAST_POWER));
    }

    /**
     * Draws the meteor from far off: vanilla draws an entity only within 64
     * blocks for each block of its size, which hid this one until it was
     * nearly down.
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSq) {
        return distanceSq < SEEN_FROM * SEEN_FROM;
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
        start = input.read(TAG_START, Vec3.CODEC).orElse(position());
        target = input.read(TAG_TARGET, Vec3.CODEC).orElse(position());
        power = input.read(TAG_POWER, Codec.FLOAT).orElse(0f);
        windUpTicks = Math.max(0, input.getIntOr(TAG_WIND_UP_TICKS, 0));
        fallTicks = Math.max(1, input.getIntOr(TAG_FALL_TICKS, 1));
        age = input.getIntOr(TAG_AGE, 0);
        syncPath(level().getGameTime() - age);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store(TAG_START, Vec3.CODEC, start);
        output.store(TAG_TARGET, Vec3.CODEC, target);
        output.store(TAG_POWER, Codec.FLOAT, power);
        output.putInt(TAG_WIND_UP_TICKS, windUpTicks);
        output.putInt(TAG_FALL_TICKS, fallTicks);
        output.putInt(TAG_AGE, age);
    }
}
