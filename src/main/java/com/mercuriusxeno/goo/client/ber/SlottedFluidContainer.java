package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.BandedSurfaceSubmitter;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.TypeBand;
import com.mercuriusxeno.goo.client.model.FluidFaceEmitter;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Submits the per-slot fluid surface pass shared by Canister and Hub
 * block-entity renderers. The orchestration is identical between them
 * (predicate scan -> single fluid submission -> per-slot top + side
 * faces); the differences are pure data: slot count, XZ centers,
 * Y-range geometry, and whether vanilla fluids are supported.
 *
 * <p>Vat does not use this runner because vat fluid is a single stack-
 * column body, not a slot-array.
 */
public final class SlottedFluidContainer {

    private SlottedFluidContainer() {
    }

    /**
     * Submits one fluid submission covering every filled slot.
     * No-op when no slot has fluid.
     *
     * @param poseStack       the pose stack
     * @param nodeCollector   the render node collector
     * @param slots           per-slot snapshots
     * @param geom            shared slot geometry (HW + body Y range + inset)
     * @param centers         per-slot XZ block-coord centers (parallel to {@code slots})
     * @param supportsVanilla true if {@link SlotState#fluid} should be rendered when no goo type is set
     */
    public static void submitFluids(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                    SlotState[] slots,
                                    SlotFluidGeometry.SlotGeometry geom, float[][] centers,
                                    boolean supportsVanilla) {
        if (hasAnySingleFluid(slots, supportsVanilla)) {
            GooSubmitter.submitFluid(poseStack, nodeCollector,
                    ctx -> renderAllFluids(ctx, slots, geom, centers, supportsVanilla));
        }
        submitMingledFluids(GooSubmitter.bandedSurfaces(poseStack, nodeCollector), slots, geom, centers);
    }

    private static void renderAllFluids(RenderContext ctx, SlotState[] slots,
                                        SlotFluidGeometry.SlotGeometry geom, float[][] centers,
                                        boolean supportsVanilla) {
        for (int i = 0; i < slots.length; i++) {
            SlotState s = slots[i];
            if (!drawsOneSurface(s, supportsVanilla)) {
                continue;
            }
            if (s.type != null) {
                renderGooSurface(ctx, centers[i], s.type, s.fill, geom);
            } else {
                renderVanillaSurface(ctx, centers[i], s.fluid, s.fill, geom);
            }
        }
    }

    private static boolean hasAnySingleFluid(SlotState[] slots, boolean supportsVanilla) {
        for (SlotState s : slots) {
            if (drawsOneSurface(s, supportsVanilla)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a slot draws one whole surface: filled, holding one goo type, or a
     * vanilla fluid where the host shows one.
     *
     * @param s               the slot snapshot
     * @param supportsVanilla true if the host shows a slot's vanilla fluid
     * @return true when the slot draws in the single-surface pass
     */
    private static boolean drawsOneSurface(SlotState s, boolean supportsVanilla) {
        if (s.fill <= 0f || s.mingles()) {
            return false;
        }
        return s.type != null || supportsVanilla && s.fluid != Fluids.EMPTY;
    }

    /**
     * Submits each slot holding several goo types once per type band through the
     * vat's banded submitter, every layer lifted outward of the one below, so the
     * column mingles the types by noise (decision noise-mingled-type-textures).
     *
     * @param submitter submits one band's surface on that band type's sprite
     * @param slots     per-slot snapshots
     * @param geom      shared slot geometry
     * @param centers   per-slot XZ block-coord centers (parallel to {@code slots})
     */
    static void submitMingledFluids(BandedSurfaceSubmitter submitter, SlotState[] slots,
                                    SlotFluidGeometry.SlotGeometry geom, float[][] centers) {
        for (int i = 0; i < slots.length; i++) {
            SlotState s = slots[i];
            if (s.fill > 0f && s.mingles()) {
                CuboidBounds b = SlotFluidGeometry.computeBounds(geom, centers[i][0], centers[i][1], s.fill);
                for (TypeBand band : s.bands) {
                    submitter.submit(band, (ctx, sprite) ->
                            FluidFaceEmitter.emitFluidFaces(ctx, b.liftedOutward(band.lift()), sprite, ctx.color()));
                }
            }
        }
    }

    private static void renderGooSurface(RenderContext ctx, float[] center,
                                         ResourceKey<GooTypeDefinition> type, float fill,
                                         SlotFluidGeometry.SlotGeometry geom) {
        CuboidBounds b = SlotFluidGeometry.computeBounds(geom, center[0], center[1], fill);
        TextureAtlasSprite sprite = GooSubmitter.fluidSprite(type);
        int tint = GooSubmitter.fluidTint(type);
        SlotFluidGeometry.renderFluidTop(ctx, b, sprite, tint);
        SlotFluidGeometry.renderFluidSides(ctx, b, sprite, fill, geom, tint);
    }

    private static void renderVanillaSurface(RenderContext ctx, float[] center, Fluid fluid,
                                             float fill, SlotFluidGeometry.SlotGeometry geom) {
        CuboidBounds b = SlotFluidGeometry.computeBounds(geom, center[0], center[1], fill);
        TextureAtlasSprite sprite = GooSubmitter.fluidSprite(fluid);
        int tint = GooSubmitter.fluidTint(fluid);
        SlotFluidGeometry.renderFluidTop(ctx, b, sprite, tint);
        SlotFluidGeometry.renderFluidSides(ctx, b, sprite, fill, geom, tint);
    }
}
