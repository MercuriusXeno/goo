package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ISidedProxy;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.LoadedAbilityCost;
import com.mercuriusxeno.goo.ability.program.SlickStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import java.util.List;
import java.util.Optional;

/**
 * Kinetic's slick: a sixteenth-thin film of electric blue and white goo laid
 * on a block's top, slippery as ice. A thing trying to move on it has its
 * horizontal velocity ramped each tick by a ramp that grows the longer it
 * keeps trying, as kinetic_slick.json's slick step names; anything else only
 * slides. The client reads that step from the abilities it was synced, since
 * it moves its own player. It stands until something destroys it, then drops
 * the kinetic goo its film cost.
 * slick-coats-the-ground-with-kinetic-speed
 * slick-ramp-grows-while-a-thing-keeps-moving
 * kinetic-is-electric-blue-and-white
 * breaking-slick-drops-kinetic-goo-worth-its-film
 */
public class KineticSlickBlock extends Block {

    /** The ability whose slick step names the ramp. */
    public static final Identifier SLICK_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "kinetic_slick");
    /** Ice's friction, so the film is as slippery as ice. */
    public static final float SLIPPERY = 0.98F;
    private static final VoxelShape FILM = box(0, 0, 0, 16, 1, 16);

    /**
     * Creates the slick block.
     *
     * @param properties the block properties
     */
    public KineticSlickBlock(Properties properties) {
        super(properties);
    }

    /**
     * The goo a broken film gives back.
     *
     * @param type the goo type
     * @param mb   the goo in mb
     */
    record Reclaim(ResourceKey<GooTypeDefinition> type, int mb) {
    }

    /**
     * The kinetic goo a broken film gives back: Slick's cost, the price the
     * stream charged the film, the whole mb of it.
     * breaking-slick-drops-kinetic-goo-worth-its-film
     * slick-drains-per-film-it-lays
     *
     * @param slickCost Slick's cost, the price of one film
     * @return the goo
     */
    static Reclaim reclaimed(GooFraction slickCost) {
        return new Reclaim(GooTypes.KINETIC, slickCost.floor());
    }

    /**
     * Gives back the kinetic goo the film cost, whatever breaks it; a film
     * broken where no Slick definition loads gives back nothing.
     * breaking-slick-drops-kinetic-goo-worth-its-film
     */
    @Override
    protected @NonNull List<ItemStack> getDrops(@NonNull BlockState state, LootParams.@NonNull Builder builder) {
        return LoadedAbilityCost.of(builder.getLevel(), SLICK_ABILITY).map(KineticSlickBlock::reclaimed)
                .filter(reclaim -> reclaim.mb() > 0)
                .map(reclaim -> List.of(GooStacks.createForOutput(reclaim.type(), reclaim.mb()))).orElse(List.of());
    }

    /**
     * Refuses a placed block the cell, so building on slick sets the block
     * over it rather than deleting the goo its film holds; the cell stays
     * replaceable for the slick step and the rules that read
     * BlockState.canBeReplaced().
     * breaking-slick-drops-kinetic-goo-worth-its-film
     */
    @Override
    protected boolean canBeReplaced(@NonNull BlockState state, @NonNull BlockPlaceContext context) {
        return false;
    }

    /**
     * Ramps the horizontal velocity of a thing trying to move on the slick.
     */
    @Override
    public void stepOn(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state,
                       @NonNull Entity entity) {
        SlickStep.in(slickProgram(level)).ifPresent(slick -> SlickBuild.slide(entity, slick));
        super.stepOn(level, pos, state, entity);
    }

    /**
     * The steps Slick runs, from the server's registry or the client's synced abilities.
     *
     * @param level the level the slick stands in
     * @return Slick's steps, empty where no Slick is loaded
     */
    private static List<Step> slickProgram(Level level) {
        if (level.isClientSide()) {
            return ISidedProxy.get().syncedAbilityProgram(SLICK_ABILITY.toString());
        }
        return Optional.ofNullable(AbilityRegistry.of(level).getAbility(SLICK_ABILITY))
                .map(AbilityDefinition::behaviors).orElse(List.of());
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext ctx) {
        return FILM;
    }

    @Override
    protected @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                                    @NonNull BlockPos pos, @NonNull CollisionContext ctx) {
        return FILM;
    }

    /**
     * Whether the film hides its face toward a neighbour: toward another
     * film, so neighbouring slick reads as one film with no seam between, as
     * goop's coat does.
     * diagnose-and-correct-the-slick-film-seams
     *
     * @param neighbour the state of the cell beside the face
     * @return true where the face is not drawn
     */
    static boolean hidesFaceToward(BlockState neighbour) {
        return neighbour.getBlock() instanceof KineticSlickBlock;
    }

    /**
     * Hides a face toward neighbouring slick. Slick registers noOcclusion, so
     * its model's cullfaces never cull against another film on their own.
     */
    @Override
    protected boolean skipRendering(@NonNull BlockState state, @NonNull BlockState neighbour,
                                    @NonNull Direction direction) {
        return hidesFaceToward(neighbour) || super.skipRendering(state, neighbour, direction);
    }

    @Override
    protected boolean canSurvive(@NonNull BlockState state, @NonNull LevelReader level, @NonNull BlockPos pos) {
        return SlimeCoatBlock.liesOn(level, pos);
    }

    /**
     * Drops the slick where its top is gone.
     */
    @Override
    protected @NonNull BlockState updateShape(@NonNull BlockState state, @NonNull LevelReader level,
                                              @NonNull ScheduledTickAccess ticks, @NonNull BlockPos pos,
                                              @NonNull Direction direction, @NonNull BlockPos neighborPos,
                                              @NonNull BlockState neighborState, @NonNull RandomSource random) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }
}
