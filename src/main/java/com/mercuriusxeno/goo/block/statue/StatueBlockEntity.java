package com.mercuriusxeno.goo.block.statue;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.GooSyncedBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The mob a statue was: its type, the way it faced, whether it was a baby,
 * and the experience it gives when the statue is mined, saved with the
 * statue and synced to the clients that draw it
 * (decision petrify-stone-encasement-and-calcify-map); and the whole mob's
 * save, kept on the server alone, which Heal wakes back into that same mob
 * (decision statue-keeps-the-whole-mob).
 */
public class StatueBlockEntity extends GooSyncedBlockEntity {

    private static final String TAG_ENTITY = "entity";
    private static final String TAG_YAW = "yaw";
    private static final String TAG_BABY = "baby";
    private static final String TAG_EXPERIENCE = "experience";
    private static final String TAG_MOB = "mob";
    /** The saved type of a statue holding no mob. */
    private static final String NO_ENTITY = "";

    private @Nullable Identifier entityType;
    private float yaw;
    private boolean baby;
    private int experience;
    private @Nullable CompoundTag savedMob;

    /**
     * Creates the statue's block entity.
     *
     * @param pos   the statue's position
     * @param state the statue's block state
     */
    public StatueBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.STATUE.get(), pos, state);
    }

    /**
     * Holds the mob the statue was, its whole save included, then saves and syncs it.
     *
     * @param mob        the petrified mob, not yet discarded
     * @param typeId     the id of the mob's type
     * @param experience the experience it gives when mined
     */
    public void hold(Mob mob, Identifier typeId, int experience) {
        this.entityType = typeId;
        this.yaw = mob.yBodyRot;
        this.baby = mob.isBaby();
        this.experience = experience;
        this.savedMob = saveOf(mob);
        setChanged();
        BlockEntitySync.markDirtyAndSync(this);
    }

    private @Nullable CompoundTag saveOf(Mob mob) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(problemPath(),
                Goo.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, mob.registryAccess());
            return mob.save(output) ? output.buildResult() : null;
        }
    }

    /**
     * @return the id of the type of the mob the statue was, or null before it holds one
     */
    public @Nullable Identifier entityType() {
        return entityType;
    }

    /**
     * @return the body yaw the mob faced, in degrees
     */
    public float yaw() {
        return yaw;
    }

    /**
     * @return true when the mob was a baby
     */
    public boolean baby() {
        return baby;
    }

    /**
     * @return the experience the statue gives when mined
     */
    public int experience() {
        return experience;
    }

    /**
     * @return a copy of the whole mob's save, or null for a statue saved before statues kept the mob
     */
    public @Nullable CompoundTag savedMob() {
        return savedMob == null ? null : savedMob.copy();
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        entityType = Identifier.tryParse(input.getStringOr(TAG_ENTITY, NO_ENTITY));
        yaw = input.getFloatOr(TAG_YAW, 0f);
        baby = input.getBooleanOr(TAG_BABY, false);
        experience = input.getIntOr(TAG_EXPERIENCE, 0);
        savedMob = input.read(TAG_MOB, CompoundTag.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        if (entityType != null) {
            output.putString(TAG_ENTITY, entityType.toString());
        }
        output.putFloat(TAG_YAW, yaw);
        output.putBoolean(TAG_BABY, baby);
        output.putInt(TAG_EXPERIENCE, experience);
        output.storeNullable(TAG_MOB, CompoundTag.CODEC, savedMob);
    }

    /**
     * The renderer draws from the type, yaw and baby flag alone, so the
     * whole mob's save stays on the server.
     * decision statue-keeps-the-whole-mob
     */
    @Override
    protected CompoundTag syncedView(CompoundTag saved) {
        saved.remove(TAG_MOB);
        return saved;
    }
}
