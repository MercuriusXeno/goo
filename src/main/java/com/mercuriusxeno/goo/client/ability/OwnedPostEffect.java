package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.nio.ByteBuffer;

/**
 * One of Goo's post-effect chains holding the game renderer's single post
 * effect slot, with one custom uniform block it rewrites each frame. The
 * chain yields the slot to any other post effect already standing (a
 * vanilla spectator cam or another chain), and releases only a slot it holds.
 *
 * <p>A chain's custom uniforms are created with {@link GpuBuffer#USAGE_UNIFORM}
 * alone, with no {@code MAP_WRITE} bit, so the first write to each chain
 * instance installs a writable replacement buffer
 * ({@code USAGE_UNIFORM | USAGE_COPY_DST}) and later writes stream into it.
 */
final class OwnedPostEffect {

    private final Identifier chainId;
    private final String uniformBlock;
    private final String bufferLabel;
    /** The chain instance whose uniform buffers were already swapped for writable copies. */
    private @Nullable PostChain managedChain;

    /**
     * @param chainId      the chain under {@code assets/<ns>/post_effect/}
     * @param uniformBlock the custom uniform block the chain declares and this rewrites
     */
    OwnedPostEffect(Identifier chainId, String uniformBlock) {
        this.chainId = chainId;
        this.uniformBlock = uniformBlock;
        this.bufferLabel = chainId.getPath() + "/" + uniformBlock;
    }

    /**
     * Writes the frame's uniform bytes into the chain and takes the slot
     * where it stands free.
     *
     * @param mc    the client instance
     * @param bytes the std140 bytes of the uniform block
     */
    void show(Minecraft mc, ByteBuffer bytes) {
        PostChain chain = mc.getShaderManager().getPostChain(chainId, LevelTargetBundle.MAIN_TARGETS);
        if (chain == null) {
            return;
        }
        writeUniforms(chain, bytes);
        Identifier current = mc.gameRenderer.currentPostEffect();
        if (current == null) {
            mc.gameRenderer.setPostEffect(chainId);
        }
    }

    /**
     * Clears the slot where this chain holds it.
     *
     * @param gameRenderer the active game renderer
     */
    void hide(GameRenderer gameRenderer) {
        if (chainId.equals(gameRenderer.currentPostEffect())) {
            gameRenderer.clearPostEffect();
        }
    }

    private void writeUniforms(PostChain chain, ByteBuffer bytes) {
        if (managedChain != chain) {
            installManagedBuffers(chain, bytes);
            managedChain = chain;
            return;
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        for (PostPass pass : chain.passes) {
            GpuBuffer buffer = pass.customUniforms.get(uniformBlock);
            if (buffer != null) {
                bytes.position(0);
                encoder.writeToBuffer(buffer.slice(), bytes);
            }
        }
    }

    private void installManagedBuffers(PostChain chain, ByteBuffer initialData) {
        int usage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
        for (PostPass pass : chain.passes) {
            GpuBuffer oldBuffer = pass.customUniforms.get(uniformBlock);
            if (oldBuffer == null) {
                continue;
            }
            initialData.position(0);
            GpuBuffer newBuffer = RenderSystem.getDevice().createBuffer(() -> bufferLabel, usage, initialData);
            pass.customUniforms.put(uniformBlock, newBuffer);
            oldBuffer.close();
        }
    }
}
