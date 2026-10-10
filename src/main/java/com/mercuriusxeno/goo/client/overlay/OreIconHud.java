package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * Draws each gem ore vein Glitter revealed as its ore's item icon on the
 * gui, over the point in the world the vein's centroid stands at, seen
 * through walls: the world's render records the camera each frame, and the
 * gui layer projects every icon through it.
 * decision glitter-sphere-icons-gem-ore-groups
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class OreIconHud {

    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "ore_icons");
    /** Half an item icon's side in gui pixels. */
    private static final float HALF_ICON = 8f;

    private static @Nullable Matrix4f clip;
    private static Vec3 cameraAt = Vec3.ZERO;

    private OreIconHud() {
    }

    /**
     * Registers the icons as a gui layer under the crosshair.
     *
     * @param event the layer registration event
     */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.CROSSHAIR, LAYER_ID, OreIconHud::render);
    }

    /**
     * Records the frame's camera, which the gui layer projects the icons through.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        CameraRenderState camera = event.getLevelRenderState().cameraRenderState;
        clip = new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix);
        cameraAt = camera.pos;
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Matrix4f frame = clip;
        if (mc.level == null || frame == null || mc.options.hideGui) {
            return;
        }
        double now = mc.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
        for (OreIcons.Icon icon : OreIcons.CLIENT.iconsAt(now)) {
            OreIcons.toGui(frame, icon.centroid().subtract(cameraAt), graphics.guiWidth(), graphics.guiHeight())
                    .ifPresent(at -> paint(graphics, icon, at));
        }
    }

    private static void paint(GuiGraphicsExtractor graphics, OreIcons.Icon icon, float[] at) {
        ItemStack stack = new ItemStack(BuiltInRegistries.BLOCK.getValue(icon.ore()).asItem());
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(at[0], at[1]);
        pose.scale(icon.scale(), icon.scale());
        graphics.fakeItem(stack, (int) -HALF_ICON, (int) -HALF_ICON);
        pose.popMatrix();
    }
}
