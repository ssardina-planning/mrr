/*******************************************************************************
 * MRR - Minimum Reinstantiated Reorder
 *
 * Copyright (C) 2020 
 * Max Waters (max.waters@rmit.edu.au)
 * RMIT University, Melbourne VIC 3000
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/

package au.rmit.agtgrp.mrr.pct;

import java.util.HashSet;
import java.util.Set;

import au.rmit.agtgrp.mrr.utils.collections.graph.DirectedBipartiteGraph;

public class PcGraph extends DirectedBipartiteGraph<Producer, Consumer> {
	
	protected HashSet<PcLink> all = null;
	
	@Override
	public void addEdge(Producer source, Consumer dest) {
		super.addEdge(source, dest);
		all = null;
	}

	@Override
	public void removeEdge(Producer source, Consumer dest) {
		super.removeEdge(source, dest);
		all = null;
	}
	
	public Set<PcLink> getAllEdges() {
		if (all == null) {
			all = new HashSet<PcLink>();
			for (Producer source : linksFrom.keySet()) {
				for (Consumer dest : linksFrom.get(source))
					all.add(new PcLink(source, dest).intern());
			}
		}
		return all;
	}

	@Override
	public void clear() {
		super.clear();
		all.clear();
	}

}
