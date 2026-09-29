package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;

/**
 * The four fixed chrysm tiers and the goo volume each holds, the one place
 * those volumes live (decision chrysm-tiers-in-32x-steps): round numbers about
 * 32 times the tier below, named for growth, and no tier past materia, since
 * one more step breaks the int cap (decision chrysm-tiers-fixed-and-stackable).
 */
public enum ChrysmTier {
    CHRYSM("chrysm", 32_000L),
    BUDDING_CHRYSM("budding_chrysm", 1_000_000L),
    FLOWERING_CHRYSM("flowering_chrysm", 32_000_000L),
    MATERIA("materia", 1_000_000_000L);

    /** The crystal spent crystallizing a chrysm is a tenth of its goo. */
    private static final long CRYSTAL_DIVISOR = 10L;
    private static final String ITEM_KEY_PREFIX = "item." + Goo.MODID + ".";

    private final String registryPath;
    private final long volume;

    ChrysmTier(String registryPath, long volume) {
        this.registryPath = registryPath;
        this.volume = volume;
    }

    /**
     * @return the tier's item id path under the goo namespace
     */
    public String registryPath() {
        return registryPath;
    }

    /**
     * @return the goo volume one item of this tier holds, in mB
     */
    public long volume() {
        return volume;
    }

    /**
     * The goo one item of this tier is worth and melts back into: its volume of its
     * type and the crystal goo spent to crystallize it, a tenth of that volume, so
     * storing goo as chrysm loses no crystal (decision chrysm-melts-back-to-its-goo).
     *
     * @param type the goo type the chrysm carries
     * @return the tier volume of that type beside its crystal
     */
    public GooContents contentsOf(ResourceKey<GooTypeDefinition> type) {
        return GooContents.EMPTY.withAdded(type, Math.toIntExact(volume))
                .withAdded(GooTypes.CRYSTAL, Math.toIntExact(volume / CRYSTAL_DIVISOR));
    }

    /**
     * @return the item name's translation key, taking the type name
     */
    public String translationKey() {
        return ITEM_KEY_PREFIX + registryPath;
    }
}
