package com.mercuriusxeno.goo.lunar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.SurfaceRules;

/**
 * The {@code goo:sea_cavern} surface rule condition: it passes a block in one part of the sea cavern, read from the
 * same seeded noises the cavern's density opens its air from, so lunar's surface rule lays molten iron in the sea and
 * iron core in the floor under it.
 * decision lunar-underground-changes-by-depth-down-to-a-molten-iron-sea
 *
 * @param part the part of the cavern the condition passes
 */
public record SeaCavernCondition(Part part) implements SurfaceRules.ConditionSource {

    /** The data codec of the condition, naming its part. */
    public static final MapCodec<SeaCavernCondition> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Part.CODEC.fieldOf("part").forGetter(SeaCavernCondition::part)
    ).apply(instance, SeaCavernCondition::new));

    /**
     * A part of the sea cavern.
     */
    public enum Part implements StringRepresentable {
        /** The molten iron under the sea's level, over the floor, off every pillar. */
        SEA("sea"),
        /** The iron core of the sea floor and everything under it, islands among it. */
        CORE("core");

        /** The part's codec, by its name. */
        public static final Codec<Part> CODEC = StringRepresentable.fromEnum(Part::values);
        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    @Override
    public MapCodec<? extends SurfaceRules.ConditionSource> codec() {
        return DATA_CODEC;
    }

    @Override
    public SurfaceRules.Condition apply(SurfaceRules.Context context) {
        SeaCavern cavern = SeaCavern.seeded(context.randomState.getOrCreateNoise(SeaCavern.FLOOR_NOISE),
                context.randomState.getOrCreateNoise(SeaCavern.ROOF_NOISE),
                context.randomState.getOrCreateNoise(SeaCavern.PILLAR_NOISE));
        return new BlockTest(context, cavern, part);
    }

    /**
     * Tests the block the surface rules stand on, reading the column's floor and pillar once per column.
     */
    private static final class BlockTest implements SurfaceRules.Condition {
        private final SurfaceRules.Context context;
        private final SeaCavern cavern;
        private final Part part;
        private int testedX = Integer.MIN_VALUE;
        private int testedZ = Integer.MIN_VALUE;
        private int floorY;
        private boolean pillar;

        private BlockTest(SurfaceRules.Context context, SeaCavern cavern, Part part) {
            this.context = context;
            this.cavern = cavern;
            this.part = part;
        }

        @Override
        public boolean test() {
            if (context.blockX != testedX || context.blockZ != testedZ) {
                testedX = context.blockX;
                testedZ = context.blockZ;
                floorY = cavern.floorYAt(testedX, testedZ);
                pillar = cavern.isPillarAt(testedX, testedZ);
            }
            return part == Part.CORE ? context.blockY <= floorY : SeaCavern.isSea(context.blockY, floorY, pillar);
        }
    }
}
