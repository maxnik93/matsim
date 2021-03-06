/* *********************************************************************** *
 * project: org.matsim.*
 * EqualDistanceCondition.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2010 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package org.matsim.contrib.socnetgen.sna.graph.spatial.generators;


import org.matsim.contrib.common.gis.CartesianDistanceCalculator;
import org.matsim.contrib.common.gis.DistanceCalculator;
import org.matsim.contrib.common.stats.Discretizer;
import org.matsim.contrib.common.stats.LinearDiscretizer;
import org.matsim.contrib.socnetgen.sna.graph.matrix.AdjacencyMatrix;
import org.matsim.contrib.socnetgen.sna.graph.mcmc.EdgeSwitchCondition;
import org.matsim.contrib.socnetgen.sna.graph.spatial.SpatialVertex;

/**
 * @author illenberger
 *
 */
public class EqualDistanceCondition implements EdgeSwitchCondition {

	private DistanceCalculator calculator = new CartesianDistanceCalculator();
	
	private Discretizer discretizer = new LinearDiscretizer(2000);
	
	private final double threshold = 0;
	
	@Override
	public boolean allowSwitch(AdjacencyMatrix<?> y, int i, int j, int u, int v) {
		SpatialVertex vi = (SpatialVertex) y.getVertex(i);
		SpatialVertex vj = (SpatialVertex) y.getVertex(j);
		SpatialVertex vu = (SpatialVertex) y.getVertex(u);
		SpatialVertex vv = (SpatialVertex) y.getVertex(v);
		
		double d_ij = discretizer.discretize(calculator.distance(vi.getPoint(), vj.getPoint()));
		double d_uv = discretizer.discretize(calculator.distance(vu.getPoint(), vv.getPoint()));
		
		if(Math.abs(d_ij - d_uv) <= threshold)
			return true;
		else
			return false;
	}

}
