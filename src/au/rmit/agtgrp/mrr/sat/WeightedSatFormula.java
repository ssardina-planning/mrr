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

package au.rmit.agtgrp.mrr.sat;

import java.util.List;

import au.rmit.agtgrp.mrr.utils.collections.graph.UndirectedGraph;

public class WeightedSatFormula extends SatFormula {
	
	private static final long serialVersionUID = 1L;
	
	private final int hardClauseWeight;
	private long weightsSum;

	public WeightedSatFormula(List<int[]> clauses, int nProps, int hardClauseWeight) {
		super(clauses, nProps);
		this.hardClauseWeight = hardClauseWeight;
		weightsSum = -1;
	}
	
	public WeightedSatFormula(int hardClauseWeight) {
		this.hardClauseWeight = hardClauseWeight;
		weightsSum = 0;
	}
	
	public int getHardClauseWeight() {
		return hardClauseWeight;
	}
	
	@Override
	public void addClause(List<Integer> clause) {
		addHardClause(clause);
	}

	@Override
	public void addClause(int ... clause) {
		addHardClause(clause);
	}
	
	public void addHardClause(List<Integer> clause) {
		addWeightedClause(hardClauseWeight, clause);
	}

	public void addHardClause(int ... clause) {
		addWeightedClause(hardClauseWeight, clause);
	}

	
	public void addWeightedClause(int weight, List<Integer> clause) {
		int[] combined = new int[clause.size() + 1];
		combined[0] = weight;
		int i = 1;
		for (int p : clause)
			combined[i++] = p;
		
		super.addClause(combined);
		setWeightSum(weight);
	}
	
	public void addWeightedClause(int weight, int ... clause) {
		int[] combined = new int[clause.length + 1];
		combined[0] = weight;
		for (int i = 0; i < clause.length; i++)
			combined[i+1] = clause[i];
		
		super.addClause(combined);
		setWeightSum(weight);
	}
	
	private void setWeightSum(int weight) {
		if (weightsSum == -1) {	
			weightsSum = 0;
			for (int[] clause : clauses) {
				weightsSum+=clause[0];
			}
		}
		
		if (weight <= 0)
			throw new IllegalArgumentException("Weights must > 0");
		if (weightsSum + weight < 0) // wrap around
			throw new IllegalArgumentException("Sum of weights (" + weightsSum +") > " + Long.MAX_VALUE);

		weightsSum+=weight;
	}
	
	@Override
	protected void setHighestPropNumber(int[] clause) {
		for (int i = 1; i < clause.length; i++) {
			int p = Math.abs(clause[i]);
			if (p > nProps)
				nProps = p;
			if (p == 0)
				throw new IllegalArgumentException("Invalid prop: " + Integer.toString(p));
		}
	}
	
	@Override
	protected String formatDescription() {
		return "p wcnf " + nProps + " " + clauses.size() + " " + hardClauseWeight;
	}
	
	@Override
	public UndirectedGraph<Integer> getPrimalGraph() {
		UndirectedGraph<Integer> primalGraph = new UndirectedGraph<Integer>();
		for (int[] clause : clauses) {
			for (int i = 1; i < clause.length; i++) {
				for (int j = i+1; j < clause.length; j++) {
					primalGraph.addEdge(clause[i], clause[j]);
				}
			}
		}
		return primalGraph;
	}
	
	@Override
	public UndirectedGraph<Integer> getIncidenceGraph() {
		UndirectedGraph<Integer> incGraph = new UndirectedGraph<Integer>();
		int cnum = nProps+1;
		for (int[] clause : clauses) {
			for (int i = 1; i < clause.length; i++) {
				incGraph.addEdge(clause[i], cnum);
			}
			cnum++;
		}
		return incGraph;
	}

}
