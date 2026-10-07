package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import java.util.List;

/**
 * The ghost of a goo type's landing visual, drawn at the aim point while right
 * click is held: the landing's own dome at resting size through its own shader,
 * so a later change to the landing look carries into its ghost.
 * held-visual-ghosts-the-landing-in-two-passes
 */
public interface HeldGhostVisual {

    /**
     * @return the goo type whose landing this ghost draws
     */
    ResourceKey<GooTypeDefinition> gooType();

    /**
     * The ghost an ability of this type holds: its dome's radius and its rings.
     * By default the rings travel outward to the dome's radius, the area's size.
     *
     * @param area      the ability's synced area, a sphere
     * @param behaviors the ability's synced program
     * @return the ghost
     */
    default HeldGhost ghost(AbilityArea area, List<Step> behaviors) {
        return HeldGhost.outwardTo((float) area.size());
    }

    /**
     * @return the render type the dome draws through over blocks, depth tested
     */
    RenderType heldType();

    /**
     * @return the render type the dome draws through behind blocks, depth ignored
     */
    RenderType heldThroughBlocksType();

    /**
     * Emits the dome at resting size about the block center, in block-local
     * coordinates as a burnout draws, at a share of the landing's opacity.
     *
     * @param pose       the pose entry
     * @param c          the vertex consumer
     * @param ghost      the ghost being drawn
     * @param face       the face the throw strikes, the landing's placed face
     * @param opacity    the share of the landing's opacity, in [0, 1]
     * @param nowSeconds seconds on the real-time clock, for a shader that animates while held
     */
    void emitHeld(PoseStack.Pose pose, VertexConsumer c, HeldGhost ghost, Direction face, float opacity,
                  double nowSeconds);
}
