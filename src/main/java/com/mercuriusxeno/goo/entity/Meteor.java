package com.mercuriusxeno.goo.entity;

import com.mercuriusxeno.goo.ability.program.ExplosionMode;
import com.mercuriusxeno.goo.ability.program.GooExplosion;
import com.mercuriusxeno.goo.ability.world.MeteorFall;
import com.mercuriusxeno.goo.registry.GooEntities;
import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Meteo's meteor: it appears high above its target, falls straight down to
 * it over its fall time trailing flame, and explodes there through Goo's
 * explosion, breaking blocks into a crater.
 * decision meteo-needs-a-clear-sky
 */
public class Meteor extends Entity {

    private static final String TAG_TARGET = "Target";
    private static final String TAG_START = "Start";
    private static final String TAG_POWER = "Power";
    private static final String TAG_FALL_TICKS = "FallTicks";
    private static final String TAG_AGE = "Age";
    private static final int TRAIL_PARTICLES = 6;
    private static final double TRAIL_SPREAD = 0.3;
    private static final double TRAIL_SPEED = 0.02;

    private Vec3 start = Vec3.ZERO;
    private Vec3 target = Vec3.ZERO;
    private float power;
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
     * the fall height or the world's top allows, and lands after the fall time.
     *
     * @param level     the server level
     * @param at        the point it strikes
     * @param power     the explosion's power
     * @param fallTicks the ticks it falls for
     */
    public static void call(ServerLevel level, Vec3 at, float power, int fallTicks) {
        Meteor meteor = new Meteor(GooEntities.METEOR.get(), level);
        meteor.target = at;
        meteor.start = MeteorFall.startAbove(at, level.getMaxY());
        meteor.power = power;
        meteor.fallTicks = Math.max(1, fallTicks);
        meteor.setPos(meteor.start);
        level.addFreshEntity(meteor);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        // the client draws the trail the server sends and reads nothing the server holds
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        age++;
        setPos(MeteorFall.positionAt(start, target, age, fallTicks));
        server.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), TRAIL_PARTICLES,
                TRAIL_SPREAD, TRAIL_SPREAD, TRAIL_SPREAD, TRAIL_SPEED);
        server.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), TRAIL_PARTICLES,
                TRAIL_SPREAD, TRAIL_SPREAD, TRAIL_SPREAD, TRAIL_SPEED);
        if (age >= fallTicks) {
            discard();
            GooExplosion.detonate(server, target, power, ExplosionMode.TNT, GooExplosion.Look.vanilla());
        }
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
        fallTicks = Math.max(1, input.getIntOr(TAG_FALL_TICKS, 1));
        age = input.getIntOr(TAG_AGE, 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store(TAG_START, Vec3.CODEC, start);
        output.store(TAG_TARGET, Vec3.CODEC, target);
        output.store(TAG_POWER, Codec.FLOAT, power);
        output.putInt(TAG_FALL_TICKS, fallTicks);
        output.putInt(TAG_AGE, age);
    }
}
