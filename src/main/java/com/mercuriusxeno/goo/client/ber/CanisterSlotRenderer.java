package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.canister.CanisterGeometry;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws canisters standing in a host's slots: bodies, gasket caps and fluid.
 * Every host places the same canister, so each names only its
 * {@link CanisterGeometry} and its slot centers (decision
 * machine-base-owns-the-lifecycle). A cap is choral on an end whose canister
 * carries a gasket there, copper otherwise, each texture batched in one draw call.
 */
public final class CanisterSlotRenderer {

    /** Copper endcap texture: an end with no gasket. */
    private static final Identifier COPPER_GASKET =
            Identifier.fromNamespaceAndPath("goo", "textures/block/gasket.png");

    /** Choral gasket texture: an end carrying a gasket. */
    private static final Identifier CHORAL_GASKET =
            Identifier.fromNamespaceAndPath("goo", "textures/block/choral_gasket.png");

    /** Side strip of either gasket texture: columns 4 to 8 of 16, row 0 to 1 of 16. */
    public static final float CAP_SIDE_U_START = 4f / 16f;
    /** End column of the gasket side strip. */
    public static final float CAP_SIDE_U_END = 8f / 16f;
    /** End row of the gasket side strip. */
    public static final float CAP_SIDE_V_END = 1f / 16f;

    private CanisterSlotRenderer() {
    }

    /**
     * The canister's square footprint at a center, with Y zeroed.
     *
     * @param cx the center X, in block units
     * @param cz the center Z, in block units
     * @return the footprint
     */
    public static CuboidBounds footprint(float cx, float cz) {
        float hw = CanisterGeometry.HALF_WIDTH;
        return new CuboidBounds(cx - hw, cx + hw, cz - hw, cz + hw, 0, 0);
    }

    /**
     * The fluid column's geometry inside a canister body.
     *
     * @param geometry the host's canister geometry
     * @return the slot fluid geometry
     */
    public static SlotFluidGeometry.SlotGeometry fluidGeometry(CanisterGeometry geometry) {
        return new SlotFluidGeometry.SlotGeometry(CanisterGeometry.HALF_WIDTH,
                geometry.bodyBottom(), geometry.bodyTop(), CanisterGeometry.FLUID_INSET);
    }

    /**
     * Emits one gasket cap box at an end.
     *
     * @param ctx       the render context
     * @param footprint the canister's footprint
     * @param yBottom   the cap's bottom edge
     * @param yTop      the cap's top edge
     */
    public static void capBox(RenderContext ctx, CuboidBounds footprint, float yBottom, float yTop) {
        ctx.gasketBox(footprint.withY(yBottom, yTop), CAP_SIDE_U_START, CAP_SIDE_U_END, CAP_SIDE_V_END);
    }

    /**
     * Submits every present canister body in one draw call, at the host's world light.
     *
     * @param poseStack     the pose stack
     * @param nodeCollector the node collector
     * @param light         the packed light
     * @param geometry      the host's canister geometry
     * @param slots         the slot snapshots
     * @param centers       the slot XZ centers, parallel to {@code slots}
     */
    public static void submitBodies(PoseStack poseStack, SubmitNodeCollector nodeCollector, int light,
                                    CanisterGeometry geometry, SlotState[] slots, float[][] centers) {
        List<CuboidBounds> bodies = new ArrayList<>();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i].present) {
                bodies.add(footprint(centers[i][0], centers[i][1])
                        .withY(geometry.bodyBottom(), geometry.bodyTop()));
            }
        }
        if (!bodies.isEmpty()) {
            GooSubmitter.submitSidedBodies(poseStack, nodeCollector, light, bodies);
        }
    }

    /**
     * Submits the caps of every present canister: copper caps in one draw call,
     * choral caps in another, a texture with no cap to draw submitting nothing.
     *
     * @param poseStack     the pose stack
     * @param nodeCollector the node collector
     * @param light         the packed light
     * @param geometry      the host's canister geometry
     * @param slots         the slot snapshots
     * @param centers       the slot XZ centers, parallel to {@code slots}
     */
    public static void submitCaps(PoseStack poseStack, SubmitNodeCollector nodeCollector, int light,
                                  CanisterGeometry geometry, SlotState[] slots, float[][] centers) {
        submitCapsOf(poseStack, nodeCollector, light, geometry, slots, centers, false);
        submitCapsOf(poseStack, nodeCollector, light, geometry, slots, centers, true);
    }

    /**
     * Submits the fluid of every filled slot in one draw call.
     *
     * @param poseStack       the pose stack
     * @param nodeCollector   the node collector
     * @param geometry        the host's canister geometry
     * @param slots           the slot snapshots
     * @param centers         the slot XZ centers, parallel to {@code slots}
     * @param supportsVanilla true if a slot's vanilla fluid renders when it holds no goo
     */
    public static void submitFluids(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                    CanisterGeometry geometry, SlotState[] slots, float[][] centers,
                                    boolean supportsVanilla) {
        SlottedFluidContainer.submitFluids(poseStack, nodeCollector, slots,
                fluidGeometry(geometry), centers, supportsVanilla);
    }

    private static void submitCapsOf(PoseStack poseStack, SubmitNodeCollector nodeCollector, int light,
                                     CanisterGeometry geometry, SlotState[] slots, float[][] centers,
                                     boolean choral) {
        if (!anyCap(slots, choral)) {
            return;
        }
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entitySolid(choral ? CHORAL_GASKET : COPPER_GASKET),
                (pose, c) -> renderCaps(new RenderContext(pose, c, light), geometry, slots, centers, choral));
    }

    private static boolean anyCap(SlotState[] slots, boolean choral) {
        for (SlotState slot : slots) {
            if (slot.present && (slot.topGasketPresent == choral || slot.bottomGasketPresent == choral)) {
                return true;
            }
        }
        return false;
    }

    private static void renderCaps(RenderContext ctx, CanisterGeometry geometry, SlotState[] slots,
                                   float[][] centers, boolean choral) {
        for (int i = 0; i < slots.length; i++) {
            SlotState slot = slots[i];
            if (!slot.present) {
                continue;
            }
            CuboidBounds base = footprint(centers[i][0], centers[i][1]);
            if (slot.topGasketPresent == choral) {
                capBox(ctx, base, geometry.bodyTop(), geometry.gasketTop());
            }
            if (slot.bottomGasketPresent == choral) {
                capBox(ctx, base, geometry.gasketBottom(), geometry.bodyBottom());
            }
        }
    }
}
