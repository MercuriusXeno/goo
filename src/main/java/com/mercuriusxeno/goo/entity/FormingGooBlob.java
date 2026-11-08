package com.mercuriusxeno.goo.entity;

import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooEntities;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The goo blob Unmake's chunks converge on: it hovers where it formed,
 * grows as each unmade block's goo fills it, drifts into any other forming
 * blob within 3 blocks and merges with it, and once nothing more is on its
 * way it sits a moment, shrinks to a goo item's size and becomes the goo
 * item, one per goo type it holds.
 * unmake-converges-near-the-unmade-blocks-into-a-goo-item
 */
public class FormingGooBlob extends Entity {

    /** Blocks an unmade block's chunks travel to a blob at most, and blobs draw together within. */
    public static final double REACH = 3.0;
    /**
     * Ticks a blob hovers after the last goo it waits on arrives, before it
     * shrinks: a moment, so it becomes the goo item about as its stream ends.
     */
    public static final int HOVER_TICKS = 2;
    /** Ticks a blob takes to shrink to a goo item's size. */
    public static final int SHRINK_TICKS = 6;
    /** Blocks a blob drifts toward its nearest neighbor each tick. */
    static final double DRIFT_PER_TICK = 0.04;
    /** Blocks apart at which two drifting blobs become one. */
    static final double MERGE_DISTANCE = 0.2;
    private static final double HALF = 0.5;
    /** The most times a goo item's size a filling blob draws. */
    static final double LARGEST_FILL = 3;
    /** How much larger than a goo item a blob holding a thousand mB draws. */
    static final double FILL_GROWTH = 1;
    /** The share of the way to its size a blob's drawn size closes each tick, so it grows and shrinks smoothly. */
    private static final float EASE_PER_TICK = 0.4f;

    private static final String TAG_HELD = "Held";
    private static final String TAG_HOLD_UNTIL = "HoldUntil";
    private static final EntityDataAccessor<ItemStack> SHOWN =
            SynchedEntityData.defineId(FormingGooBlob.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> SHRINKING =
            SynchedEntityData.defineId(FormingGooBlob.class, EntityDataSerializers.BOOLEAN);

    private GooContents held = GooContents.EMPTY;
    private long holdUntil;
    private int shrinkingSince = -1;
    private @Nullable FormingGooBlob mergedInto;
    private float shownScale;
    private float lastShownScale;

    /**
     * The constructor the entity type builds a loaded or synced blob with.
     *
     * @param type  the entity type
     * @param level the level
     */
    public FormingGooBlob(EntityType<? extends FormingGooBlob> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /**
     * The blob an unmade block's chunks converge on: the nearest forming
     * within {@link #REACH} of it, or a new one at the block.
     *
     * @param level the server level
     * @param block the unmade block's middle
     * @return the blob
     */
    public static FormingGooBlob gatherNear(ServerLevel level, Vec3 block) {
        List<FormingGooBlob> forming = level.getEntitiesOfClass(FormingGooBlob.class,
                new AABB(block, block).inflate(REACH), FormingGooBlob::forming);
        return nearestWithin(block, forming, Entity::position).orElseGet(() -> {
            FormingGooBlob blob = new FormingGooBlob(GooEntities.FORMING_GOO_BLOB.get(), level);
            blob.setPos(block);
            blob.holdUntil = level.getGameTime() + HOVER_TICKS;
            level.addFreshEntity(blob);
            return blob;
        });
    }

    /**
     * The nearest of some blobs within {@link #REACH} of a point.
     *
     * @param from    the point
     * @param blobs   the blobs
     * @param placeOf where each blob is
     * @param <T>     what a blob is
     * @return the nearest within reach, or empty where none is
     */
    static <T> Optional<T> nearestWithin(Vec3 from, Collection<T> blobs, Function<T, Vec3> placeOf) {
        return blobs.stream().filter(blob -> placeOf.apply(blob).distanceTo(from) <= REACH)
                .min(Comparator.comparingDouble(blob -> placeOf.apply(blob).distanceToSqr(from)));
    }

    /**
     * Where a blob drifts this tick: toward its nearest neighbor within
     * {@link #REACH}, {@link #DRIFT_PER_TICK} at most and never past the
     * middle between them, or nowhere when none is that near.
     *
     * @param self   where the blob is
     * @param others where the other forming blobs are
     * @return where it is next tick
     */
    static Vec3 driftStep(Vec3 self, Collection<Vec3> others) {
        return nearestWithin(self, others, Function.identity()).map(nearest -> {
            Vec3 toward = nearest.subtract(self);
            double step = Math.min(DRIFT_PER_TICK, toward.length() * HALF);
            return step <= 0 ? self : self.add(toward.normalize().scale(step));
        }).orElse(self);
    }

    /**
     * @param one   where one blob is
     * @param other where another is
     * @return whether they are near enough to become one
     */
    static boolean merges(Vec3 one, Vec3 other) {
        return one.distanceTo(other) <= MERGE_DISTANCE;
    }

    /**
     * Holds the blob until goo on its way arrives, and its hover after.
     *
     * @param arrives the game time the goo arrives
     */
    public void expect(long arrives) {
        holdUntil = Math.max(holdUntil, arrives + HOVER_TICKS);
    }

    /**
     * Fills the blob with goo that arrived, or, once it has shrunk away or
     * started to, a new blob where it stood.
     *
     * @param goo the whole mB that arrived
     */
    public void feed(GooContents goo) {
        if (!(level() instanceof ServerLevel server) || goo.isEmpty()) {
            return;
        }
        FormingGooBlob into = resolve();
        if (!into.forming()) {
            into = gatherNear(server, into.position());
        }
        into.held = into.held.mergeWith(goo);
        into.holdUntil = Math.max(into.holdUntil, server.getGameTime() + HOVER_TICKS);
        into.refreshShown();
    }

    /**
     * @return the blob this one merged into, followed to the one standing, or this one
     */
    public FormingGooBlob resolve() {
        FormingGooBlob at = this;
        while (at.mergedInto != null) {
            at = at.mergedInto;
        }
        return at;
    }

    /**
     * @return the goo item the blob shows: of the type it holds most, holding all it holds
     */
    public ItemStack shown() {
        return entityData.get(SHOWN);
    }

    /**
     * @return whether the blob is shrinking to a goo item
     */
    public boolean shrinking() {
        return entityData.get(SHRINKING);
    }

    private boolean forming() {
        return isAlive() && !shrinking();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(SHOWN, ItemStack.EMPTY);
        entityData.define(SHRINKING, false);
    }

    /**
     * How large the blob draws for the goo it holds, against a goo item's
     * own size: nothing while empty, so it never pops in, then larger by the
     * cube root of its goo, up to {@link #LARGEST_FILL}.
     *
     * @param volume the mB it holds
     * @return its drawn size, 1 for a goo item's
     */
    public static float fillScale(long volume) {
        if (volume <= 0) {
            return 0;
        }
        return (float) Math.min(LARGEST_FILL, 1 + FILL_GROWTH * Math.cbrt(volume / (double) GooStacks.THOUSAND));
    }

    /**
     * @param partialTick the partial tick
     * @return how large the blob draws this frame, 1 for a goo item's size
     */
    public float shownScale(float partialTick) {
        return lastShownScale + (shownScale - lastShownScale) * partialTick;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            lastShownScale = shownScale;
            float target = shrinking() ? 1 : fillScale(GooStacks.volumeOf(shown()));
            shownScale += (target - shownScale) * EASE_PER_TICK;
            return;
        }
        if (shrinking()) {
            if (tickCount - shrinkingSince >= SHRINK_TICKS) {
                becomeItems(server);
            }
            return;
        }
        List<FormingGooBlob> others = server.getEntitiesOfClass(FormingGooBlob.class,
                getBoundingBox().inflate(REACH), other -> other != this && other.forming());
        for (FormingGooBlob other : others) {
            if (getId() < other.getId() && merges(position(), other.position())) {
                absorb(other);
            }
        }
        setPos(driftStep(position(), others.stream().filter(Entity::isAlive).map(Entity::position).toList()));
        if (server.getGameTime() > holdUntil) {
            startShrinking();
        }
    }

    private void absorb(FormingGooBlob other) {
        held = held.mergeWith(other.held);
        holdUntil = Math.max(holdUntil, other.holdUntil);
        other.held = GooContents.EMPTY;
        other.mergedInto = this;
        other.discard();
        refreshShown();
    }

    private void startShrinking() {
        if (held.isEmpty()) {
            discard();
            return;
        }
        shrinkingSince = tickCount;
        entityData.set(SHRINKING, true);
    }

    private void becomeItems(ServerLevel server) {
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> entry : held.getAll().entrySet()) {
            ItemStack stack = GooStacks.createForOutput(entry.getKey(), entry.getValue());
            if (!stack.isEmpty()) {
                ItemEntity item = new ItemEntity(server, getX(), getY(), getZ(), stack, 0, 0, 0);
                item.setDefaultPickUpDelay();
                server.addFreshEntity(item);
            }
        }
        held = GooContents.EMPTY;
        discard();
    }

    private void refreshShown() {
        entityData.set(SHOWN, held.isEmpty() ? ItemStack.EMPTY
                : GooStacks.createForOutput(held.largestType(), (int) Math.min(Integer.MAX_VALUE, held.totalVolume())));
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
        held = input.read(TAG_HELD, GooContents.CODEC).orElse(GooContents.EMPTY);
        holdUntil = input.getLongOr(TAG_HOLD_UNTIL, 0L);
        refreshShown();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store(TAG_HELD, GooContents.CODEC, held);
        output.putLong(TAG_HOLD_UNTIL, holdUntil);
    }
}
