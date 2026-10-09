package com.mercuriusxeno.goo.entity;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.FlightHost;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.ability.program.TravelingStep;
import com.mercuriusxeno.goo.network.GooEffectScheduler;
import com.mercuriusxeno.goo.registry.GooEntities;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * A goo that rolls through the air rather than flying an arc: it moves in a
 * straight line along its throw at its delivery's slow speed with no
 * gravity, running its ability's traveling steps where it is each tick, and
 * the first block or mob it touches, or the end of its range, ends it: the
 * rest of its program runs there as its landing. Frost's Orb rolls this way
 * and ends in a strong nova (decision orb-carries-a-swirling-nova).
 */
public class RollingGoo extends Projectile {

    private static final String LOG_REFUSED = "Traveling steps of {} refused to load on a rolling goo: {}";
    private static final String TAG_ABILITY = "ability";
    private static final String TAG_GOO_TYPE = "goo_type";
    /** The ability a goo carries before one is set. */
    private static final String NO_ABILITY = "";
    /** The struck entity a landing names when it lands at a point, not on an entity. */
    private static final int NO_ENTITY = -1;
    private static final String TAG_RANGE_LEFT = "range_left";

    /** The goo type, synced so clients draw the ball in its look. */
    private static final EntityDataAccessor<String> GOO_TYPE =
            SynchedEntityData.defineId(RollingGoo.class, EntityDataSerializers.STRING);
    /** The ability rolling, synced so clients draw the swirl its traveling step names. */
    private static final EntityDataAccessor<String> ABILITY =
            SynchedEntityData.defineId(RollingGoo.class, EntityDataSerializers.STRING);

    /** Blocks the goo may still roll before its range ends it. */
    private double rangeLeft;

    /**
     * Creates a rolling goo, as the entity type's factory does.
     *
     * @param type  the entity type
     * @param level the level
     */
    public RollingGoo(EntityType<? extends RollingGoo> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    /**
     * Sets a goo rolling from a point along a direction.
     *
     * @param level   the server level
     * @param thrower the throwing player
     * @param ability the ability thrown
     * @param from    where it leaves the hand
     * @param heading the unit direction it rolls along
     * @return the goo, added to the level
     */
    public static RollingGoo roll(ServerLevel level, ServerPlayer thrower, AbilityDefinition ability, Vec3 from,
                                  Vec3 heading) {
        RollingGoo goo = new RollingGoo(GooEntities.ROLLING_GOO.get(), level);
        goo.setOwner(thrower);
        goo.setPos(from);
        goo.entityData.set(GOO_TYPE, GooTypes.id(ability.gooType()));
        goo.entityData.set(ABILITY, ability.id().toString());
        goo.rangeLeft = ability.delivery().range();
        goo.setDeltaMovement(heading.normalize().scale(ability.delivery().blocksPerTick()));
        level.addFreshEntity(goo);
        return goo;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(GOO_TYPE, GooTypes.id(GooTypes.FROST));
        builder.define(ABILITY, NO_ABILITY);
    }

    /**
     * The goo type rolling, as clients draw it.
     *
     * @return the type, or null for a type this side does not hold
     */
    public @Nullable ResourceKey<GooTypeDefinition> gooType() {
        return GooTypes.byId(entityData.get(GOO_TYPE));
    }

    /**
     * The ability rolling.
     *
     * @return its id string
     */
    public String abilityId() {
        return entityData.get(ABILITY);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement();
        if (level() instanceof ServerLevel server) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                end(server, hit.getLocation(), hit instanceof BlockHitResult block ? block : null);
                return;
            }
            travel(server);
            rangeLeft -= motion.length();
            if (rangeLeft <= 0) {
                end(server, position(), null);
                return;
            }
        }
        setPos(position().add(motion));
    }

    /**
     * Runs the ability's traveling steps where the goo is this tick.
     *
     * @param server the server level
     */
    private void travel(ServerLevel server) {
        ability(server).flatMap(def -> TravelingStep.of(def.behaviors())).ifPresent(traveling -> {
            try {
                ProgramBehavior.forHost(traveling.steps(), HostKind.FLIGHT).tick(new FlightHost(server, position()));
            } catch (ProgramLoadException e) {
                Goo.LOGGER.error(LOG_REFUSED, abilityId(), e.getMessage());
                discard();
            }
        });
    }

    /**
     * Ends the goo: the rest of its program lands at the point it reached,
     * on the block face it struck, or at the open point where a mob or its
     * range stopped it.
     *
     * @param server the server level
     * @param point  where it ended
     * @param block  the block it struck, or null for a mob or the end of its range
     */
    private void end(ServerLevel server, Vec3 point, @Nullable BlockHitResult block) {
        ResourceKey<GooTypeDefinition> type = gooType();
        if (type != null && getOwner() instanceof ServerPlayer thrower) {
            BlockPos pos = block == null ? BlockPos.containing(point) : block.getBlockPos();
            Direction face = block == null ? Direction.UP : block.getDirection();
            GooEffectScheduler.landNow(new GooEffectScheduler.PendingEffect(server.getServer().getTickCount(),
                    server, thrower, type, NO_ENTITY, pos, face, abilityId(), null, point));
        }
        discard();
    }

    private Optional<AbilityDefinition> ability(ServerLevel server) {
        Identifier id = Identifier.tryParse(abilityId());
        return id == null ? Optional.empty() : Optional.ofNullable(AbilityRegistry.of(server).getAbility(id));
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity != getOwner();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString(TAG_ABILITY, abilityId());
        output.putString(TAG_GOO_TYPE, entityData.get(GOO_TYPE));
        output.putDouble(TAG_RANGE_LEFT, rangeLeft);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(ABILITY, input.getStringOr(TAG_ABILITY, NO_ABILITY));
        entityData.set(GOO_TYPE, input.getStringOr(TAG_GOO_TYPE, GooTypes.id(GooTypes.FROST)));
        rangeLeft = input.getDoubleOr(TAG_RANGE_LEFT, 0);
    }
}
