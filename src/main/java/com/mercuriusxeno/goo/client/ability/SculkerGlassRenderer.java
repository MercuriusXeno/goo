package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.deep.SculkerSwim;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.overlay.FungusXray;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws Sculker's glass field: every block within the field's radius of the
 * local player's eyes draws its own model and texture through the x-ray
 * pipeline Fungal Sight draws through, ghosted at the field's strength for
 * its distance, farthest first so the nearer glass lies over it. The blocks
 * the player's body is inside are within the field and draw with the rest.
 * decision sculker-shows-blocks-translucent-close-up
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SculkerGlassRenderer {

    /** The opacity a block at the player draws at, short of opaque so the glass stays glass. */
    private static final float MOST_OPACITY = 0.7f;
    /** A block this faint is not drawn at all. */
    private static final float FAINTEST = 0.03f;
    private static final int OPAQUE = 255;
    private static final int WHITE_RGB = 0xFFFFFF;

    private SculkerGlassRenderer() {
    }

    /**
     * Draws the glass field while the local player's Sculker stands.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !SculkerSwim.dives(player)) {
            return;
        }
        Vec3 eyes = player.getEyePosition();
        List<BlockPos> field = fieldAround(mc, eyes);
        if (field.isEmpty()) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType glass = GooRenderTypes.fungusXray(mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location());
        VertexConsumer consumer = buffers.getBuffer(glass);
        for (BlockPos pos : field) {
            float strength = SculkerGlass.strengthAt(Vec3.atCenterOf(pos).distanceTo(eyes));
            int color = ARGB.color(Math.round(MOST_OPACITY * strength * OPAQUE), WHITE_RGB);
            FungusXray.drawBlock(mc, poseStack, consumer, camera, pos, FungusXray.instance(color), 1f);
        }
        buffers.endBatch(glass);
    }

    /**
     * The blocks within the field that draw, farthest first.
     *
     * @param mc   the client, its level loaded
     * @param eyes the player's eyes
     * @return the cells holding a block strong enough to show
     */
    private static List<BlockPos> fieldAround(Minecraft mc, Vec3 eyes) {
        int reach = (int) Math.ceil(SculkerGlass.FIELD_RADIUS);
        BlockPos center = BlockPos.containing(eyes);
        List<BlockPos> field = new ArrayList<>();
        for (BlockPos cell : BlockPos.betweenClosed(center.offset(-reach, -reach, -reach), center.offset(reach, reach, reach))) {
            if (!mc.level.getBlockState(cell).isAir()
                    && SculkerGlass.strengthAt(Vec3.atCenterOf(cell).distanceTo(eyes)) > FAINTEST) {
                field.add(cell.immutable());
            }
        }
        field.sort(Comparator.comparingDouble((BlockPos cell) -> Vec3.atCenterOf(cell).distanceToSqr(eyes)).reversed());
        return field;
    }
}
