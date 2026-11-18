package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.UndeadInversion;
import com.mercuriusxeno.goo.ability.spray.SprayPrograms;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

/**
 * Shroom's Mycosis cell: the volume cell bound to Mycosis' goo, which
 * infects a living thing touching it, standing on its top or wading in its
 * space, as the spore stream infected what it reached: the laying ability's
 * entity steps run on it, giving it the mycosis effect and the spores that
 * burst Mycosis from its corpse. A thing still carrying the effect is left
 * as it is, so the goo infects it anew once its infection lapses. The cell
 * spares the player who placed it.
 * mycosis-goo-infects-what-touches-it-as-the-spray-did
 * mycosis-is-the-residual-goo-that-infests-with-mushrooms
 */
public class MycosisCellBlock extends VolumeCellBlock {

    /**
     * Creates a Mycosis cell.
     *
     * @param properties the block properties
     * @param binding    the Mycosis binding
     */
    public MycosisCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
    }

    /**
     * Infects a thing standing on the goo's top, as every cell's surface then acts on it.
     */
    @Override
    public void stepOn(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state,
                       @NonNull Entity entity) {
        infect(level, pos, entity);
        super.stepOn(level, pos, state, entity);
    }

    /**
     * Infects a thing in the goo's space, wading in it or standing on a cell short of a whole block.
     */
    @Override
    protected void entityInside(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                @NonNull Entity entity, @NonNull InsideBlockEffectApplier effects,
                                boolean pastEdges) {
        infect(level, pos, entity);
        super.entityInside(state, level, pos, entity, effects, pastEdges);
    }

    /**
     * Runs the laying ability's entity steps on a living thing touching the
     * goo, on the server, the cell's placer as their source; the placer, and
     * a thing whose infection still stands, are left as they are.
     * spared as Vines spares its caster (operator ruling 2026-11-17 on Vines: the caster is spared their own goo)
     *
     * @param level  the level the cell stands in
     * @param pos    the cell
     * @param entity the entity touching the cell
     */
    private void infect(Level level, BlockPos pos, Entity entity) {
        if (level instanceof ServerLevel server && entity instanceof LivingEntity living && infectable(living)) {
            ServerPlayer placer = creditedPlayerAt(server, pos);
            AbilityDefinition mycosis = AbilityRegistry.of(server).getAbility(binding().layingAbility());
            if (mycosis != null && !UndeadInversion.sparesCaster(living, placer)) {
                SprayPrograms.runOnLiving(server, living, placer, mycosis);
            }
        }
    }

    /**
     * Whether the goo infects a living thing touching it: a live one, no
     * spectator, whose last infection has lapsed.
     *
     * @param living the living thing touching the cell
     * @return true where the goo infects it
     */
    private static boolean infectable(LivingEntity living) {
        return living.isAlive() && !living.isSpectator() && !living.hasEffect(GooMobEffects.MYCOSIS);
    }

    /**
     * A Mycosis cell spares its placer, so the cell remembers who placed it.
     * standing-goo-thing-credits-whoever-placed-it
     */
    @Override
    protected boolean creditsItsPlacer() {
        return true;
    }
}
