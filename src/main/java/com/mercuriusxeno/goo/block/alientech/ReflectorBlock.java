package com.mercuriusxeno.goo.block.alientech;

import com.mercuriusxeno.goo.block.BlockEntityTicks;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The reflector: an 8 pixel cuboid centered in its cell with a lens on each side, see-through in the middle, held
 * off a solid face by a short neck, the lens toward that face closed. A light rail leaves each open lens and joins
 * the next reflector it meets through clear air, open lens to open lens.
 * decision reflector-rails-carry-the-brightest-light
 * decision all-utilities-are-redesigned-as-alien-tech-blocks
 */
public class ReflectorBlock extends FaceMountedBlock {

    /** The cuboid's edge, and the neck's, in pixels. */
    private static final float CUBOID = 8f;
    private static final float NECK = 4f;
    private static final float CUBOID_FAR = 4f;
    private static final float CUBOID_NEAR = 12f;
    private static final float CELL = 16f;
    private static final BlockEntityTicks<ReflectorBlockEntity> TICKS =
            BlockEntityTicks.onServer(GooBlockEntities.REFLECTOR, ReflectorBlockEntity::serverTick);

    /**
     * Creates the block, pointing up off a floor.
     *
     * @param properties the block properties
     */
    public ReflectorBlock(Properties properties) {
        super(properties, Shapes.or(boxZ(CUBOID, CUBOID_FAR, CUBOID_NEAR), boxZ(NECK, CUBOID_NEAR, CELL)));
    }

    /**
     * Remembers the player who placed the reflector, whom the rails it lays credit.
     * standing-goo-thing-credits-whoever-placed-it
     */
    @Override
    public void setPlacedBy(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state,
                            @Nullable LivingEntity by, @NonNull ItemStack itemStack) {
        if (by != null && level.getBlockEntity(pos) instanceof ReflectorBlockEntity reflector) {
            reflector.placedBy(by.getUUID());
        }
    }

    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new ReflectorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(@NonNull Level level,
                                                                           @NonNull BlockState state,
                                                                           @NonNull BlockEntityType<T> type) {
        return TICKS.tickerFor(level, type);
    }
}
