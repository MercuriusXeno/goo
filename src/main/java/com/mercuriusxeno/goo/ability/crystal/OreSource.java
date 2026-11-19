package com.mercuriusxeno.goo.ability.crystal;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.util.function.Function;

/**
 * The ore a sense's front can find: each block's ore, and whether a
 * sixteen-block section may hold any, so a walk skips every block of a
 * section that cannot.
 * decision glitter-sphere-icons-gem-ore-groups
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 */
public interface OreSource {

    /**
     * @param pos a block
     * @return the ore standing there, or null where none of the sense's ore stands
     */
    @Nullable Identifier oreAt(BlockPos pos);

    /**
     * Whether a section may hold the sense's ore: false only where no block
     * of it can answer an ore.
     *
     * @param sectionX the section's x, in sections
     * @param sectionY the section's y, in sections
     * @param sectionZ the section's z, in sections
     * @return true when the section may hold ore
     */
    boolean mayHoldOre(int sectionX, int sectionY, int sectionZ);

    /**
     * Ore read block by block, every section taken as one that may hold it.
     *
     * @param oreAt each block's ore, or null
     * @return the source
     */
    static OreSource everywhere(Function<BlockPos, @Nullable Identifier> oreAt) {
        return new OreSource() {
            @Override
            public @Nullable Identifier oreAt(BlockPos pos) {
                return oreAt.apply(pos);
            }

            @Override
            public boolean mayHoldOre(int sectionX, int sectionY, int sectionZ) {
                return true;
            }
        };
    }
}
