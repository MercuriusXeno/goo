package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import net.minecraft.resources.ResourceKey;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Central registry for chain effect profiles. Each goo type with a chain
 * effect gets a profile that defines fuse duration, max stacks and range
 * formula; the post-fuse behavior is always the marker's ability. Profiles
 * are registered during mod init and looked up at runtime by the chain
 * marker block entity, the renderer and the throw sender.
 */
public final class ChainProfiles {

    private static final int BLAZE_FUSE_TICKS = 30;
    private static final int BLAZE_MAX_STACKS = ChainFootprint.MAX_STACKS;
    private static final int ROCK_FUSE_TICKS = 30;
    private static final int ROCK_MAX_STACKS = ChainFootprint.MAX_STACKS;
    private static final int NETHER_FUSE_TICKS = 30;
    private static final int NETHER_MAX_STACKS = 5;
    private static final int FROST_FUSE_TICKS = 30;
    private static final int FROST_MAX_STACKS = 4;
    private static final int METAL_FUSE_TICKS = 30;
    private static final int METAL_MAX_STACKS = 8;
    private static final int CRYSTAL_FUSE_TICKS = 30;
    private static final int CRYSTAL_MAX_STACKS = 8;
    private static final int UNSTABLE_FUSE_TICKS = 20;
    private static final int UNSTABLE_MAX_STACKS = 8;
    private static final int GLOW_FUSE_TICKS = 30;
    private static final int GLOW_MAX_STACKS = 4;

    private ChainProfiles() {
    }

    /**
     * Called once from {@link com.mercuriusxeno.goo.Goo#commonSetup}.
     */
    public static void registerAll() {
        registerBlaze();
        registerRock();
        registerNether();
        registerFrost();
        registerMetal();
        registerCrystal();
        registerUnstable();
        registerGlow();
    }

    /**
     * Registers the blaze chain profile.
     */
    private static void registerBlaze() {
        ChainProfile.register(GooTypes.BLAZE, new ChainProfile(
                BLAZE_FUSE_TICKS,
                BLAZE_MAX_STACKS,
                ChainFootprint::tunnelDepth
        ));
    }

    /**
     * Registers the rock chain profile.
     */
    private static void registerRock() {
        ChainProfile.register(GooTypes.ROCK, new ChainProfile(
                ROCK_FUSE_TICKS,
                ROCK_MAX_STACKS,
                ChainFootprint::tunnelDepth
        ));
    }

    /**
     * Registers the crystal chain profile.
     */
    private static void registerCrystal() {
        ChainProfile.register(GooTypes.CRYSTAL, new ChainProfile(
                CRYSTAL_FUSE_TICKS,
                CRYSTAL_MAX_STACKS,
                stacks -> 1
        ));
    }

    /**
     * Registers the unstable chain profile.
     */
    private static void registerUnstable() {
        ChainProfile.register(GooTypes.UNSTABLE, new ChainProfile(
                UNSTABLE_FUSE_TICKS,
                UNSTABLE_MAX_STACKS,
                stacks -> 1
        ));
    }

    /**
     * Registers the frost chain profile.
     */
    private static void registerFrost() {
        ChainProfile.register(GooTypes.FROST, new ChainProfile(
                FROST_FUSE_TICKS,
                FROST_MAX_STACKS,
                AbilityMath::computeFreezeRadius
        ));
    }

    /**
     * Registers the metal chain profile.
     */
    private static void registerMetal() {
        ChainProfile.register(GooTypes.METAL, new ChainProfile(
                METAL_FUSE_TICKS,
                METAL_MAX_STACKS,
                stacks -> 1
        ));
    }

    /**
     * Registers the glow chain profile.
     */
    private static void registerGlow() {
        ChainProfile.register(GooTypes.GLOW, new ChainProfile(
                GLOW_FUSE_TICKS,
                GLOW_MAX_STACKS,
                stacks -> 1
        ));
    }

    /**
     * Registers the nether chain profile.
     */
    private static void registerNether() {
        ChainProfile.register(GooTypes.NETHER, new ChainProfile(
                NETHER_FUSE_TICKS,
                NETHER_MAX_STACKS,
                AbilityMath::computeNetherRadius
        ));
    }


    /**
     * The fuse, stack and range parameters of a goo type's chain markers.
     *
     * @param fuseTicks    how long the fuse window lasts
     * @param maxStacks    maximum stack count (additional blobs during fuse)
     * @param rangeFormula computes range/depth from stack count
     */
    public record ChainProfile(
            int fuseTicks,
            int maxStacks,
            IntUnaryOperator rangeFormula
    ) {
        private static final Map<ResourceKey<GooTypeDefinition>, ChainProfile> PROFILES = new HashMap<>();

        /**
         * Registers a chain profile for a goo type. Called during mod init.
         *
         * @param type    the goo type
         * @param profile the chain profile definition
         */
        public static void register(ResourceKey<GooTypeDefinition> type, ChainProfile profile) {
            PROFILES.put(type, profile);
        }

        /**
         * Looks up the profile for a goo type. Returns null if unregistered.
         *
         * @param type the goo type
         * @return the chain profile, or null if none registered
         */
        public static ChainProfile forType(ResourceKey<GooTypeDefinition> type) {
            return PROFILES.get(type);
        }

        /**
         * Returns true if the given goo type has a registered chain profile.
         *
         * @param type the goo type to check
         * @return true if a chain profile exists
         */
        public static boolean isChainType(ResourceKey<GooTypeDefinition> type) {
            return PROFILES.containsKey(type);
        }
    }
}
