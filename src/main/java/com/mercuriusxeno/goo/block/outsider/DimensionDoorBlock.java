package com.mercuriusxeno.goo.block.outsider;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The dimension door standing in each Outsider room: used, it carries the
 * player back to the exact place they cast Outsider from. Nothing breaks it,
 * and the pocket stands it again if anything removes it.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
 */
public class DimensionDoorBlock extends Block {

    /**
     * @param properties the block's properties
     */
    public DimensionDoorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (player instanceof ServerPlayer traveller) {
            OutsiderRooms.returnHome(traveller);
        }
        return InteractionResult.SUCCESS;
    }
}
