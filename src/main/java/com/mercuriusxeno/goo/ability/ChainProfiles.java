package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.world.*;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/**
 * Central registry for chain effect profiles. Each goo type with a chain
 * effect gets a profile that defines fuse duration, max stacks, range
 * formula, and a factory for the post-fuse {@link ChainBehavior}.
 * Profiles are registered during mod init and looked up at runtime by
 * the chain marker block entity.
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
    private static final Identifier GLOW_CRYSTAL_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "glow_crystal");
    private static final Identifier ROCK_TUNNEL_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "rock_tunnel");
    private static final Identifier BLAZE_TUNNEL_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "blaze_tunnel");
    private static final Identifier FROST_SPHERE_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "frost_sphere");
    private static final Identifier METAL_SPIKES_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "metal_spikes");

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
     * Registers the blaze chain profile. Legacy non-ability path
     * (no abilityId selected) runs the {@code blaze_tunnel} ability's
     * program.
     */
    private static void registerBlaze() {
        ChainProfile.register(GooTypes.BLAZE, new ChainProfile(
                BLAZE_FUSE_TICKS,
                BLAZE_MAX_STACKS,
                ChainFootprint::tunnelDepth,
                () -> abilityBehavior(BLAZE_TUNNEL_ABILITY)
        ));
    }

    /**
     * Registers the rock chain profile. Legacy non-ability path
     * (no abilityId selected) runs the {@code rock_tunnel} ability's
     * program.
     */
    private static void registerRock() {
        ChainProfile.register(GooTypes.ROCK, new ChainProfile(
                ROCK_FUSE_TICKS,
                ROCK_MAX_STACKS,
                ChainFootprint::tunnelDepth,
                () -> abilityBehavior(ROCK_TUNNEL_ABILITY)
        ));
    }

    /**
     * Registers the crystal chain profile.
     */
    private static void registerCrystal() {
        ChainProfile.register(GooTypes.CRYSTAL, new ChainProfile(
                CRYSTAL_FUSE_TICKS,
                CRYSTAL_MAX_STACKS,
                stacks -> 1,
                CrystalBehavior::new
        ));
    }

    /**
     * Registers the unstable chain profile.
     */
    private static void registerUnstable() {
        ChainProfile.register(GooTypes.UNSTABLE, new ChainProfile(
                UNSTABLE_FUSE_TICKS,
                UNSTABLE_MAX_STACKS,
                stacks -> 1,
                UnstableBehavior::new
        ));
    }

    /**
     * Registers the frost chain profile. Legacy non-ability path
     * (no abilityId selected) runs the {@code frost_sphere} ability's
     * program.
     */
    private static void registerFrost() {
        ChainProfile.register(GooTypes.FROST, new ChainProfile(
                FROST_FUSE_TICKS,
                FROST_MAX_STACKS,
                AbilityMath::computeFreezeRadius,
                () -> abilityBehavior(FROST_SPHERE_ABILITY)
        ));
    }

    /**
     * Registers the metal chain profile. Legacy non-ability path
     * (no abilityId selected) runs the {@code metal_spikes} ability's
     * field-effect program.
     */
    private static void registerMetal() {
        ChainProfile.register(GooTypes.METAL, new ChainProfile(
                METAL_FUSE_TICKS,
                METAL_MAX_STACKS,
                stacks -> 1,
                () -> abilityBehavior(METAL_SPIKES_ABILITY)
        ));
    }

    /**
     * Registers the glow chain profile. Legacy non-ability path
     * (no abilityId selected) runs the {@code glow_crystal} ability's
     * own program, so both paths place the same crystal.
     */
    private static void registerGlow() {
        ChainProfile.register(GooTypes.GLOW, new ChainProfile(
                GLOW_FUSE_TICKS,
                GLOW_MAX_STACKS,
                stacks -> 1,
                () -> abilityBehavior(GLOW_CRYSTAL_ABILITY)
        ));
    }

    /**
     * Composes the behavior an ability's JSON declares, for a legacy
     * profile whose post-fuse behavior migrated onto a program.
     *
     * @param ability the ability's id
     * @return the ability's behavior, or null while the registry does not hold it
     */
    private static @Nullable ChainBehavior abilityBehavior(Identifier ability) {
        AbilityDefinition definition = AbilityRegistry.getAbility(ability);
        return definition == null ? null : new DataDrivenChainBehavior(definition);
    }

    /**
     * Registers the nether chain profile.
     */
    private static void registerNether() {
        ChainProfile.register(GooTypes.NETHER, new ChainProfile(
                NETHER_FUSE_TICKS,
                NETHER_MAX_STACKS,
                AbilityMath::computeNetherRadius,
                NetherBehavior::new
        ));
    }


    /**
     * Defines the behavior of a chain effect for a specific goo type.
     * The {@code behaviorFactory} is called when the fuse expires to
     * produce a fresh {@link ChainBehavior} that owns the type-specific
     * post-fuse lifecycle.
     *
     * @param fuseTicks       how long the fuse window lasts
     * @param maxStacks       maximum stack count (additional blobs during fuse)
     * @param rangeFormula    computes range/depth from stack count
     * @param behaviorFactory factory that creates a fresh {@link ChainBehavior}, or answers
     *                        null when the behavior it delegates to is not loaded, which
     *                        removes the marker at fuse expiry
     */
    public record ChainProfile(
            int fuseTicks,
            int maxStacks,
            IntUnaryOperator rangeFormula,
            Supplier<ChainBehavior> behaviorFactory
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
