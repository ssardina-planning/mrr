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

package au.rmit.agtgrp.mrr.auto;

import java.util.Arrays;
import java.util.List;

public class NautyResult {
	
	private static double log2(double d) {
		// log2(12)=log10(12)/log10(2)
		return Math.log(d)/Math.log(2);
	}
	
	public Group generator;
	public List<int[]> orbits;
	public long groupSize;
	public int nVerts;
	
	public NautyResult(Group generator, List<int[]> orbits, long groupSize, int nVerts) {
		this.generator = generator;
		this.orbits = orbits;
		this.groupSize = groupSize;
		this.nVerts = nVerts;
	}	
	
	public double getGraphEntropy() {
		double ent = 0.0;
		for (int[] orbit : orbits) {		
			double nov = ((double) orbit.length)/nVerts;
			ent+=nov*log2(nov);
		}	
		return -ent;
	}
	
	public double getMowshowitzSymmetryIndex() {
		double symm = 0.0;
		for (int[] orbit : orbits)
			symm+=orbit.length * log2(orbit.length);
		
		symm/=nVerts;
		symm+=log2(groupSize);
		return symm;
	}
	
	public double getVertexSymmetryIndex() {
		double max = 0.0;
		for (int[] orbit : orbits) {
			if (orbit.length > max)
				max = orbit.length;
		}
		
		return max/nVerts;
	}
	
	public double getOrbitHomogeneityIndex() {
		double min = Double.MAX_VALUE;
		double max = 0.0;
		for (int[] orbit : orbits) {
			if (orbit.length > max)
				max = orbit.length;
			if (orbit.length < min)
				min = orbit.length;
		}	
		
		return min/max;
	}
	
	public double getOrbitDeviationIndex() {
		double max = 0.0;
		for (int[] orbit : orbits) {
			if (orbit.length > max)
				max = orbit.length;
		}
		
		double sum = 0.0;
		for (int[] orbit : orbits)
			sum+=orbit.length/max;
		
		return sum/orbits.size();
	}
	
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Generator: " + generator.toString() + "\n");
		sb.append("Orbits: ");
		for (int[] orbit : orbits)
			sb.append(Arrays.toString(orbit) + " ");
		sb.append("\n");
		sb.append("Autos size: " + groupSize);
		
		return sb.toString();
	}
}
