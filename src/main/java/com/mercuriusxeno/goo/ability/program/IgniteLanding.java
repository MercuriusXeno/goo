package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.block.ability.AbilityBlockEntity;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Ignite's landing: a fire lit in the cell the blob lands in, and the
 * explosive ability block it struck set off as a fiery Goo explosion, the
 * block's own burnout playing as it goes.
 * ignite-fire-and-explosive-combo
 * <p>
 * The operator's rulings (idea ignite-combo-rulings): the combo goes off at
 * Lurker's own power, 2.5; fire in vanilla's one in
 * three of the cleared floor cells; both burnouts with vanilla's boom.
 */
final class IgniteLanding {

    private IgniteLanding() {
    }

    /**
     * Lights a fire in the cell where fire can stand there.
     *
     * @param level the server level
     * @param cell  the landing cell
     * @param face  the struck face
     */
    static void lightFire(ServerLevel level, BlockPos cell, Direction face) {
        if (BaseFireBlock.canBePlacedAt(level, cell, face)) {
            level.setBlock(cell, BaseFireBlock.getState(level, cell), Block.UPDATE_ALL);
        }
    }

    /**
     * Sets off the explosive ability block the blob struck, or the one in its
     * cell: its burnout plays, it goes, and a fiery explosion takes its place.
     *
     * @param level the server level
     * @param cell  the landing cell
     * @param face  the struck face
     * @param power the explosion's power
     * @return true when an explosive block was set off
     */
    static boolean detonateExplosive(ServerLevel level, BlockPos cell, Direction face, float power) {
        Optional<AbilityBlockEntity> explosive = Stream.of(cell.relative(face.getOpposite()), cell)
                .map(level::getBlockEntity).filter(AbilityBlockEntity.class::isInstance)
                .map(AbilityBlockEntity.class::cast).filter(be -> explosive(level, be)).findFirst();
        explosive.ifPresent(be -> {
            BlockPos pos = be.getBlockPos();
            new MarkerHost(level, pos, be).burnout().sendToTracking(level);
            level.removeBlock(pos, false);
            GooExplosion.detonate(level, Vec3.atCenterOf(pos), power, ExplosionMode.FIRE,
                    GooExplosion.Look.silent(GooParticles.SILENT_BLAST.get()));
        });
        return explosive.isPresent();
    }

    private static boolean explosive(ServerLevel level, AbilityBlockEntity be) {
        Identifier id = Identifier.tryParse(be.getAbilityId());
        AbilityDefinition ability = id == null ? null : AbilityRegistry.of(level).getAbility(id);
        return ability != null && ability.hasTag(AbilityTags.EXPLOSIVE);
    }
}
