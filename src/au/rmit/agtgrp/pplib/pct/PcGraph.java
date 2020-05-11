package au.rmit.agtgrp.pplib.pct;

import java.util.HashSet;
import java.util.Set;

import au.rmit.agtgrp.pplib.utils.collections.graph.DirectedBipartiteGraph;

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
