package com.mercuriusxeno.goo.block.ability;

import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.NonNull;
import java.util.Optional;

/**
 * A fungus goologged into a goo cell, as a waterlogged block shares its
 * space with water and as GoologgedSides hangs a vine on a cell's wall: a
 * fungal bud or the mushroom it ripens into stands on the cell's floor in the
 * cell's own block space, so growing it displaces no goo. A cell whose goo
 * grows fungus carries this one property; Mycosis' cell grows it, and the
 * fungus left standing once the goo is spent is the plain block.
 * mushrooms-and-buds-are-goo-logged-with-shroom-goo
 */
public enum GoologgedFungus implements StringRepresentable {

    /** No fungus stands in the cell. */
    NONE("none", 0),
    /** A bud fresh from the spores, at the fungal bud's first age. */
    BUD_0("bud_0", 0),
    /** A bud one age along. */
    BUD_1("bud_1", 1),
    /** A bud two ages along. */
    BUD_2("bud_2", 2),
    /** A bud at the fungal bud's full age, ripening into its mushroom next. */
    BUD_3("bud_3", FungalBudBlock.MAX_AGE),
    /** A brown mushroom. */
    BROWN_MUSHROOM("brown_mushroom", 0),
    /** A red mushroom. */
    RED_MUSHROOM("red_mushroom", 0),
    /** A crimson fungus, which a bud on crimson nylium ripens into. */
    CRIMSON_FUNGUS("crimson_fungus", 0),
    /** A warped fungus, which a bud on warped nylium ripens into. */
    WARPED_FUNGUS("warped_fungus", 0);

    /** The fungus standing in a goo cell's space. */
    public static final EnumProperty<GoologgedFungus> FUNGUS = EnumProperty.create("fungus", GoologgedFungus.class);

    private static final GoologgedFungus[] BUDS = {BUD_0, BUD_1, BUD_2, BUD_3};

    private final String serialized;
    private final int budAge;

    GoologgedFungus(String serialized, int budAge) {
        this.serialized = serialized;
        this.budAge = budAge;
    }

    @Override
    public @NonNull String getSerializedName() {
        return serialized;
    }

    /**
     * @return true for a bud of any age
     */
    public boolean isBud() {
        return this == BUD_0 || this == BUD_1 || this == BUD_2 || this == BUD_3;
    }

    /**
     * The fungus a cell's state holds.
     *
     * @param state a block state
     * @return the fungus, NONE for a state without the property
     */
    public static GoologgedFungus in(BlockState state) {
        return state.hasProperty(FUNGUS) ? state.getValue(FUNGUS) : NONE;
    }

    /**
     * Whether a block state holds goologged fungus, which a new bud stands beside no more than a placed one.
     *
     * @param state a block state
     * @return true where a cell holds a bud or a mushroom
     */
    public static boolean holdsFungus(BlockState state) {
        return in(state) != NONE;
    }

    /**
     * What this fungus is after one tending: a bud one age older, a ripe
     * bud the mushroom its floor grows, as a placed fungal bud ripens; a
     * mushroom stays as it is.
     * mycosis-grows-and-reaps-nether-wart
     *
     * @param floor  the block the cell lies on
     * @param random the random source choosing the mushroom
     * @return the fungus after the tending
     */
    public GoologgedFungus tended(BlockState floor, RandomSource random) {
        if (!isBud()) {
            return this;
        }
        if (budAge < FungalBudBlock.MAX_AGE) {
            return BUDS[budAge + 1];
        }
        return of(FungalBudBlock.growthOn(floor, random).defaultBlockState()).orElse(BROWN_MUSHROOM);
    }

    /**
     * The fungus a plain block is, as a goo cell holds it.
     *
     * @param plain a fungal bud or a mushroom
     * @return the goologged fungus, empty for any other block
     */
    public static Optional<GoologgedFungus> of(BlockState plain) {
        if (plain.getBlock() instanceof FungalBudBlock) {
            return Optional.of(BUDS[plain.getValue(FungalBudBlock.AGE)]);
        }
        for (GoologgedFungus fungus : values()) {
            if (fungus.plainBlock().filter(plain::is).isPresent()) {
                return Optional.of(fungus);
            }
        }
        return Optional.empty();
    }

    /**
     * The block this fungus stands as once no goo shares its space.
     *
     * @param bud the plain fungal bud's state
     * @return the plain fungal bud at its age or the mushroom, air for none
     */
    public BlockState standingAlone(BlockState bud) {
        if (isBud()) {
            return bud.setValue(FungalBudBlock.AGE, budAge);
        }
        return plainBlock().map(Block::defaultBlockState).orElse(Blocks.AIR.defaultBlockState());
    }

    private Optional<Block> plainBlock() {
        return switch (this) {
            case BROWN_MUSHROOM -> Optional.of(Blocks.BROWN_MUSHROOM);
            case RED_MUSHROOM -> Optional.of(Blocks.RED_MUSHROOM);
            case CRIMSON_FUNGUS -> Optional.of(Blocks.CRIMSON_FUNGUS);
            case WARPED_FUNGUS -> Optional.of(Blocks.WARPED_FUNGUS);
            default -> Optional.empty();
        };
    }
}
