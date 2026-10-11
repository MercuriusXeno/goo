package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.TypeBand;
import com.mercuriusxeno.goo.client.TypeBands;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Per-slot render snapshot for slot-arrayed machines (Hub, Canister) and
 * single-slot machines (Tap, Reactor). Mutable; populated by the BER's
 * extractRenderState path each frame and consumed by submit. Each
 * RenderState instance owns its slot states for the life of the BE.
 *
 * <p>Some fields apply only to certain machines:
 * <ul>
 *   <li>{@code fluid} - vanilla fluids in canister slots; Hub leaves
 *       this {@link Fluids#EMPTY}.</li>
 *   <li>{@code compression} - canister Compression enchantment level; consumed by
 *       Tap/Reactor renderers, ignored by Hub/Canister.</li>
 *   <li>{@code topGasketPresent} / {@code bottomGasketPresent} - choral
 *       gasket caps; populated by Hub and Canister.</li>
 *   <li>Stream fields - active gasket flow visualization; populated by
 *       Hub and Canister when goo or vanilla fluid is flowing in.</li>
 * </ul>
 */
public final class SlotState {
    /** True when a canister occupies this slot. */
    public boolean present;
    /** Goo type in the slot, or null if empty / vanilla fluid. */
    public @Nullable ResourceKey<GooTypeDefinition> type;
    /** Vanilla fluid for non-goo contents; {@link Fluids#EMPTY} when empty or has goo. */
    public Fluid fluid = Fluids.EMPTY;
    /** Fill fraction in [0, 1]. */
    public float fill;
    /**
     * One band per goo type the canister holds, in the vat's band order; more
     * than one draws the mingled surface (decision noise-mingled-type-textures).
     */
    public List<TypeBand> bands = List.of();
    /** Compression level of the inserted canister (Tap / Reactor only). */
    public int compression;
    /** True if a top gasket cap is installed. */
    public boolean topGasketPresent;
    /** True if a bottom gasket cap is installed. */
    public boolean bottomGasketPresent;
    /** Active stream goo type, or null if no stream. */
    public @Nullable ResourceKey<GooTypeDefinition> streamType;
    /** Active stream vanilla fluid; {@link Fluids#EMPTY} if none or stream is goo. */
    public Fluid streamFluid = Fluids.EMPTY;
    /** Stream rate in mB/tick. */
    public float streamRate;

    /**
     * Fills the slot's fluid fields from every fluid its canister holds: the
     * dominant goo type or vanilla fluid, the total fill against capacity, and
     * a band per goo type through the vat's band builder
     * (decisions canisters-hold-more-than-one-goo-type and noise-mingled-type-textures).
     *
     * @param content  the canister's content
     * @param capacity the canister's capacity in mB
     */
    public void showContent(CanisterFluidContent content, int capacity) {
        type = content.dominantGooType();
        fluid = type == null && !content.isEmpty() ? content.dominantFluid() : Fluids.EMPTY;
        fill = capacity <= 0 ? 0f : Math.clamp((float) content.totalVolume() / capacity, 0f, 1f);
        Map<ResourceKey<GooTypeDefinition>, Integer> goo = content.gooVolumes();
        bands = goo.isEmpty() ? List.of() : TypeBands.over(new GooContents(goo));
    }

    /**
     * @return true when the slot holds more than one goo type, so its surface mingles
     */
    public boolean mingles() {
        return bands.size() > 1;
    }
}
