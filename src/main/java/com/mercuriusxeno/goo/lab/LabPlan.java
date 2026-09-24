package com.mercuriusxeno.goo.lab;

import java.util.List;

/**
 * Everything one lab build sets, in the order {@link LabBuilder} sets it,
 * with the plots, pens and range that name which ground serves what.
 *
 * @param placements every block the build sets, relative to the lab origin
 * @param plots      one plot per {@link LabMachine}
 * @param pens       the mob pens
 * @param range      the target range
 * @param spawns     every mob the build spawns
 * @param bounds     the box holding every placement, plot, pen and spawn
 */
public record LabPlan(List<LabPlacement> placements, List<LabPlot> plots, List<LabPen> pens,
                      LabRange range, List<LabSpawn> spawns, LabBox bounds) {

    /**
     * Copies the lists so a plan stays fixed once made.
     *
     * @param placements every block the build sets
     * @param plots      one plot per machine
     * @param pens       the mob pens
     * @param range      the target range
     * @param spawns     every mob the build spawns
     * @param bounds     the box holding the plan
     */
    public LabPlan {
        placements = List.copyOf(placements);
        plots = List.copyOf(plots);
        pens = List.copyOf(pens);
        spawns = List.copyOf(spawns);
    }
}
