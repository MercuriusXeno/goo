package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.canister.SlottedCanisterData;
import com.mercuriusxeno.goo.block.gasket.GasketAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import java.util.function.Function;

/**
 * A machine whose goo glows: it holds the one light loop, and a machine names
 * only the goo amounts its light is read from (decision
 * machine-base-owns-the-lifecycle). A machine holding no goo, such as the
 * choral gasket, stays a plain {@link GooMachineBlockEntity}, so its syncs kick
 * no light.
 */
public abstract class GooGlowingMachineBlockEntity extends GooMachineBlockEntity implements IGooLightSource {

    /**
     * Creates a glowing machine block entity.
     *
     * @param type     the block entity type
     * @param pos      the block position
     * @param state    the block state
     * @param attacher builds the attachment for this machine
     */
    protected GooGlowingMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                           Function<BlockEntity, GasketAttachment> attacher) {
        super(type, pos, state, attacher);
    }

    /**
     * The goo amounts this machine's light is read from: by default each held
     * slot's canister against the slot's capacity.
     *
     * @return the light entries, read fresh each call
     */
    protected List<GooLightEntry> lightEntries() {
        SlottedCanisterData slots = heldSlots();
        return slots != null ? slots.lightEntries() : List.of();
    }

    /**
     * Sums each light entry's contribution, clamped to the vanilla 15-light
     * ceiling. Before placement no registry is reachable, so the emission reads 0.
     *
     * @return goo-derived block-light emission in [0, 15]
     */
    @Override
    public final int gooLightEmission() {
        Level current = getLevel();
        if (current == null) {
            return 0;
        }
        HolderLookup.Provider registries = current.registryAccess();
        int total = 0;
        for (GooLightEntry entry : lightEntries()) {
            int contribution = GooLightContribution.forSlot(
                    GooTypes.definition(registries, entry.type()), entry.amount(), entry.capacity());
            total = GooLightContribution.addClamped(total, contribution);
            if (total >= GooLightContribution.MAX_LIGHT) {
                return GooLightContribution.MAX_LIGHT;
            }
        }
        return total;
    }
}
