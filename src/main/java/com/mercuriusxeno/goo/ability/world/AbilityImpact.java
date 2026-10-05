package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;

/**
 * What an ability goo does when it lands on a block: it places the
 * ability's chain marker, whose program runs from that tick (decisions
 * place-block-ability-grows-block, splat-runs-the-program-no-fuse).
 */
public final class AbilityImpact {

    private AbilityImpact() {
    }

    /**
     * Lands an ability goo on a block.
     *
     * @param level   the server level
     * @param pos     the struck block
     * @param type    the goo type thrown
     * @param face    the struck face
     * @param ability the ability the goo names
     */
    public static void land(ServerLevel level, BlockPos pos, ResourceKey<GooTypeDefinition> type,
                            Direction face, AbilityDefinition ability) {
        EffectBlockPlacement.placeAbility(level, pos, type, face, ability);
    }
}
