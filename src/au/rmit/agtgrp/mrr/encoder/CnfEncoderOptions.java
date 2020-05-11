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

package au.rmit.agtgrp.mrr.encoder;

import au.rmit.agtgrp.mrr.fol.symbol.Variable;
import au.rmit.agtgrp.mrr.pddl.Operator;
import au.rmit.agtgrp.mrr.utils.collections.graph.DirectedGraph;

public class CnfEncoderOptions {

	public enum AsymmetryOpt {
		NONE, OP_TYPES, STRUCT, OPVAL, INIT_STATE
	}

	public enum AcyclicityOpt {
		ATOM, BINARY
	}
	
	public enum EqualityOpt {
		NONE, ATOM, IDX
	}
	
	public enum CausalStructureOpt {
		REORDER, DEORDER, CUSTOM
	}
	
	public enum OutputOpt {
		TOTAL_ORDER, PARTIAL_ORDER
	}
	
	public final boolean verbose;	
	public final AsymmetryOpt asymmOpt;
	public final AcyclicityOpt acyclOpt;
	public final EqualityOpt equalityOpt;
	public final CausalStructureOpt csOpt;
	public final OutputOpt outOpt;
	public final boolean optimise;
	public final long optTime;
	public final boolean optTransClosure;
	public final DirectedGraph<Operator<Variable>> customPrecGraph;
	
	public CnfEncoderOptions(AsymmetryOpt asymm, EqualityOpt equality, AcyclicityOpt acycl, CausalStructureOpt csOpt, 
			OutputOpt outOpt, boolean optimise, long optTime, boolean optTransClosure, boolean verbose, DirectedGraph<Operator<Variable>> customPrecGraph) {
		this.verbose = verbose;
		this.asymmOpt = asymm;
		this.acyclOpt = acycl;
		this.csOpt = csOpt;
		this.outOpt = outOpt;
		this.optimise = optimise;
		this.optTime = optTime;
		this.optTransClosure = optTransClosure;	
		this.equalityOpt = equality;
		this.customPrecGraph = customPrecGraph;
	}
		
	public CnfEncoderOptions(AsymmetryOpt asymm, AcyclicityOpt acyc, boolean optimise, long optTime, boolean verbose) {
		this(asymm, EqualityOpt.IDX, acyc, CausalStructureOpt.REORDER, OutputOpt.PARTIAL_ORDER, optimise, optTime, true, verbose, null);
	}
	
	public CnfEncoderOptions(AsymmetryOpt asymm, AcyclicityOpt acyc, boolean verbose) {
		this(asymm, EqualityOpt.IDX, acyc, CausalStructureOpt.REORDER, OutputOpt.PARTIAL_ORDER, false, 0, true, verbose, null);
	}
	
}
