package com.mercuriusxeno.goo.entity;

import com.mercuriusxeno.goo.ability.quantum.OutOfPhase;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * An Eldritch Monstrosity: a mob living out of phase in the space between
 * dimensions. It stands phased from the moment it enters a level, so a plain
 * player neither sees it nor is hunted by it, an eldritch player sees it and
 * is hunted, and only a phased attacker can hurt it. It dies into xeno goo.
 * monstrosities-harvest-under-quantum
 */
public class EldritchMonstrosity extends Monster {

    /** The xeno goo a monstrosity dies into, in millibuckets: the first source of xeno. */
    public static final int XENO_DROP_MB = GooStacks.THOUSAND / 2;
    /** The highest block a monstrosity spawns below: the deep, under the world's old floor. */
    public static final int SPAWNS_BELOW_Y = 0;

    private static final double MAX_HEALTH = 30.0;
    private static final double ATTACK_DAMAGE = 5.0;
    private static final double MOVEMENT_SPEED = 0.25;
    private static final double FOLLOW_RANGE = 32.0;
    private static final double ATTACK_SPEED_MODIFIER = 1.0;
    private static final double STROLL_SPEED_MODIFIER = 0.8;
    private static final float LOOK_DISTANCE = 8.0f;
    private static final int FLOAT_PRIORITY = 0;
    private static final int ATTACK_PRIORITY = 2;
    private static final int STROLL_PRIORITY = 7;
    private static final int LOOK_PRIORITY = 8;
    private static final int RETALIATE_PRIORITY = 1;
    private static final int HUNT_PRIORITY = 2;

    /**
     * Creates a monstrosity.
     *
     * @param type  its type
     * @param level the level it stands in
     */
    public EldritchMonstrosity(EntityType<? extends EldritchMonstrosity> type, Level level) {
        super(type, level);
    }

    /**
     * The attributes a monstrosity stands with.
     *
     * @return its attribute builder
     */
    public static AttributeSupplier.Builder createAttributes() {
        return createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
    }

    /**
     * Whether a monstrosity may spawn at a spot: a dark one, deep below.
     *
     * @param type   its type
     * @param level  the level
     * @param reason why it spawns
     * @param pos    the spot
     * @param random the spawn's random source
     * @return true where a monstrosity may spawn
     */
    public static boolean checkSpawnRules(EntityType<EldritchMonstrosity> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return pos.getY() < SPAWNS_BELOW_Y && checkMonsterSpawnRules(type, level, reason, pos, random);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(FLOAT_PRIORITY, new FloatGoal(this));
        goalSelector.addGoal(ATTACK_PRIORITY, new MeleeAttackGoal(this, ATTACK_SPEED_MODIFIER, false));
        goalSelector.addGoal(STROLL_PRIORITY, new WaterAvoidingRandomStrollGoal(this, STROLL_SPEED_MODIFIER));
        goalSelector.addGoal(LOOK_PRIORITY, new LookAtPlayerGoal(this, Player.class, LOOK_DISTANCE));
        goalSelector.addGoal(LOOK_PRIORITY, new RandomLookAroundGoal(this));
        targetSelector.addGoal(RETALIATE_PRIORITY, new HurtByTargetGoal(this));
        // PhaseEvents and EldritchEvents turn the aim from any player neither phased nor eldritch.
        targetSelector.addGoal(HUNT_PRIORITY, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /**
     * Lays the monstrosity's phase as it enters a level, a phase that never fades.
     */
    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide()) {
            setData(GooAttachments.OUT_OF_PHASE, OutOfPhase.held());
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        spawnAtLocation(level, GooStacks.createForOutput(GooTypes.XENO, XENO_DROP_MB));
    }
}
