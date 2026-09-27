package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.ChrysmTier;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * The crystallizer's phase arithmetic over plain values, so a unit test reaches
 * it without a registry (decision crystallizer-emits-chrysm): what the holding
 * takes, when it is ready, and what forming a chrysm spends. Crystal goo is the
 * catalyst; the crystallizer holds one other type, the type it crystallizes, or
 * crystallizes crystal itself when crystal is all it holds.
 */
public final class CrystallizerPhases {

    /** n, operator ruling: one chrysm forms in 200 ticks. */
    public static final int CHRYSM_TICKS = 200;

    /** The catalyst each phase spends. */
    public static final ResourceKey<GooTypeDefinition> CATALYST = GooTypes.CRYSTAL;

    private CrystallizerPhases() {
    }

    /**
     * m, operator ruling: a phase spends the cube root of its tier's volume in crystal goo.
     *
     * @param tier the tier the phase forms
     * @return the crystal goo spent, in mB
     */
    public static int crystalCost(ChrysmTier tier) {
        return (int) Math.round(Math.cbrt(tier.volume()));
    }

    /**
     * The type the holding crystallizes: the one type besides crystal, or crystal
     * when crystal is all it holds.
     *
     * @param held the goo the crystallizer holds
     * @return the forming type, or null when it holds nothing
     */
    public static @Nullable ResourceKey<GooTypeDefinition> formingType(GooContents held) {
        ResourceKey<GooTypeDefinition> crystal = null;
        for (var entry : held.getAll().entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            if (!CATALYST.equals(entry.getKey())) {
                return entry.getKey();
            }
            crystal = entry.getKey();
        }
        return crystal;
    }

    /**
     * The most of one type the holding takes while it forms a chrysm: the chrysm's
     * volume of the forming type, the phase's crystal beside it, and nothing of a
     * third type or once a chrysm stands formed inside.
     *
     * @param held    the goo the crystallizer holds
     * @param formed  the tier held formed inside, or null
     * @param offered the type offered
     * @return the most of that type the holding takes, in mB
     */
    public static int capacityFor(GooContents held, @Nullable ChrysmTier formed,
                                  ResourceKey<GooTypeDefinition> offered) {
        if (formed != null) {
            return 0;
        }
        ResourceKey<GooTypeDefinition> forming = formingType(held);
        boolean crystalOnly = forming == null || CATALYST.equals(forming);
        return CATALYST.equals(offered) ? crystalCapacity(crystalOnly) : typeCapacity(crystalOnly, forming, offered);
    }

    private static int crystalCapacity(boolean crystalOnly) {
        int cost = crystalCost(ChrysmTier.CHRYSM);
        return crystalOnly ? Math.toIntExact(ChrysmTier.CHRYSM.volume()) + cost : cost;
    }

    private static int typeCapacity(boolean crystalOnly, @Nullable ResourceKey<GooTypeDefinition> forming,
                                    ResourceKey<GooTypeDefinition> offered) {
        return crystalOnly || offered.equals(forming) ? Math.toIntExact(ChrysmTier.CHRYSM.volume()) : 0;
    }

    /**
     * Whether the holding carries a chrysm's volume of its forming type and the phase's crystal.
     *
     * @param held the goo the crystallizer holds
     * @return true when a chrysm can form
     */
    public static boolean readyToForm(GooContents held) {
        return spentToForm(held) != null;
    }

    /**
     * The goo forming one chrysm spends from the holding.
     *
     * @param held the goo the crystallizer holds
     * @return the goo spent, or null when the holding is not ready
     */
    public static @Nullable GooContents spentToForm(GooContents held) {
        ResourceKey<GooTypeDefinition> forming = formingType(held);
        if (forming == null) {
            return null;
        }
        int chrysmVolume = Math.toIntExact(ChrysmTier.CHRYSM.volume());
        GooContents spent = GooContents.EMPTY.withAdded(forming, chrysmVolume)
                .withAdded(CATALYST, crystalCost(ChrysmTier.CHRYSM));
        for (var entry : spent.getAll().entrySet()) {
            if (held.getVolume(entry.getKey()) < entry.getValue()) {
                return null;
            }
        }
        return spent;
    }
}
