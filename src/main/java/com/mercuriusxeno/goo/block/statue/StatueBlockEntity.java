package com.mercuriusxeno.goo.block.statue;

import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.GooSyncedBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The mob a statue was: its type, the way it faced, whether it was a baby,
 * and the experience it gives when the statue is mined, saved with the
 * statue and synced to the clients that draw it
 * (decision petrify-stone-encasement-and-calcify-map).
 */
public class StatueBlockEntity extends GooSyncedBlockEntity {

    private static final String TAG_ENTITY = "entity";
    private static final String TAG_YAW = "yaw";
    private static final String TAG_BABY = "baby";
    private static final String TAG_EXPERIENCE = "experience";
    /** The saved type of a statue holding no mob. */
    private static final String NO_ENTITY = "";

    private @Nullable Identifier entityType;
    private float yaw;
    private boolean baby;
    private int experience;

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
     * Holds the mob the statue was, then saves and syncs it.
     *
     * @param mob        the petrified mob
     * @param typeId     the id of the mob's type
     * @param experience the experience it gives when mined
     */
    public void hold(Mob mob, Identifier typeId, int experience) {
        this.entityType = typeId;
        this.yaw = mob.yBodyRot;
        this.baby = mob.isBaby();
        this.experience = experience;
        setChanged();
        BlockEntitySync.markDirtyAndSync(this);
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

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        entityType = Identifier.tryParse(input.getStringOr(TAG_ENTITY, NO_ENTITY));
        yaw = input.getFloatOr(TAG_YAW, 0f);
        baby = input.getBooleanOr(TAG_BABY, false);
        experience = input.getIntOr(TAG_EXPERIENCE, 0);
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
    }
}
