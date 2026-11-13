package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.throwing.CoatRay;
import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.Optional;

/**
 * What a spray drip's strike lays: a stream program's block step that
 * builds its coat where each drip's arc strikes, rather than where rays
 * reach, so the coat lands where and when the drip lands, the hold's drips
 * and those still in flight after it alike.
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 */
public interface DripLanding {

    /**
     * The cell a drip's strike feeds.
     *
     * @param host   the caster's host
     * @param strike where the drip struck
     * @return the cell, empty where the strike lays nothing
     */
    Optional<BlockPos> cellFed(ChannelHost host, CoatRay.BlockStrike strike);

    /**
     * Feeds a cell one strike's share of its coat, laying the coat, paid
     * for, once full.
     *
     * @param host the caster's host
     * @param cell the cell a strike fed
     */
    void feed(ChannelHost host, BlockPos cell);

    /**
     * The step a program's drips land through, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the landing step, empty where the program's drips lay nothing
     */
    static Optional<DripLanding> in(List<Step> program) {
        return program.stream().filter(DripLanding.class::isInstance).map(DripLanding.class::cast).findFirst();
    }
}
