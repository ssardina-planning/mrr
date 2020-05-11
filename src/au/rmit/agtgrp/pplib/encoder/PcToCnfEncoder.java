package au.rmit.agtgrp.pplib.encoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import au.rmit.agtgrp.pplib.auto.Permutation;
import au.rmit.agtgrp.pplib.encoder.CnfEncoderOptions.AcyclicityOpt;
import au.rmit.agtgrp.pplib.encoder.CnfEncoderOptions.EqualityOpt;
import au.rmit.agtgrp.pplib.encoder.CnfEncoderOptions.OutputOpt;
import au.rmit.agtgrp.pplib.encoder.PcPlanAutomorphisms.SymmetryType;
import au.rmit.agtgrp.pplib.fol.function.Constant;
import au.rmit.agtgrp.pplib.fol.predicate.Literal;
import au.rmit.agtgrp.pplib.fol.symbol.Type;
import au.rmit.agtgrp.pplib.fol.symbol.Variable;
import au.rmit.agtgrp.pplib.fol.utils.Comparators;
import au.rmit.agtgrp.pplib.pct.AbstractPct;
import au.rmit.agtgrp.pplib.pct.CausalStructure;
import au.rmit.agtgrp.pplib.pct.Consumer;
import au.rmit.agtgrp.pplib.pct.PcLink;
import au.rmit.agtgrp.pplib.pct.PoclPlan;
import au.rmit.agtgrp.pplib.pct.Producer;
import au.rmit.agtgrp.pplib.pct.Threat;
import au.rmit.agtgrp.pplib.pct.ThreatMap;
import au.rmit.agtgrp.pplib.pddl.Operator;
import au.rmit.agtgrp.pplib.sat.SatFormula;
import au.rmit.agtgrp.pplib.utils.FormattingUtils;
import au.rmit.agtgrp.pplib.utils.collections.Pair;

public class PcToCnfEncoder {

	private static Comparator<Operator<?>> OPERATOR_COMPARATOR = new Comparator<Operator<?>>(){
		@Override
		public int compare(Operator<?> o1, Operator<?> o2) {
			return o1.getName().compareTo(o2.getName());
		}
	};

	protected CnfEncoderOptions options;

	protected long time;
	protected PoclPlan plan;

	protected PropositionMap propMap;

	protected CausalStructure causalStruct;
	protected ThreatMap threatMap;

	protected SatFormula satFormula;

	protected int opEncodingBits;
	protected Map<Type, List<Constant>> constantsByType;
	protected Map<Type, List<Variable>> variablesByType;
	protected Map<String, List<Operator<Variable>>> operatorsByType;
	protected Map<Constant, Variable> constantVars;

	protected int nSymmetryClauses;
	protected int nSymmetryProps;

	public PcToCnfEncoder(CnfEncoderOptions options) {
		this.options = options;
	}

	public SatFormula encodeConstraints(PoclPlan plan) {
		long startTime = System.currentTimeMillis();

		if (options.verbose)
			System.out.println("Initialising encoder");
		this.plan = plan;

		opEncodingBits = (int) (Math.log(plan.getPlanSteps().size()) / Math.log(2)) + 1;
		propMap = new PropositionMap(opEncodingBits);

		satFormula = initSatFormula();

		constantsByType = getConstantsByType();
		variablesByType = getVariablesByType();
		operatorsByType = getOperatorsByType();
		constantVars = getConstantVars();

		causalStruct = plan.getConstraints();		
		if (options.verbose)
			System.out.println("Filtering causal links");
		causalStruct = filterCausalLinks();

		if (options.verbose)
			System.out.println("Building threats");
		threatMap = ThreatMap.getThreatMap(plan.getPlanSteps());

		encode();

		if (options.verbose)
			System.out.println("CNF size: " + satFormula.getNumProps() + " props, " + satFormula.getNumClauses() + " clauses");

		this.time = System.currentTimeMillis() - startTime;

		return satFormula;
	}

	protected void encode() {

		if (options.verbose)
			System.out.println("Building causal link and threat constraints");
		buildProducerConsumerConstraints();

		if (options.verbose)
			System.out.println("Building init/goal ordering constraints");
		buildInitGoalOrderingConstraints();

		if (!causalStruct.isGround()) {
			if (options.verbose)
				System.out.println("Building variable domain constraints");
			buildVariableDomainConstraints();
		}

		if (options.outOpt.equals(OutputOpt.TOTAL_ORDER)) {
			if (options.verbose)
				System.out.println("Building total order constraints");
			buildTotalOrderConstraints();
		}

		if (options.verbose)
			System.out.println("Closing precedence relation");
		buildPrecClosureConstraints();

		if (options.verbose)
			System.out.println("Building symmetry breaking constraints");
		buildSymmetryBreakingConstraints();

		if (!causalStruct.isGround()) {
			if (options.verbose)
				System.out.println("Closing equality relation");	
			buildEqualityClosureConstraints();
		}
	}

	protected SatFormula initSatFormula() {
		return new SatFormula();
	}

	public PropositionMap getPropositionMap() {
		return propMap;
	}

	public long getEncodingTime() {
		return time;
	}

	public int getNumSymmetryClauses() {
		return nSymmetryClauses;
	}

	public int getNumSymmetryProps() {
		return nSymmetryProps;
	}


	protected Map<Type, List<Constant>> getConstantsByType() {
		Map<Type, List<Constant>> cmap = new HashMap<Type, List<Constant>>();

		for (Type t : plan.getProblem().getDomain().getTypes())
			cmap.put(t, new ArrayList<Constant>());

		for (Constant c : plan.getProblem().getObjects()) {
			Type t = c.getType();
			while (!t.equals(Type.ANYTHING_TYPE)) {
				cmap.get(t).add(c);
				t = t.getImmediateSupertype();
			}
		}

		for (Constant c : plan.getProblem().getDomain().getConstants()) {
			Type t = c.getType();
			while (!t.equals(Type.ANYTHING_TYPE)) {
				cmap.get(t).add(c);
				t = t.getImmediateSupertype();
			}
		}

		return cmap;
	}

	protected Map<Type, List<Variable>> getVariablesByType() {
		Map<Type, List<Variable>> cmap = new HashMap<Type, List<Variable>>();

		for (Type t : plan.getProblem().getDomain().getTypes())
			cmap.put(t, new ArrayList<Variable>());

		for (Variable c : plan.getOriginalSub().getVariables()) {
			Type t = c.getType();
			while (!t.equals(Type.ANYTHING_TYPE)) {
				cmap.get(t).add(c);
				t = t.getImmediateSupertype();
			}
		}

		return cmap;
	}

	protected Map<String, List<Operator<Variable>>> getOperatorsByType() {
		Map<String, List<Operator<Variable>>> opsByType = new HashMap<String, List<Operator<Variable>>>();

		for (Operator<Variable> op : plan.getPlanSteps()) {
			String type = getOperatorType(op);
			List<Operator<Variable>> ops = opsByType.get(type);
			if (ops == null) {
				ops = new ArrayList<Operator<Variable>>();
				opsByType.put(type, ops);
			}

			ops.add(op);
		}

		for (String type : opsByType.keySet()) {
			List<Operator<Variable>> ops = opsByType.get(type);
			Collections.sort(ops, OPERATOR_COMPARATOR);  // sort by name
		}
		return opsByType;
	}

	protected Map<Constant, Variable> getConstantVars() {
		Map<Constant, Variable> map = new HashMap<Constant, Variable>();
		for (Variable var : plan.getInitSub().getVariables())
			map.put(plan.getInitSub().apply(var), var);

		return map;
	}

	protected int buildEqualityProp(Variable v1, Variable v2) {
		Integer eqProp = propMap.getEqualityProposition(v1, v2);
		if (eqProp == null) 
			eqProp = propMap.addEqualityProposition(v1, v2);
		return eqProp;
	}

	protected int buildThreatProp(PcLink pcl, Threat threat) {
		Integer tProp = propMap.getThreatProposition(pcl, threat);
		if (tProp == null) {
			tProp = propMap.addThreatProposition(pcl, threat);
		}
		return tProp;
	}

	protected int buildPrecProp(Operator<Variable> op1, Operator<Variable> op2) {
		Integer prop = propMap.getPrecedenceProposition(op1, op2);
		if (prop == null) {
			if (options.acyclOpt == AcyclicityOpt.ATOM)
				propMap.addPrecedenceProposition(op1, op2);	
			else if (options.acyclOpt == AcyclicityOpt.BINARY) {
				for (int k = 1; k <= opEncodingBits; k++)			
					propMap.addPrecedenceProposition(op1, op2, k);	
			}
			return propMap.getPrecedenceProposition(op1, op2);
		}
		return prop;

	}

	protected int getVariableAssignmentProposition(Variable v, Constant c) {
		if (options.equalityOpt.equals(EqualityOpt.IDX)) {
			return propMap.getVariableAssignmentProposition(v, c);
		} else {
			return propMap.getEqualityProposition(v, constantVars.get(c));
		}		
	}


	protected CausalStructure filterCausalLinks() {
		CausalStructure filtered = new CausalStructure(causalStruct.isTotalOrder(), causalStruct.isGround());
		for (PcLink pcLink : causalStruct) {
			if (!isFiltered(pcLink.getProducer().operator, pcLink.getConsumer().operator))
				filtered.addProducerConsumerOption(pcLink);
		}
		return filtered;

		/*		

		switch (options.csOpt) {
		case CUSTOM:
			CausalStructure filtered = new CausalStructure(causalStruct.isTotalOrder(), causalStruct.isGround());
			for (PcLink pcLink : causalStruct) {
				if (pcLink.getProducer().operator.equals(plan.getInitAction()) || 
					pcLink.getConsumer().operator.equals(plan.getGoalAction()) ||
					options.customPrecGraph.containsEdge(pcLink.getProducer().operator, pcLink.getConsumer().operator))

					filtered.addProducerConsumerOption(pcLink);
			}
			return filtered;
		case DEORDER:
			// assume this has been done before
		case REORDER:
			// nothing to do
		default:
			return causalStruct;
		}
		 */
	}


	protected boolean isFiltered(Operator<Variable> prec, Operator<Variable> ante) {
		switch (options.csOpt) {
		case CUSTOM:
			if (prec.equals(plan.getInitAction()) || ante.equals(plan.getGoalAction()))
				return false;
			return !options.customPrecGraph.containsEdge(prec, ante);
		case DEORDER:
			return plan.getPlanSteps().indexOf(prec) > plan.getPlanSteps().indexOf(ante);
		case REORDER:
			return false;
		default:
			return false;
		}
	}


	/* 
	 * PRODUCER CONSUMER CONSTRAINTS
	 */

	protected void buildProducerConsumerConstraints() {

		// at least one producer per consumer
		for (Consumer cons : causalStruct.getAllConsumers()) {
			List<Producer> prods = new ArrayList<Producer>(causalStruct.getProducers(cons)); 
			// at least one prod for each cons
			int[] clause = new int[prods.size()];
			int i = 0;
			for (Producer prod : prods)
				clause[i++] = propMap.addProducerConsumerProposition(prod, cons);

			satFormula.addClause(clause);
		}

		// if pclink then x1 = y1 and x2 = y2 etc, and op1 < op2, and t1 and t2 etc
		for (PcLink pcl : causalStruct) {

			int pclProp = propMap.getProducerConsumerProposition(pcl);

			List<Integer> rtl = new ArrayList<Integer>();

			if (!causalStruct.isGround()) { // x1 = y1 and x2 = y2 ...
				for (int i = 0; i < pcl.getProducer().literal.getAtom().getVariables().size(); i++) {
					int eqProp = buildEqualityProp(pcl.getProducer().literal.getAtom().getVariables().get(i), pcl.getConsumer().literal.getAtom().getVariables().get(i));			
					satFormula.addClause(-pclProp, eqProp);
					rtl.add(-eqProp);
				}
			}

			// op1 < op2
			int precProp = buildPrecProp(pcl.getProducer().operator, pcl.getConsumer().operator);
			satFormula.addClause(-pclProp, precProp);
			rtl.add(-precProp);

			// all threats
			for (Threat threat : causalStruct.isGround() ? threatMap.getGroundThreats(pcl, plan.getOriginalSub()) : threatMap.getNonGroundThreats(pcl)) {				

				int tprop = buildThreatProp(pcl, threat);
				satFormula.addClause(-pclProp, tprop);
				rtl.add(-tprop);

				// build threat: t1 != x1 or t2 != x2 etc, or opt < op1 or op2 < opt	
				List<Integer> clause = new ArrayList<Integer>(); // ltr
				// neg thrt prop
				clause.add(-tprop);

				// x1 != t1 etc
				if (!causalStruct.isGround()) { 
					for (int k = 0; k < pcl.getConsumer().literal.getAtom().getVariables().size(); k++) {
						// threat var is the same as producer var
						if (pcl.getProducer().literal.getAtom().getVariables().get(k).equals(threat.literal.getAtom().getVariables().get(k)))
							continue;
						int eq = buildEqualityProp(pcl.getProducer().literal.getAtom().getVariables().get(k), threat.literal.getAtom().getVariables().get(k));
						clause.add(-eq);
						satFormula.addClause(eq, tprop);
					}
				}

				// t < p so long as t != p
				if (!threat.operator.equals(pcl.getProducer().operator) && 
						!isFiltered(threat.operator, pcl.getProducer().operator)) {
					int tPrecProp = buildPrecProp(threat.operator, pcl.getProducer().operator);
					clause.add(tPrecProp);
					satFormula.addClause(-tPrecProp, tprop);
				}

				// c < t
				if (!isFiltered(pcl.getConsumer().operator, threat.operator)) {
					int tPrecProp =	buildPrecProp(pcl.getConsumer().operator, threat.operator);
					clause.add(tPrecProp);
					satFormula.addClause(-tPrecProp, tprop);
				}

				Literal<Variable> undoing = threat.operator.getUndoing(threat.literal.getNegated());
				// threat can undone by a later postcondition of the same action, if they have the same bindings
				if (!causalStruct.isGround() && undoing != null) {										
					List<Integer> conj = new ArrayList<Integer>(); // x1=y1, ..., xn=yn -> tprop
					for (int k = 0; k < threat.literal.getAtom().getVariables().size(); k++) {
						// threat var is the same as producer var
						if (undoing.getAtom().getVariables().get(k).equals(threat.literal.getAtom().getVariables().get(k)))
							continue;
						int eq = buildEqualityProp(undoing.getAtom().getVariables().get(k), threat.literal.getAtom().getVariables().get(k));
						conj.add(-eq);
						List<Integer> disj = new ArrayList<Integer>(clause);
						disj.add(eq);
						satFormula.addClause(disj);
					}
					conj.add(tprop);
					satFormula.addClause(conj);
				} else {
					satFormula.addClause(clause);
				}

			}

			rtl.add(pclProp);
			satFormula.addClause(rtl);

		}
	}


	protected void buildTotalOrderConstraints() {
		for (Operator<Variable> op1 : plan.getPlanSteps()) {
			for (Operator<Variable> op2 : plan.getPlanSteps()) {
				if (op1.equals(op2))
					continue;

				if (propMap.getPrecedenceProposition(op2, op1) == null)
					buildPrecProp(op2, op1);
				if (propMap.getPrecedenceProposition(op1, op2) == null)
					buildPrecProp(op1, op2);

				int p21 = propMap.getPrecedenceProposition(op2, op1);
				int p12 = propMap.getPrecedenceProposition(op1, op2);

				// at least one must be true
				satFormula.addClause(p21, p12);
			}
		}
	}


	/* 
	 * ACYCLICITY CONSTRAINTS
	 */
	protected void buildPrecClosureConstraints() {
		if (options.acyclOpt == AcyclicityOpt.ATOM) {
			buildAtomAcyclicity();
		}
		else if (options.acyclOpt == AcyclicityOpt.BINARY) {
			buildBinaryAcyclicity();
		}
	}

	protected void buildAtomAcyclicity() {

		// build extra props
		for (Operator<Variable> op1 : plan.getPlanSteps()) {
			for (Operator<Variable> op2 : plan.getPlanSteps()) {
				if (op1.equals(op2) || (propMap.getPrecedenceProposition(op2, op1) == null && options.optTransClosure))
					continue;

				for (Operator<Variable> op3 : plan.getPlanSteps()) {
					if (op3.equals(op2) || op3.equals(op1) || (propMap.getPrecedenceProposition(op1, op3) == null && options.optTransClosure))
						continue;

					buildPrecProp(op2, op3);
				}
			}
		}

		// close
		for (Operator<Variable> op1 : plan.getPlanSteps()) {
			for (Operator<Variable> op2 : plan.getPlanSteps()) {
				if (op1.equals(op2) || propMap.getPrecedenceProposition(op2, op1) == null)
					continue;
				int p21 = propMap.getPrecedenceProposition(op2, op1);

				// at least one must be false
				if (propMap.getPrecedenceProposition(op1, op2) != null) {	
					int p12 = propMap.getPrecedenceProposition(op1, op2);
					satFormula.addClause(-p12, -p21);
				}

				for (Operator<Variable> op3 : plan.getPlanSteps()) {
					if (op3.equals(op2) || op3.equals(op1) || 
							propMap.getPrecedenceProposition(op1, op3) == null || 
							propMap.getPrecedenceProposition(op2, op3) == null)
						continue;

					int p13 = propMap.getPrecedenceProposition(op1, op3);
					int p23 = propMap.getPrecedenceProposition(op2, op3);

					satFormula.addClause(-p21, -p13, p23);
				}
			}
		}
	}

	protected void buildBinaryAcyclicity() {
		// op1 < op2 and op2 < op3 then op1 < op3
		for (int i = 0; i < plan.getPlanSteps().size(); i++) {
			Operator<Variable> op1 = plan.getPlanSteps().get(i);

			for (int j = 0; j < plan.getPlanSteps().size(); j++) {

				Operator<Variable> op2 = plan.getPlanSteps().get(j);
				if (i == j || propMap.getPrecedenceProposition(op1, op2) == null) // not comparable
					continue;		

				// p12 <-> binary comparison
				for (int k = 1; k <= opEncodingBits; k++) {
					int p12k = propMap.getPrecedenceProposition(op1, op2, k);
					if (k == 1) {
						int op1k = propMap.getOperatorIdxEncodingProp(op1, k);
						int op2k = propMap.getOperatorIdxEncodingProp(op2, k);

						// p12k -> binary op1 < binary op2
						satFormula.addClause(-p12k, op2k);
						satFormula.addClause(-p12k, -op1k);

						// binary op1 < binary op2 -> p12k
						satFormula.addClause(-op2k, op1k, p12k);
					}
					else {
						int p12km1 = propMap.getPrecedenceProposition(op1, op2, k-1);
						int op1k = propMap.getOperatorIdxEncodingProp(op1, k);
						int op2k = propMap.getOperatorIdxEncodingProp(op2, k);

						// p12k -> binary op1 < binary op2
						satFormula.addClause(-p12k, op2k, -op1k);
						satFormula.addClause(-p12k, op2k, p12km1);
						satFormula.addClause(-p12k, -op1k, p12km1);

						// binary op1 < binary op2 -> p12k
						satFormula.addClause(-op2k, op1k, p12k);
						satFormula.addClause(-op2k, -p12km1, p12k);
						satFormula.addClause(op1k, -p12km1, p12k);
					}
				}

				if (j > i && propMap.getPrecedenceProposition(op2, op1) != null) {
					// at least one must be false
					int p12 = propMap.getPrecedenceProposition(op1, op2);
					int p21 = propMap.getPrecedenceProposition(op2, op1);
					satFormula.addClause(-p12, -p21);
				}
			}
		}
	}


	/* 
	 * EQUALITY CLOSURE
	 */

	protected void buildEqualityClosureConstraints() {
		if (options.equalityOpt.equals(EqualityOpt.IDX)) {
			buildDomainEqualityClosureConstraints();
		} else if (options.equalityOpt.equals(EqualityOpt.ATOM)) {
			buildAtomEqualityClosureConstraints();
		}
	}

	protected void buildDomainEqualityClosureConstraints() {

		List<Variable> vars = new ArrayList<Variable>(plan.getOriginalSub().getVariables());

		for (int i = 0; i < vars.size(); i++) {
			Variable v1 = vars.get(i);
			List<Constant> v1Domain = null;
			if (plan.getInitSub().getVariables().contains(v1)) {
				v1Domain = new ArrayList<Constant>();
				v1Domain.add(plan.getInitSub().apply(v1));
			}
			else if (plan.getGoalSub().getVariables().contains(v1)) {
				v1Domain = new ArrayList<Constant>();
				v1Domain.add(plan.getGoalSub().apply(v1));
			}
			else
				v1Domain = constantsByType.get(v1.getType());

			for (int j = i + 1; j < vars.size(); j++) {
				Variable v2 = vars.get(j);
				Integer eq12 = propMap.getEqualityProposition(v1, v2);
				if (eq12 != null) { 

					List<Constant> v2Domain = null;
					if (plan.getInitSub().getVariables().contains(v2)) {
						v2Domain = new ArrayList<Constant>();
						v2Domain.add(plan.getInitSub().apply(v2));
					}
					else if (plan.getGoalSub().getVariables().contains(v2)) {
						v2Domain = new ArrayList<Constant>();
						v2Domain.add(plan.getGoalSub().apply(v2));
					}
					else
						v2Domain = constantsByType.get(v2.getType());

					List<Constant> intersection = new ArrayList<Constant>(v1Domain);
					intersection.retainAll(v2Domain);
					// v1 = v2 <-> (v1 = c1 -> v2 = c1)
					for (Constant c : intersection) {
						int[] lr = new int[3];
						lr[0] = -eq12;
						lr[1] = -getVariableAssignmentProposition(v1, c);
						lr[2] = getVariableAssignmentProposition(v2, c);					
						satFormula.addClause(lr);

						int[] rl = new int[3];
						rl[0] = eq12;
						rl[1] = -getVariableAssignmentProposition(v1, c);
						rl[2] = -getVariableAssignmentProposition(v2, c);					
						satFormula.addClause(rl);
					}

					// v1 = v2 -> v1 must be bound to a value in intersection
					if (intersection.size() < v1Domain.size()) {
						int[] clause = new int[intersection.size()+1];
						clause[0] = -eq12;
						int k = 1;
						for (Constant c : intersection) {
							clause[k] = getVariableAssignmentProposition(v1, c);
							k++;
						}
						satFormula.addClause(clause);
					}

					if (intersection.size() < v2Domain.size()) {
						int[] clause = new int[intersection.size()+1];
						clause[0] = -eq12;
						int k = 1;
						for (Constant c : intersection) {
							clause[k] = getVariableAssignmentProposition(v2, c);
							k++;
						}
						satFormula.addClause(clause);
					}
				}
			}
		}
	}

	protected void buildAtomEqualityClosureConstraints() {

		List<Variable> vars = new ArrayList<Variable>(plan.getOriginalSub().getVariables());
		Collections.sort(vars,  Comparators.SYMBOL_COMPARATOR);
		for (int i = 0; i < vars.size(); i++) {
			Variable v1 = vars.get(i);
			for (int j = i+1; j < vars.size(); j++) {
				if (j == i)
					continue;
				Variable v2 = vars.get(j);
				Integer p12 = propMap.getEqualityProposition(v1, v2);
				if (p12 == null) {
					if (options.optTransClosure)
						continue;
					else
						p12 = propMap.addEqualityProposition(v1, v2);
				}

				for (int k = j+1; k < vars.size(); k++) {
					if (k == i || k == j)
						continue;

					Variable v3 = vars.get(k);
					Integer p13 = propMap.getEqualityProposition(v1, v3);

					if (p13 == null) {
						if (options.optTransClosure)
							continue;
						else
							p13 = propMap.addEqualityProposition(v1, v3);
					}

					Integer p23 = propMap.getEqualityProposition(v2, v3);
					if (p23 == null) {
						if (options.optTransClosure)
							continue;
						else
							p23 = propMap.addEqualityProposition(v2, v3);
					}

					satFormula.addClause(-p23, -p13, p12);
					satFormula.addClause(-p12, -p23, p13);
					satFormula.addClause(-p12, -p13, p23);
				}
			}
		}
	}


	/* 
	 * VARIABLE DOMAINS
	 */


	protected void buildVariableDomainConstraints() {
		if (options.equalityOpt.equals(EqualityOpt.IDX)) {
			// add all variable assignment props
			Set<Variable> vars = new HashSet<Variable>(plan.getOriginalSub().getVariables());
			vars.removeAll(plan.getInitSub().getVariables());
			vars.removeAll(plan.getGoalSub().getVariables());

			for (Variable v : vars) {
				for (Constant c : constantsByType.get(v.getType()))
					propMap.addVariableAssignmentProposition(v, c);
			}

			// domain constraints -- exactly one of v = c1 or v = c2 etc
			for (Variable v : vars) {
				// at least one
				List<Constant> domain = constantsByType.get(v.getType());
				int[] alc = new int[domain.size()];
				int c = 0;
				for (Constant d : domain) {
					alc[c] = getVariableAssignmentProposition(v, d);
					c++;
				}
				satFormula.addClause(alc);

				// at most one
				for (int d1 = 0; d1 < domain.size(); d1++) {
					Constant c1 = domain.get(d1);
					for (int d2 = d1+1; d2 < domain.size(); d2++) {
						Constant c2 = domain.get(d2);
						int[] amo = new int[2];
						amo[0] = -getVariableAssignmentProposition(v, c1);
						amo[1] = -getVariableAssignmentProposition(v, c2);

						satFormula.addClause(amo);
					}
				}
			}

			// vars in init 
			for (Variable v : plan.getInitAction().getVariables()) {
				Constant c = plan.getOriginalSub().apply(v);
				int p = propMap.addVariableAssignmentProposition(v, c);
				satFormula.addClause(p);
			}

			// vars in goal
			for (Variable v : plan.getGoalAction().getVariables()) {
				Constant c = plan.getOriginalSub().apply(v);
				int p = propMap.addVariableAssignmentProposition(v, c);
				satFormula.addClause(p);
			}

		} else if (options.equalityOpt.equals(EqualityOpt.ATOM)) {
			// add all variable assignment props
			Set<Variable> vars = new HashSet<Variable>(plan.getOriginalSub().getVariables());
			vars.removeAll(plan.getInitSub().getVariables());
			vars.removeAll(plan.getGoalSub().getVariables());

			for (Variable v : vars) {
				for (Constant c : constantsByType.get(v.getType()))
					if (propMap.getEqualityProposition(v, constantVars.get(c)) == null)
						propMap.addEqualityProposition(v, constantVars.get(c));
			}

			// domain constraints -- exactly one of v = c1 or v = c2 etc
			for (Variable v : vars) {
				// at least one
				List<Constant> domain = constantsByType.get(v.getType());
				int[] alc = new int[domain.size()];
				int c = 0;
				for (Constant d : domain) {
					alc[c] = getVariableAssignmentProposition(v, d);
					c++;
				}
				satFormula.addClause(alc);

				// at most one
				for (int d1 = 0; d1 < domain.size(); d1++) {
					Constant c1 = domain.get(d1);
					for (int d2 = d1+1; d2 < domain.size(); d2++) {
						Constant c2 = domain.get(d2);
						int[] amo = new int[2];
						amo[0] = -getVariableAssignmentProposition(v, c1);
						amo[1] = -getVariableAssignmentProposition(v, c2);

						satFormula.addClause(amo);
					}
				}
			}

			// vars in init -- all different
			List<Variable> initVars = new ArrayList<Variable>(plan.getInitAction().getVariables());
			Collections.sort(initVars, Comparators.SYMBOL_COMPARATOR);
			for (int i = 0; i < initVars.size(); i++) {
				Variable v1 = initVars.get(i);
				for (int j = i+1; j < initVars.size(); j++) {
					Variable v2 = initVars.get(j);
					if (!v1.equals(v2)) {
						Integer p = propMap.getEqualityProposition(v1, v2);
						if (p == null)		
							p = propMap.addEqualityProposition(v1, v2);
						satFormula.addClause(-p);
					}
				}
			}

			// vars in goal -- must be bound to var in init state
			for (Variable v : plan.getGoalAction().getVariables()) {
				Constant c = plan.getOriginalSub().apply(v);
				Integer p = propMap.getEqualityProposition(v, constantVars.get(c));
				if (p == null)
					p = propMap.addEqualityProposition(v, constantVars.get(c));
				satFormula.addClause(p);
			}
		}
	}

	protected void buildInitGoalOrderingConstraints() {

		if (options.acyclOpt == AcyclicityOpt.ATOM) {
			// init goal prec props
			for (int i = 1; i < plan.getPlanSteps().size()-1; i++) {
				buildPrecProp(plan.getInitAction(), plan.getPlanSteps().get(i));
				buildPrecProp(plan.getPlanSteps().get(i), plan.getGoalAction());	
			}


			// all ops precede goal and are after init
			for (int i = 1; i < plan.getPlanSteps().size()-1; i++) {
				satFormula.addClause(propMap.getPrecedenceProposition(plan.getInitAction(), plan.getPlanSteps().get(i)));
				satFormula.addClause(propMap.getPrecedenceProposition(plan.getPlanSteps().get(i), plan.getGoalAction()));	
			}
		}
		else if (options.acyclOpt == AcyclicityOpt.BINARY) {
			// operator index encoding props
			for (Operator<Variable> op : plan.getPlanSteps()) {
				for (int k = 1; k <= opEncodingBits; k++)
					propMap.addOperatorIdxEncodingProp(op, k);
			}

			// init goal prec props
			for (int i = 1; i < plan.getPlanSteps().size()-1; i++) {
				buildPrecProp(plan.getInitAction(), plan.getPlanSteps().get(i));
				buildPrecProp(plan.getPlanSteps().get(i), plan.getGoalAction());	
			}

			//init must be index 0
			for (int k = 1; k <= opEncodingBits; k++) {
				satFormula.addClause(-propMap.getOperatorIdxEncodingProp(plan.getInitAction(), k));
			}

			// index of goal is plan length-1
			int n = plan.getPlanSteps().size()-1;
			for (int k = 1; k <= opEncodingBits; k++, n/=2) {
				if (n % 2 == 0)
					satFormula.addClause(-propMap.getOperatorIdxEncodingProp(plan.getGoalAction(), k));
				else
					satFormula.addClause(propMap.getOperatorIdxEncodingProp(plan.getGoalAction(), k));
			}

			// all ops precede goal
			for (int i = 1; i < plan.getPlanSteps().size()-1; i++) {
				satFormula.addClause(propMap.getPrecedenceProposition(plan.getInitAction(), plan.getPlanSteps().get(i)));
				satFormula.addClause(propMap.getPrecedenceProposition(plan.getPlanSteps().get(i), plan.getGoalAction()));	
			}
		}
	}



	/* 
	 * SYMMETRY BREAKING CONSTRAINTS
	 */
	protected void buildSymmetryBreakingConstraints() {

		int nClauses = satFormula.getNumClauses();
		int nProps = satFormula.getNumProps();

		switch (options.asymmOpt) {
		case NONE:
			break;
		case OPVAL:		
			breakOperatorValueSymmetries();
			break;
		case OP_TYPES: 
			breakOperatorTypeSymmetries();
			break;
		case STRUCT: // init state, goal and operator pre/post
			breakStructuralSymmetries(true);
			break;
		case INIT_STATE: // init state and goal only
			breakStructuralSymmetries(false);
			break;
		default:
			throw new IllegalArgumentException("Unsupported symmetry breaking arg: " + options.asymmOpt);
		}

		this.nSymmetryClauses = satFormula.getNumClauses() - nClauses;
		this.nSymmetryProps= satFormula.getNumProps() - nProps;
		if (options.verbose) {
			System.out.println("Added " + nSymmetryProps + " symmetry breaking propositions");
			System.out.println("Added " + nSymmetryClauses + " symmetry breaking constraints");
		}
	}


	protected void breakStructuralSymmetries(boolean opSymms) {

		Comparator<AbstractPct> comp = new Comparator<AbstractPct>() {
			@Override
			public int compare(AbstractPct o1, AbstractPct o2) {
				int c = OPERATOR_COMPARATOR.compare(o1.operator, o2.operator);
				if (c == 0)
					c = Integer.compare(o1.operator.getPreconditions().indexOf(o1.literal), o2.operator.getPreconditions().indexOf(o2.literal));
				if (c == 0)
					c = Integer.compare(o1.operator.getPostconditions().indexOf(o1.literal), o2.operator.getPostconditions().indexOf(o2.literal));			
				return c;
			}	
		};

		List<Producer> prodOrder = new ArrayList<Producer>(plan.getConstraints().getAllProducers());
		List<Consumer> consOrder = new ArrayList<Consumer>(plan.getConstraints().getAllConsumers());
		Collections.sort(prodOrder, comp);
		Collections.sort(consOrder, comp);

		// tree map -- keys are sorted
		SortedMap<Producer, Integer> prodInd = new TreeMap<Producer, Integer>(comp);
		SortedMap<Consumer, Integer> consInd = new TreeMap<Consumer, Integer>(comp);

		for (int i = 0; i < prodOrder.size(); i++)
			prodInd.put(prodOrder.get(i), i);
		for (int i = 0; i < consOrder.size(); i++)
			consInd.put(consOrder.get(i), i);


		PcPlanAutomorphisms autos = PcPlanAutomorphisms.getPdgAutomorphisms(plan, options.verbose);
		for (Permutation perm : autos.getGroup().getPermutations()) {
			Map<Producer, Producer> prodPerms = new HashMap<Producer, Producer>();
			Map<Consumer, Consumer> consPerms = new HashMap<Consumer, Consumer>();

			for (int i = 0; i < perm.getDomainSize(); i++) {				
				if (i != perm.apply(i)) { 
					if (autos.getVertexProdMap().containsKey(i)) {
						Producer prod = autos.getVertexProdMap().get(i);
						Producer img = autos.getVertexProdMap().get(perm.apply(i));				
						prodPerms.put(prod, img);

					} else if (autos.getVertexConsMap().containsKey(i)) {
						Consumer cons = autos.getVertexConsMap().get(i);
						Consumer img = autos.getVertexConsMap().get(perm.apply(i));				
						consPerms.put(cons, img);						
					}
				}
			}

			if (!prodPerms.isEmpty() || !consPerms.isEmpty())
				encodeStructuralLeader(prodPerms, consPerms, prodInd, consInd);
		}

		// if deordering, do not do this
		if (opSymms) {
			for (String type : operatorsByType.keySet()) {
				List<Operator<Variable>> ops = operatorsByType.get(type);
				for (int i = 0; i < ops.size()-1; i++) {
					Map<Producer, Producer> prodPerms = new HashMap<Producer, Producer>();
					Map<Consumer, Consumer> consPerms = new HashMap<Consumer, Consumer>();

					Operator<Variable> op1 = ops.get(i);
					Operator<Variable> op2 = ops.get(i+1); // next
					for (int j = 0; j < op1.getPostconditions().size(); j++) {
						System.out.println(op1.formatParameters() + "<->" + op2.formatParameters());
						Producer prod = new Producer(op1, op1.getPostconditions().get(j));
						Producer img = new Producer(op2, op2.getPostconditions().get(j));
						prodPerms.put(prod, img);
						prodPerms.put(img, prod);
					}

					for (int j = 0; j < op1.getPreconditions().size(); j++) {
						Consumer cons = new Consumer(op1, op1.getPreconditions().get(j));
						Consumer img = new Consumer(op2, op2.getPreconditions().get(j));
						consPerms.put(cons, img);
						consPerms.put(img, cons);
					}

					if (!prodPerms.isEmpty() || !consPerms.isEmpty())
						encodeStructuralLeader(prodPerms, consPerms, prodInd, consInd);
				}
			}
		}
	}

	protected void encodeStructuralLeader(Map<Producer, Producer> prodPerms, Map<Consumer, Consumer> consPerms,  SortedMap<Producer, Integer> prodInd, SortedMap<Consumer, Integer> consInd) {
		if (options.verbose) {
			System.out.println("Encoding causal structure lex-leader:");			
			for (Producer prod : prodPerms.keySet())
				System.out.println(prod + " -> " + prodPerms.get(prod));
			for (Consumer cons : consPerms.keySet())
				System.out.println(cons + " -> " + consPerms.get(cons));
		}

		List<Integer> arr = new ArrayList<Integer>();
		List<Integer> img = new ArrayList<Integer>();

		for (Producer prod : prodInd.keySet()) {
			if (prodPerms.containsKey(prod)) {
				Producer prodImage = prodPerms.get(prod);
				if (prodInd.get(prod) < prodInd.get(prodImage)) { 
					for (Consumer cons : consInd.keySet()) {
						Consumer consImage = consPerms.containsKey(cons) ? consPerms.get(cons) : cons;
						if (plan.getConstraints().getConsumers(prod).contains(cons)) {
							arr.add(propMap.getProducerConsumerProposition(prod, cons));
							img.add(propMap.getProducerConsumerProposition(prodImage, consImage));
						}
					}	
				}
			} else {
				for (Consumer cons : consInd.keySet()) {
					if (consPerms.containsKey(cons)) {
						Consumer consImage = consPerms.get(cons);
						if (consInd.get(cons) < consInd.get(consImage)) {
							if (plan.getConstraints().getConsumers(prod).contains(cons)) {
								arr.add(propMap.getProducerConsumerProposition(prod, cons));
								img.add(propMap.getProducerConsumerProposition(prod, consImage));
							}
						}
					}
				}
			}
		}

		if (arr.isEmpty()) {
			if (options.verbose)
				System.out.println("All causal links filtered");
			return;
		}

		List<Integer> aux = new ArrayList<Integer>();
		for (int i = 0; i < arr.size(); i++) // one too many!
			aux.add(propMap.addEncodedObject("cslex-aux-"+i));

		/*
		satFormula.addClause(aux.get(0));
		for (int i = 0; i < arr.size(); i++) {
			int auxi = aux.get(i);
			satFormula.addClause(-auxi, -arr.get(i), img.get(i));
			if (i < arr.size()-1) {
				int auxip1 = aux.get(i+1);
				satFormula.addClause(auxip1, -auxi, -arr.get(i));
				satFormula.addClause(auxip1, -auxi, img.get(i));

				satFormula.addClause(-auxip1, auxi);
				satFormula.addClause(-auxip1, arr.get(i), -img.get(i) );
			}
		}
		 */

		for (int i = 0; i < arr.size(); i++) {
			if (i == 0) {
				satFormula.addClause(-arr.get(0), img.get(0));
			} else {
				int auxi = aux.get(i);
				satFormula.addClause(-auxi, -arr.get(i), img.get(i));
			}

			if (i < arr.size()-1) {
				if (i == 0) {
					int auxip1 = aux.get(i+1);
					satFormula.addClause(auxip1, -arr.get(i));
					satFormula.addClause(auxip1, img.get(i));

					//satFormula.addClause(-auxip1, auxi);
					satFormula.addClause(-auxip1, arr.get(i), -img.get(i));
				} else {
					int auxi = aux.get(i);
					int auxip1 = aux.get(i+1);
					satFormula.addClause(auxip1, -auxi, -arr.get(i));
					satFormula.addClause(auxip1, -auxi, img.get(i));

					satFormula.addClause(-auxip1, auxi);
					satFormula.addClause(-auxip1, arr.get(i), -img.get(i));
				}
			}
		}


		/*
		for (int i = 0; i < arr.size(); i++) {
			int xi = aux.get(i);

			if (i == 0) {
				// x_0 <-> m_0 = n_0

				// ltr
				// [x_0 -> (m_0 -> n_0)] & [x_0 -> (n_0 -> m_0)]
				satFormula.addClause(-xi, -arr.get(0), img.get(0));
				satFormula.addClause(-xi, -img.get(0), arr.get(0));

				//rtl
				// (m_0 -> n_0) & (n_0 -> m_0) -> x_0, i.e., [ (m_0 & n_0) | (-m_0 & -n_0) ] -> x_0 
				satFormula.addClause(-arr.get(0), -img.get(0), xi);
				satFormula.addClause(arr.get(0), img.get(0), xi);


				// m_0 >= n_0, i.e., n_0 -> m_0
				satFormula.addClause(-img.get(0), arr.get(0));


			} else {
				int xim1 = aux.get(i-1);
				// aux prop
				// x_i <-> (x_i-1 & (m_i = n_i)

				// ltr
				// [x_i -> x_i-1] & [x_i -> (m_i = n_i)]
				satFormula.addClause(-xi, xim1);
				satFormula.addClause(-xi, -arr.get(i), img.get(i));
				satFormula.addClause(-xi, -img.get(i), arr.get(i));

				//rtl
				// [x_i-1 & (m_i = n_i)] -> x_i, i.e., [ (x_i-1 & m_i & n_i) | (x_i-1 & -m_i & -n_i)] -> x_i
				satFormula.addClause(-xim1, -arr.get(i), -img.get(i), xi);
				satFormula.addClause(-xim1, arr.get(i), img.get(i), xi);

				// x_i-1 -> m_i >= n_i, i.e, x_i-1 -> (n_i -> m_i)
				satFormula.addClause(-xim1, -img.get(i), arr.get(i));
			}

		}
		 */

	}

	protected void breakOperatorValueSymmetries() {
		PcPlanAutomorphisms autos = PcPlanAutomorphisms.getPdgAutomorphisms(plan, options.verbose);
		for (Permutation perm : autos.getGroup().getPermutations()) {	

			switch (getSymmetryType(perm, autos.getVertexConstantMap(), autos.getVertexOpMap(), autos.getVertexVariableMap())) {
			case OBJECT:
				Map<Constant, Constant> constPerm = new HashMap<Constant, Constant>();
				for (int i = 0; i < perm.getDomainSize(); i++) {				
					if (i != perm.apply(i) && autos.getVertexConstantMap().containsKey(i)) {						
						constPerm.put(autos.getVertexConstantMap().get(i), 
								autos.getVertexConstantMap().get(perm.apply(i)));
					}
				}

				if (options.verbose) 
					System.out.println("Encoding value lex-leader: " + constPerm);
				encodeValueLexLeader(constPerm, autos.getVariableVertexMap(), autos.getConstantVertexMap());
				break;

			case OPERATOR:
				for (int i = 0; i < perm.getDomainSize(); i++) {				
					if (i != perm.apply(i) && autos.getVertexOpMap().containsKey(i)) {		
						if (options.verbose)
							System.out.println("Encoding operator lex-leader: " + autos.getVertexOpMap().get(i).formatParameters() + " <-> " + autos.getVertexOpMap().get(perm.apply(i)).formatParameters());
						encodeOperatorLexLeader(autos.getVertexOpMap().get(i), autos.getVertexOpMap().get(perm.apply(i)), autos.getVariableVertexMap(), autos.getConstantVertexMap());
						break;
					}
				}		
				break;
			case STRUCTURAL:
				break;
			}
		}
	}

	protected SymmetryType getSymmetryType(Permutation perm, Map<Integer, Constant> constVertMap, Map<Integer, Operator<Variable>> opVertMap, Map<Integer, Variable> varVertMap) {
		boolean isObPerm = false;
		List<Operator<Variable>> ops = new ArrayList<Operator<Variable>>();
		List<Variable> vars = new ArrayList<Variable>();
		for (int i = 0; i < perm.getDomainSize(); i++) {
			if (i != perm.apply(i)) {
				if (constVertMap.containsKey(i)) 
					isObPerm = true;

				if (opVertMap.containsKey(i))
					ops.add(opVertMap.get(i));

				if (varVertMap.containsKey(i))
					vars.add(varVertMap.get(i));				
			}
		}

		if (isObPerm) {
			if (ops.isEmpty() && vars.isEmpty())
				return SymmetryType.OBJECT;
			else 
				throw new IllegalStateException("Permutes more than just objects:\nOps: " + FormattingUtils.toString(ops) + "\nVars: " + FormattingUtils.toString(vars));
		}

		if (ops.size() != 2) {
			throw new IllegalStateException("Permutation of " + ops.size() + " operators.\nOps: " + FormattingUtils.toString(ops) + "\nVars: " + FormattingUtils.toString(vars));
		}

		if (vars.size() != ops.get(0).getSymbol().getArity() * 2) {
			throw new IllegalStateException("Permutation of " + ops.size() + " operators and " + vars.size() + " variables.\nOps: " + FormattingUtils.toString(ops) + "\nVars: " + FormattingUtils.toString(vars));
		}

		for (Operator<Variable> op :ops) {
			for (Variable var : op.getVariables()) {
				if (!vars.contains(var)) {
					throw new IllegalStateException("Variable/operator mismatch.\nOps: " + FormattingUtils.toString(ops) + "\nVars: " + FormattingUtils.toString(vars));
				}
			}
		}	

		return SymmetryType.OPERATOR;

	}


	protected void encodeValueLexLeader(Map<Constant, Constant> perm, Map<Variable, Integer> varIndexMap, Map<Constant, Integer> consIndexMap) {

		// get types
		Set<Type> types = new HashSet<Type>();
		for (Constant cons : perm.keySet())
			types.add(cons.getType());

		//get vars of type
		List<Variable> vars = new ArrayList<Variable>();
		for (Type t : types)
			vars.addAll(variablesByType.get(t));

		// not vars in init or goal
		vars.removeAll(plan.getInitSub().getVariables());
		vars.removeAll(plan.getGoalSub().getVariables());

		// var ordering
		Collections.sort(vars, new Comparator<Variable>() {
			@Override
			public int compare(Variable o1, Variable o2) {
				return Integer.compare(varIndexMap.get(o1), varIndexMap.get(o2));
			}

		});

		Map<Type, List<Constant>> ltCons = new HashMap<Type, List<Constant>>();
		Map<Type, List<Constant>> eqCons = new HashMap<Type, List<Constant>>();

		for (Type t : types) {
			ltCons.put(t, new ArrayList<Constant>());
			eqCons.put(t, new ArrayList<Constant>());

			for (Constant cons : constantsByType.get(t)) {
				if (perm.get(cons) == null) 
					eqCons.get(t).add(cons);
				else if (consIndexMap.get(cons) < consIndexMap.get(perm.get(cons))) {
					ltCons.get(t).add(cons);
				}
			}
		}

		List<Integer> aux = new ArrayList<Integer>();
		for (int i = 0; i < vars.size(); i++)
			aux.add(propMap.addEncodedObject("objlex-aux-"+i));

		for (int i = 0; i < vars.size(); i++) {
			int xi = aux.get(i);

			Variable var = vars.get(i);
			Type t = var.getType();

			List<Integer> ltps = new ArrayList<Integer>();
			for (Constant lt : ltCons.get(t)) {
				ltps.add(getVariableAssignmentProposition(var, lt));
			}
			List<Integer> eqps = new ArrayList<Integer>();
			for (Constant eq : eqCons.get(t)) {
				eqps.add(getVariableAssignmentProposition(var, eq));
			}

			if (i == 0) {
				// aux prop
				//ltr
				List<Integer> clause = new ArrayList<Integer>();
				clause.add(-xi);
				clause.addAll(eqps);
				satFormula.addClause(clause);

				// rtl
				for (int eqp : eqps) {
					satFormula.addClause(-eqp, xi);
				}

				// binding cons
				clause = new ArrayList<Integer>(eqps);
				clause.addAll(ltps);
				satFormula.addClause(clause);

			} else {
				int xim1 = aux.get(i-1);
				// aux prop
				// ltr
				satFormula.addClause(-xi, xim1);
				List<Integer> clause = new ArrayList<Integer>();
				clause.add(-xi);
				clause.addAll(eqps);
				satFormula.addClause(clause);

				//rtl
				for (int eqp : eqps) {
					satFormula.addClause(-xim1, -eqp, xi);
				}

				// binding cons
				clause = new ArrayList<Integer>(eqps);
				clause.add(-xim1);
				clause.addAll(ltps);
				satFormula.addClause(clause);
			}

		}
	}

	protected void encodeVariableLexLeader(Operator<Variable> opi, Operator<Variable> opj, Map<Variable, Integer> varIndexMap, Map<Constant, Integer> conIndexMap) {

		// first build var lex-leader
		List<Variable> vars = new ArrayList<Variable>(opi.getVariables());
		vars.addAll(opj.getVariables());
		// var ordering
		Collections.sort(vars, new Comparator<Variable>() {
			@Override
			public int compare(Variable o1, Variable o2) {
				return Integer.compare(varIndexMap.get(o1), varIndexMap.get(o2));
			}
		});

		Map<Variable, Variable> perm = new HashMap<Variable, Variable>();
		for (int i = 0; i < opi.getVariables().size(); i++) {
			perm.put(opi.getVariables().get(i), opj.getVariables().get(i));
			perm.put(opj.getVariables().get(i), opi.getVariables().get(i));
		}

		List<Integer> aux = new ArrayList<Integer>();
		for (int i = 0; i < vars.size(); i++)
			aux.add(propMap.addEncodedObject("oplex-vars-aux-"+i));

		for (int i = 0; i < vars.size(); i++) {
			Variable var = vars.get(i);
			Variable pvar = perm.get(var);

			buildEqualityProp(var, pvar); // might not be built yet

			int xi = aux.get(i);

			// aux_0 <-> x_1 = perm(x_1)
			if (i == 0) {
				// aux prop
				int eqProp = propMap.getEqualityProposition(var, pvar);
				satFormula.addClause(-xi, eqProp);
				satFormula.addClause(-eqProp, xi);

				// bindings
				List<Integer> clause = new ArrayList<Integer>();
				clause.add(eqProp); // equal, or
				for (Constant c : constantsByType.get(var.getType())) {
					clause.add(-getVariableAssignmentProposition(var, c));
					for (Constant c2 : constantsByType.get(var.getType())) {
						if (conIndexMap.get(c2) < conIndexMap.get(c)) {
							clause.add(getVariableAssignmentProposition(pvar, c2));
						}
					}	
				}
				satFormula.addClause(clause);

			} else {
				// aux prop
				int xim1 = aux.get(i-1);
				int eqProp = propMap.getEqualityProposition(var, pvar);
				//ltr
				satFormula.addClause(xi, xim1);
				satFormula.addClause(xi, eqProp);
				//rtl
				satFormula.addClause(-xim1, -eqProp, xi);

				// bindings
				List<Integer> clause = new ArrayList<Integer>();
				clause.add(-xim1); // previous aux prop implies
				clause.add(eqProp); // equal, or
				for (Constant c : constantsByType.get(var.getType())) {
					clause.add(-getVariableAssignmentProposition(var, c));
					for (Constant c2 : constantsByType.get(var.getType())) {
						if (conIndexMap.get(c2) < conIndexMap.get(c)) {
							clause.add(getVariableAssignmentProposition(pvar, c2));
						}
					}	
				}
				satFormula.addClause(clause);	
			}
		}	

	}

	protected void encodeOperatorLexLeader(Operator<Variable> opi, Operator<Variable> opj, Map<Variable, Integer> varIndexMap, Map<Constant, Integer> conIndexMap) {

		// first build var lex-leader
		List<Variable> vars = new ArrayList<Variable>(opi.getVariables());
		vars.addAll(opj.getVariables());
		// var ordering
		Collections.sort(vars, new Comparator<Variable>() {
			@Override
			public int compare(Variable o1, Variable o2) {
				return Integer.compare(varIndexMap.get(o1), varIndexMap.get(o2));
			}
		});

		Map<Variable, Variable> perm = new HashMap<Variable, Variable>();
		for (int i = 0; i < opi.getVariables().size(); i++) {
			perm.put(opi.getVariables().get(i), opj.getVariables().get(i));
			perm.put(opj.getVariables().get(i), opi.getVariables().get(i));
		}

		List<Integer> aux = new ArrayList<Integer>();
		for (int i = 0; i < vars.size(); i++)
			aux.add(propMap.addEncodedObject("oplex-vars-aux-"+i));

		for (int i = 0; i < vars.size(); i++) {
			Variable var = vars.get(i);
			Variable pvar = perm.get(var);

			buildEqualityProp(var, pvar); // might not be built yet

			int xi = aux.get(i);

			// aux_0 <-> x_1 = perm(x_1)
			if (i == 0) {
				// aux prop
				int eqProp = propMap.getEqualityProposition(var, pvar);
				satFormula.addClause(-xi, eqProp);
				satFormula.addClause(-eqProp, xi);

				// bindings
				List<Integer> clause = new ArrayList<Integer>();
				clause.add(eqProp); // equal, or
				for (Constant c : constantsByType.get(var.getType())) {
					clause.add(-getVariableAssignmentProposition(var, c));
					for (Constant c2 : constantsByType.get(var.getType())) {
						if (conIndexMap.get(c2) < conIndexMap.get(c)) {
							clause.add(getVariableAssignmentProposition(pvar, c2));
						}
					}	
				}
				satFormula.addClause(clause);

			} else {
				// aux prop
				int xim1 = aux.get(i-1);
				int eqProp = propMap.getEqualityProposition(var, pvar);
				//ltr
				satFormula.addClause(xi, xim1);
				satFormula.addClause(xi, eqProp);
				//rtl
				satFormula.addClause(-xim1, -eqProp, xi);

				// bindings
				List<Integer> clause = new ArrayList<Integer>();
				clause.add(-xim1); // previous aux prop implies
				clause.add(eqProp); // equal, or
				for (Constant c : constantsByType.get(var.getType())) {
					clause.add(-getVariableAssignmentProposition(var, c));
					for (Constant c2 : constantsByType.get(var.getType())) {
						if (conIndexMap.get(c2) < conIndexMap.get(c)) {
							clause.add(getVariableAssignmentProposition(pvar, c2));
						}
					}	
				}
				satFormula.addClause(clause);	
			}
		}	

		// operator ordering lex-leader

		Operator<Variable> op1 = plan.getPlanSteps().indexOf(opi) < plan.getPlanSteps().indexOf(opj) ? opi : opj;
		int op1Ind = plan.getPlanSteps().indexOf(op1);

		List<Pair<Operator<Variable>, Operator<Variable>>> vec = new ArrayList<Pair<Operator<Variable>, Operator<Variable>>>();
		for (int i = 0; i < op1Ind; i++) {
			vec.add(Pair.instance(plan.getPlanSteps().get(i), op1));
		}
		for (Operator<Variable> op2 : plan.getPlanSteps()) {
			vec.add(Pair.instance(op2, op1));
		}
		for (int i = op1Ind+1; i < plan.getPlanSteps().size(); i++) {
			vec.add(Pair.instance(plan.getPlanSteps().get(i), op1));
		}

		int xim1 = aux.get(aux.size()-1); // end of previous lex leader
		for (int i = 0; i < vec.size(); i++) {
			Operator<Variable> first = vec.get(i).getFirst();
			Operator<Variable> second = vec.get(i).getSecond();	
			Operator<Variable> permFirst = (first.equals(opi) ? opj : (first.equals(opj) ? opi : first));
			Operator<Variable> permSecond = (second.equals(opi) ? opj : (second.equals(opj) ? opi : second));


			// aux prop
			int xi = propMap.addEncodedObject("oplex-order-aux-"+i);

			Integer precProp = propMap.getPrecedenceProposition(first, second);
			Integer permPrecProp = propMap.getPrecedenceProposition(permFirst, permSecond);


			// ltr
			satFormula.addClause(-xi, xim1);
			if (precProp == null && permPrecProp == null) {
				// add nothing

			} else if (precProp == null && permPrecProp != null) {
				satFormula.addClause(-xi, -permPrecProp);

			} else if (precProp != null && permPrecProp == null) {
				satFormula.addClause(-xi, -precProp);
			} else {
				satFormula.addClause(-xi, -precProp, permPrecProp);
				satFormula.addClause(-xi, precProp, -permPrecProp);
			}

			// rtl
			if (precProp == null && permPrecProp == null) {
				satFormula.addClause(xi, -xim1);
			} else if (precProp == null && permPrecProp != null) {
				satFormula.addClause(xi, -xim1, permPrecProp);
			} else if (precProp != null && permPrecProp == null) {
				satFormula.addClause(xi, -xim1, precProp);
			} else {
				satFormula.addClause(xi, -xim1, precProp, permPrecProp);
				satFormula.addClause(xi, -xim1, -precProp, -permPrecProp);
			}


			// ordering
			// satFormula.addClause(-xim1, -precProp, permPrecProp);
			if (precProp == null && permPrecProp == null) {

			} else if (precProp == null && permPrecProp != null) {

			} else if (precProp != null && permPrecProp == null) {
				satFormula.addClause(-xim1, -precProp);
			} else {
				satFormula.addClause(-xim1, -precProp, permPrecProp);
			}

			xim1 = xi; // ready for next iteration
		}

	}

	protected void breakOperatorTypeSymmetries() {
		for (String type : operatorsByType.keySet()) {
			List<Operator<Variable>> ops = operatorsByType.get(type);

			for (int i = 0; i < ops.size(); i++) {
				Operator<Variable> op1 = ops.get(i);
				for (int j = i + 1; j < ops.size(); j++) {
					Operator<Variable> op2 = ops.get(j);

					// if it's a ground encoding, only order operators with the same bindings
					if (!this.causalStruct.isGround() || plan.getOriginalSub().apply(op1.getVariables()).equals(plan.getOriginalSub().apply(op2.getVariables()))) {					
						// if index(op1) < index(op2) then not (op2 < op1)
						Integer prop = propMap.getPrecedenceProposition(op2, op1);
						if (prop != null) {
							satFormula.addClause(-prop);
							if (options.verbose) {
								System.out.println(op2.getName() + " >= " + op1.getName());
							}
						}

					}
				}
			}
		}
	}

	protected static String getOperatorType(Operator<?> op) {
		String type = op.getName();
		return type.substring(type.indexOf("_")+1);	
	}

}
