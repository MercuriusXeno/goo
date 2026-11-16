package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The tint a volume cell's layers and drips draw under: its goo type's
 * highlight at the shell's alpha, over the goo type's own two-toned fluid
 * sprite its models name, the look the palm blob's shell and the dropped goo
 * cuboid wear, so world goo reads as goo rather than reskinned slime. The
 * block renderer multiplies all four channels by it, so the alpha makes the
 * layers translucent.
 * connected-goo-draws-as-one-layered-mass-with-a-core
 * palm-blob-draws-as-the-charge-blob
 * goo-types-are-more-two-toned
 */
public final class VolumeCellTint implements BlockTintSource {

    /**
     * The layers' alpha, the palm blob shell's: the tuning a thicker or
     * clearer world goo turns.
     */
    public static final int SHELL_ALPHA = 0x60;
    /** The tint index a cell's slime core draws under, after the layers' own at 0. */
    public static final int CORE_TINT_INDEX = 1;
    /**
     * The core's tint: the shell's alpha over its fluid sprite's own colors.
     * slime-core-draws-translucent-at-the-shells-opacity
     */
    private static final BlockTintSource CORE_TINT = state -> ARGB.color(SHELL_ALPHA, 0xFFFFFF);

    private final ResourceKey<GooTypeDefinition> gooType;
    private final ToIntFunction<ResourceKey<GooTypeDefinition>> colorOf;

    /**
     * Creates the tint of one goo type's volume cell.
     *
     * @param gooType the goo type the cell holds
     * @param colorOf the RGB a goo type draws in, its highlight
     */
    public VolumeCellTint(ResourceKey<GooTypeDefinition> gooType,
                          ToIntFunction<ResourceKey<GooTypeDefinition>> colorOf) {
        this.gooType = gooType;
        this.colorOf = colorOf;
    }

    /**
     * The tints a goo type's volume cell registers, in tint index order: the
     * layers' and drips' at 0, the core's at {@link #CORE_TINT_INDEX}.
     *
     * @param gooType the goo type the cell holds
     * @param colorOf the RGB a goo type draws in, its highlight
     * @return the tint sources
     */
    public static List<BlockTintSource> sources(ResourceKey<GooTypeDefinition> gooType,
                                                ToIntFunction<ResourceKey<GooTypeDefinition>> colorOf) {
        return List.of(new VolumeCellTint(gooType, colorOf), CORE_TINT);
    }

    @Override
    public int color(BlockState state) {
        return ARGB.color(SHELL_ALPHA, colorOf.applyAsInt(gooType));
    }
}
