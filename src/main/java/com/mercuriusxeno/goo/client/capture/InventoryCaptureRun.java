package com.mercuriusxeno.goo.client.capture;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.command.GooInventoryReport;
import com.mercuriusxeno.goo.workbench.CaptureManifest;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The capture run gradle's captureInventory starts: a dev client handed goo.captureInventory=imageFolder captures,
 * once its models are baked and its player stands in a world, whose registries bind each item's components, every
 * item the inventory json beside the folder lists (every registered item where none stands) that the manifest lacks,
 * then exits. A client handed no folder never runs it.
 * decision images-are-slot-captures-taken-on-detection
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class InventoryCaptureRun {
    private static final String FOLDER_PROPERTY = "goo.captureInventory";
    private static final String LOG_CAPTURING = "Capturing {} inventory items into {}";
    private static final String LOG_FAILED = "The inventory capture failed: {}";
    private static boolean started;

    private InventoryCaptureRun() {
    }

    /**
     * Starts the capture on the first frame with the loading overlay gone and the player in the world.
     *
     * @param event the frame event
     */
    @SubscribeEvent
    public static void onFrameEnd(RenderFrameEvent.Post event) {
        String folder = System.getProperty(FOLDER_PROPERTY);
        if (started || folder == null || folder.isBlank() || !inWorld(Minecraft.getInstance())) {
            return;
        }
        started = true;
        captureThenStop(Path.of(folder), Minecraft.getInstance());
    }

    private static boolean inWorld(Minecraft minecraft) {
        return minecraft.getOverlay() == null && minecraft.player != null;
    }

    private static void captureThenStop(Path imageFolder, Minecraft minecraft) {
        try {
            List<String> missing = CaptureManifest.read(CaptureManifest.fileBeside(imageFolder))
                    .missingFrom(listedItems(imageFolder));
            Goo.LOGGER.info(LOG_CAPTURING, missing.size(), imageFolder);
            SlotCaptureQueue.enqueue(imageFolder, missing).whenComplete((unused, failure) -> {
                if (failure != null) {
                    Goo.LOGGER.error(LOG_FAILED, failure.getMessage());
                }
                minecraft.stop();
            });
        } catch (IOException e) {
            Goo.LOGGER.error(LOG_FAILED, e.getMessage());
            minecraft.stop();
        }
    }

    private static List<String> listedItems(Path imageFolder) throws IOException {
        Path inventory = imageFolder.resolveSibling(GooInventoryReport.FILE_INVENTORY);
        if (Files.isRegularFile(inventory)) {
            return CaptureManifest.itemIdsOf(Files.readString(inventory, StandardCharsets.UTF_8));
        }
        return BuiltInRegistries.ITEM.keySet().stream().sorted().map(Identifier::toString).toList();
    }
}
