package com.mercuriusxeno.goo.client.capture;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.workbench.CaptureManifest;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.jspecify.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Captures queued items as their slots draw them, a few each frame once the game's models are baked, writing each
 * to namespace/path.png in its image folder and naming the batch in the manifest beside the folder when its last
 * image lands. Any thread queues; the frame's end on the render thread draws.
 * decision images-are-slot-captures-taken-on-detection
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SlotCaptureQueue {
    private static final int CAPTURES_PER_FRAME = 32;
    private static final String PNG = ".png";
    private static final String LOG_UNKNOWN = "No item {} to capture";
    private static final String LOG_DRAW_FAIL = "Failed to capture {}: {}";
    private static final String LOG_WRITE_FAIL = "Failed to write the capture of {}: {}";
    private static final Queue<Batch> PENDING = new ConcurrentLinkedQueue<>();
    private static @Nullable SlotCapturer capturer;

    private SlotCaptureQueue() {
    }

    /**
     * @param imageFolder the folder the captures land in
     * @param itemIds     the ids of the items to capture
     * @return done once every capture is written and the manifest names it
     */
    public static CompletableFuture<Void> enqueue(Path imageFolder, List<String> itemIds) {
        if (itemIds.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Batch batch = new Batch(imageFolder, new ArrayDeque<>(itemIds));
        PENDING.add(batch);
        return batch.done;
    }

    /**
     * Draws the next few captures of the oldest batch, after the frame's world and GUI and before it reaches the
     * screen.
     *
     * @param event the frame event
     */
    @SubscribeEvent
    public static void onFrameEnd(RenderFrameEvent.Post event) {
        Batch batch = PENDING.peek();
        if (batch == null || Minecraft.getInstance().getOverlay() != null) {
            return;
        }
        drawFrameOf(batch);
        if (batch.toDraw.isEmpty()) {
            PENDING.poll();
            batch.finishWhenWritten();
        }
    }

    private static void drawFrameOf(Batch batch) {
        SlotCapturer slots = capturer();
        for (int drawn = 0; drawn < CAPTURES_PER_FRAME && !batch.toDraw.isEmpty(); drawn++) {
            batch.drawNext(slots);
        }
    }

    // Only the render thread reads it, so the lazy creation needs no lock.
    private static SlotCapturer capturer() {
        if (capturer == null) {
            capturer = new SlotCapturer();
        }
        return capturer;
    }

    /**
     * One caller's items: those left to draw, the read backs still in flight, and those written.
     */
    private static final class Batch {
        private final Path imageFolder;
        private final Deque<String> toDraw;
        private final Map<String, Instant> written = new TreeMap<>();
        private final CompletableFuture<Void> done = new CompletableFuture<>();
        private int awaiting;

        Batch(Path imageFolder, Deque<String> toDraw) {
            this.imageFolder = imageFolder;
            this.toDraw = toDraw;
        }

        void drawNext(SlotCapturer slots) {
            String id = toDraw.poll();
            Optional<Item> item = Optional.ofNullable(Identifier.tryParse(id)).flatMap(BuiltInRegistries.ITEM::getOptional);
            if (item.isEmpty()) {
                Goo.LOGGER.warn(LOG_UNKNOWN, id);
                return;
            }
            awaiting++;
            try {
                slots.capture(item.get().getDefaultInstance(), image -> write(id, image));
            } catch (RuntimeException e) {
                // One item's renderer failing leaves it uncaptured and out of the manifest; the rest still capture.
                awaiting--;
                Goo.LOGGER.error(LOG_DRAW_FAIL, id, e.toString());
            }
        }

        private void write(String id, NativeImage image) {
            Identifier item = Identifier.parse(id);
            Path png = imageFolder.resolve(item.getNamespace()).resolve(item.getPath() + PNG);
            try (image) {
                Files.createDirectories(png.getParent());
                image.writeToFile(png);
                written.put(id, Instant.now());
            } catch (IOException e) {
                Goo.LOGGER.error(LOG_WRITE_FAIL, id, e.getMessage());
            }
            awaiting--;
            finishWhenWritten();
        }

        void finishWhenWritten() {
            if (!toDraw.isEmpty() || awaiting > 0 || done.isDone()) {
                return;
            }
            try {
                Path file = CaptureManifest.fileBeside(imageFolder);
                CaptureManifest manifest = CaptureManifest.read(file);
                for (Map.Entry<String, Instant> capture : written.entrySet()) {
                    manifest = manifest.withCapture(capture.getKey(), capture.getValue());
                }
                manifest.write(file);
                done.complete(null);
            } catch (IOException e) {
                done.completeExceptionally(e);
            }
        }
    }
}
