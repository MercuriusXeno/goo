package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import static java.util.Map.entry;

/**
 * Registry that maps each goo type to its polymorphic world effect.
 * Dispatch is a single map lookup - no switch statements.
 */
public final class WorldEffects {

    /**
     * One effect instance per goo type.
     */
    private static final Map<ResourceKey<GooTypeDefinition>, WorldEffect> EFFECTS = buildRegistry();

    private WorldEffects() {
    }

    /**
     * Applies the world effect for the given goo type at the target position.
     *
     * @param level      the world
     * @param pos        the target block position
     * @param type       the goo type whose effect to apply
     * @param targetFace the face of the block that was hit, or null if unknown
     */
    public static void apply(Level level, BlockPos pos, ResourceKey<GooTypeDefinition> type, @Nullable Direction targetFace) {
        WorldEffect effect = EFFECTS.get(type);
        if (effect != null) {
            effect.apply(level, pos, targetFace);
        }
    }

    /**
     * Builds the type-to-effect map. Every goo type key should have an entry.
     *
     * @return immutable type-to-effect map covering all 15 goo types
     */
    private static Map<ResourceKey<GooTypeDefinition>, WorldEffect> buildRegistry() {
        return new HashMap<>(Map.ofEntries(
                entry(GooTypes.ROCK, new RockBehavior()), entry(GooTypes.BLAZE, new BlazeBehavior()),
                entry(GooTypes.FROST, new FrostBehavior()), entry(GooTypes.METAL, new MetalEffect()),
                entry(GooTypes.CRYSTAL, new CrystalEffect()), entry(GooTypes.HEX, new HexEffect()),
                entry(GooTypes.LEAF, new LeafEffect()), entry(GooTypes.VITAL, new VitalEffect()),
                entry(GooTypes.SHROOM, new ShroomEffect()), entry(GooTypes.TYPHOON, new TyphoonEffect()),
                entry(GooTypes.GLOW, new GlowBehavior()), entry(GooTypes.PULSE, new PulseEffect()),
                entry(GooTypes.NETHER, new NetherEffect()), entry(GooTypes.ENDER, new EnderEffect()),
                entry(GooTypes.AEON, new AeonEffect()), entry(GooTypes.UNSTABLE, new UnstableBehavior())));
    }
}
