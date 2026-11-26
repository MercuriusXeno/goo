package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * A whole block's model part for the headless holds: one quad on each side,
 * none unculled, each side's list held as a model holds it.
 *
 * @param side the quads every side holds
 */
record CubePart(List<BakedQuad> side) implements BlockStateModelPart {

    @Override
    public List<BakedQuad> getQuads(@Nullable Direction direction) {
        return direction == null ? List.of() : side;
    }

    @Override
    @Deprecated
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public Material.Baked particleMaterial() {
        throw new UnsupportedOperationException("a counted part draws no particle");
    }

    @Override
    public int materialFlags() {
        return 0;
    }
}
