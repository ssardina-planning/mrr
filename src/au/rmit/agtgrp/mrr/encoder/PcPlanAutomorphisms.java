package au.rmit.agtgrp.mrr.encoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import au.rmit.agtgrp.mrr.auto.Group;
import au.rmit.agtgrp.mrr.auto.NautyInterface;
import au.rmit.agtgrp.mrr.auto.NautyResult;
import au.rmit.agtgrp.mrr.fol.function.Constant;
import au.rmit.agtgrp.mrr.fol.predicate.Literal;
import au.rmit.agtgrp.mrr.fol.predicate.Predicate;
import au.rmit.agtgrp.mrr.fol.symbol.Type;
import au.rmit.agtgrp.mrr.fol.symbol.Variable;
import au.rmit.agtgrp.mrr.pct.Consumer;
import au.rmit.agtgrp.mrr.pct.PoclPlan;
import au.rmit.agtgrp.mrr.pct.Producer;
import au.rmit.agtgrp.mrr.pddl.Operator;
import au.rmit.agtgrp.mrr.pddl.PddlProblem;
import au.rmit.agtgrp.mrr.utils.collections.graph.UndirectedColouredGraph;

public class PcPlanAutomorphisms {

	public enum SymmetryType {
		OBJECT, OPERATOR, STRUCTURAL
	}

	public static PcPlanAutomorphisms getPdgAutomorphisms(PoclPlan plan, boolean verbose) {

		UndirectedColouredGraph<Integer, Integer> graph = new UndirectedColouredGraph<Integer, Integer>();

		// colours
		int c = 0;

		Map<Type, Integer> constTypeColours = new HashMap<Type, Integer>();
		Map<Type, Integer> varTypeColours = new HashMap<Type, Integer>();
		for (Type t : plan.getDomain().getTypes()) {
			constTypeColours.put(t, c++);
			varTypeColours.put(t, c++);
		}
		Map<Boolean, Map<Predicate, Integer>> predColours = new HashMap<Boolean, Map<Predicate, Integer>>();
		predColours.put(true, new HashMap<Predicate, Integer>());
		predColours.put(false, new HashMap<Predicate, Integer>());

		for (Predicate p : plan.getDomain().getPredicates()) {
			predColours.get(true).put(p, c++);
			predColours.get(false).put(p, c++);	
		}

		Map<String, Integer> opTypeColours = new HashMap<String, Integer>();
		for (Operator<Variable> op : plan.getOperators()) {
			String type = op.getName().substring(op.getName().indexOf("_") + 1);
			opTypeColours.put(type, c++);
		}

		// vertices
		int v = 0;
		Map<Constant, Integer> constVerts = new HashMap<Constant, Integer>();
		for (Constant con : plan.getDomain().getConstants()) {
			graph.addVertex(v, constTypeColours.get(con.getType()));
			constVerts.put(con, v++);
		}
		for (Constant con :plan.getProblem().getObjects()) {
			graph.addVertex(v, constTypeColours.get(con.getType()));
			constVerts.put(con, v++);
		}

		Map<Variable, Integer> varVerts = new HashMap<Variable, Integer>();
		for (Operator<Variable> op : plan.getOperators()) {
			if (op.equals(plan.getInitAction()) || op.equals(plan.getGoalAction()))
				continue;

			for (Variable var : op.getVariables()) {
				graph.addVertex(v, varTypeColours.get(var.getType()));
				varVerts.put(var, v++);
			}
		}

		Map<Operator<Variable>, Integer> opVerts = new HashMap<Operator<Variable>, Integer>();
		for (Operator<Variable> op : plan.getOperators()) {
			String type = op.getName().substring(op.getName().indexOf("_") + 1);
			graph.addVertex(v, opTypeColours.get(type));
			opVerts.put(op, v++);
		}

		Map<Producer, List<Integer>> prodVerts = new HashMap<Producer, List<Integer>>();
		for (Literal<Variable> lit : plan.getInitAction().getPostconditions()) {
			List<Integer> verts = new ArrayList<Integer>();
			int col = predColours.get(lit.getValue()).get(lit.getAtom().getSymbol());
			for (int i = 0; i < lit.getAtom().getSymbol().getArity()+1; i++) {
				graph.addVertex(v, col);
				verts.add(v++);
			}
			prodVerts.put(new Producer(plan.getInitAction(), lit), verts);
		}

		Map<Consumer, List<Integer>> consVerts = new HashMap<Consumer, List<Integer>>();
		for (Literal<Variable> lit : plan.getGoalAction().getPreconditions()) {
			List<Integer> verts = new ArrayList<Integer>();
			int col = predColours.get(lit.getValue()).get(lit.getAtom().getSymbol());
			for (int i = 0; i < lit.getAtom().getSymbol().getArity()+1; i++) { // one for lit and one for each parameter
				graph.addVertex(v, col);
				verts.add(v++);
			}
			consVerts.put(new Consumer(plan.getGoalAction(), lit), verts);
		}

		// edges
		for (Operator<Variable> op : plan.getOperators()) {
			if (op.equals(plan.getInitAction()) || op.equals(plan.getGoalAction())) {
				List<Literal<Variable>> lits = op.equals(plan.getInitAction()) ? op.getPostconditions() : op.getPreconditions();
				for (Literal<Variable> lit : lits) {
					
					List<Integer> paramVerts = op.equals(plan.getInitAction()) ? prodVerts.get(new Producer(plan.getInitAction(), lit)) : consVerts.get(new Consumer(plan.getGoalAction(), lit));
					int opVert = opVerts.get(op);
					int preVert = paramVerts.get(0);
					graph.addEdge(opVert, preVert); // from op to literal
					
					for (int i = 0; i < lit.getAtom().getSymbol().getArity(); i++) {// from lit to first param and on
						int paramVert = paramVerts.get(i+1);
						graph.addEdge(paramVert, preVert);
						preVert = paramVert;

						int constVert = constVerts.get(plan.getOriginalSub().apply(lit.getAtom().getVariables().get(i)));
						graph.addEdge(paramVert, constVert);
					}


				}
			} else {
				int preVert = opVerts.get(op);
				for (Variable var : op.getVariables()) {
					int varVert = varVerts.get(var);
					graph.addEdge(preVert, varVert);
					preVert = varVert;
				}
			}
		}

		NautyResult result = NautyInterface.getAutomorphisms(graph, verbose);

		Map<Producer, Integer> prodVert = new HashMap<Producer, Integer>();
		for (Producer prod : prodVerts.keySet())
			prodVert.put(prod,  prodVerts.get(prod).get(0));
		Map<Consumer, Integer> consVert = new HashMap<Consumer, Integer>();
		for (Consumer cons : consVerts.keySet())
			consVert.put(cons,  consVerts.get(cons).get(0));
		
		
		return new PcPlanAutomorphisms(result, constVerts, opVerts, varVerts, prodVert, consVert);

	}

	public static PcPlanAutomorphisms getProbDescAutomorphisms(PddlProblem problem, boolean verbose) {

		UndirectedColouredGraph<Integer, Integer> graph = new UndirectedColouredGraph<Integer, Integer>();

		// colours
		int c = 0;

		Map<Type, Integer> constTypeColours = new HashMap<Type, Integer>();
		Map<Type, Integer> varTypeColours = new HashMap<Type, Integer>();
		for (Type t : problem.getDomain().getTypes()) {
			constTypeColours.put(t, c++);
			varTypeColours.put(t, c++);
		}
		Map<Boolean, Map<Predicate, Integer>> predColours = new HashMap<Boolean, Map<Predicate, Integer>>();
		predColours.put(true, new HashMap<Predicate, Integer>());
		predColours.put(false, new HashMap<Predicate, Integer>());

		for (Predicate p : problem.getDomain().getPredicates()) {
			predColours.get(true).put(p, c++);
			predColours.get(false).put(p, c++);	
		}

		int initCol = c++;
		int goalCol = c++;

		// vertices
		int v = 0;
		Map<Constant, Integer> constVerts = new HashMap<Constant, Integer>();
		for (Constant con : problem.getDomain().getConstants()) {
			graph.addVertex(v, constTypeColours.get(con.getType()));
			constVerts.put(con, v++);
		}
		for (Constant con : problem.getObjects()) {
			graph.addVertex(v, constTypeColours.get(con.getType()));
			constVerts.put(con, v++);
		}

		int initVert = v;
		graph.addVertex(v++, initCol);
		int goalVert = v;
		graph.addVertex(v++, goalCol);



		Map<Literal<Constant>, List<Integer>> litVerts = new HashMap<Literal<Constant>, List<Integer>>();
		for (Literal<Constant> lit : problem.getInitialState().getFacts()) {
			List<Integer> verts = new ArrayList<Integer>();
			int col = predColours.get(lit.getValue()).get(lit.getAtom().getSymbol());
			for (int i = 0; i < lit.getAtom().getSymbol().getArity(); i++) {
				graph.addVertex(v, col);
				verts.add(v++);
			}
			litVerts.put(lit, verts);
		}

		for (Literal<Constant> lit : problem.getGoalState().getFacts()) {
			List<Integer> verts = new ArrayList<Integer>();
			int col = predColours.get(lit.getValue()).get(lit.getAtom().getSymbol());
			for (int i = 0; i < lit.getAtom().getSymbol().getArity(); i++) {
				graph.addVertex(v, col);
				verts.add(v++);
			}
			litVerts.put(lit, verts);
		}

		// edges
		for (Literal<Constant> lit : problem.getInitialState().getFacts()) {
			List<Integer> paramVerts = litVerts.get(lit);
			
			int preVert = initVert;
			for (int i = 0; i < lit.getAtom().getSymbol().getArity(); i++) {
				int paramVert = paramVerts.get(i);
				graph.addEdge(paramVert, preVert);
				preVert = paramVert;

				int constVert = constVerts.get(lit.getAtom().getParameters().get(i));
				graph.addEdge(paramVert, constVert);
			}
		}
		
		for (Literal<Constant> lit : problem.getGoalState().getFacts()) {
			List<Integer> paramVerts = litVerts.get(lit);
			
			int preVert = goalVert;
			for (int i = 0; i < lit.getAtom().getSymbol().getArity(); i++) {
				int paramVert = paramVerts.get(i);
				graph.addEdge(paramVert, preVert);
				preVert = paramVert;

				int constVert = constVerts.get(lit.getAtom().getParameters().get(i));
				graph.addEdge(paramVert, constVert);
			}
		}

		NautyResult result = NautyInterface.getAutomorphisms(graph, verbose);

		return new PcPlanAutomorphisms(result, constVerts, null, null, null, null);

	}


	private final NautyResult result;

	private final Map<Operator<Variable>, Integer> opVertexMap;
	private final Map<Constant, Integer> constantVertexMap;
	private final Map<Variable, Integer> variableVertexMap;
	private final Map<Producer, Integer> prodVertexMap;
	private final Map<Consumer, Integer> consVertexMap;

	private final Map<Integer, Operator<Variable>> vertexOpMap;
	private final Map<Integer, Constant> vertexConstantMap;
	private final Map<Integer, Variable> vertexVariableMap;
	private final Map<Integer, Producer> vertexProdMap;
	private final Map<Integer, Consumer> vertexConsMap;
	

	private PcPlanAutomorphisms(NautyResult result, Map<Constant, Integer> constantVertexMap,
			Map<Operator<Variable>, Integer> opVertexMap, Map<Variable, Integer> variableVertexMap, 
			Map<Producer, Integer> prodVertexMap, Map<Consumer, Integer> consVertexMap) {

		
		this.result = result;

		this.constantVertexMap = constantVertexMap;
		if (constantVertexMap != null) {
			vertexConstantMap = new HashMap<Integer, Constant>();
			for (Constant c : constantVertexMap.keySet())
				vertexConstantMap.put(constantVertexMap.get(c), c);
		}
		else {
			vertexConstantMap = null;
		}

		this.variableVertexMap = variableVertexMap;
		if (variableVertexMap != null) {
			vertexVariableMap = new HashMap<Integer, Variable>();
			for (Variable v : variableVertexMap.keySet())
				vertexVariableMap.put(variableVertexMap.get(v), v);
		}
		else {
			vertexVariableMap = null;
		}

		this.opVertexMap = opVertexMap;
		if (opVertexMap != null) {
			vertexOpMap = new HashMap<Integer, Operator<Variable>>();
			for (Operator<Variable> op : opVertexMap.keySet())
				vertexOpMap.put(opVertexMap.get(op), op);
		}
		else {
			vertexOpMap = null;
		}
		
		this.prodVertexMap = prodVertexMap;
		if (prodVertexMap != null) {
			vertexProdMap = new HashMap<Integer, Producer>();
			for (Producer prod : prodVertexMap.keySet())
				vertexProdMap.put(prodVertexMap.get(prod), prod);
		} else {
			vertexProdMap = null;
		}

		this.consVertexMap = consVertexMap;
		if (consVertexMap != null) {
			vertexConsMap = new HashMap<Integer, Consumer>();
			for (Consumer cons : consVertexMap.keySet())
				vertexConsMap.put(consVertexMap.get(cons), cons);
		} else {
			vertexConsMap = null;
		}

	
	}

	public NautyResult getNautyResult() {
		return result;
	}
	
	public Group getGroup() {
		return result.generator;
	}

	public Map<Integer, Constant> getVertexConstantMap() {
		return vertexConstantMap;
	}

	public Map<Integer, Variable> getVertexVariableMap() {
		return vertexVariableMap;
	}

	public Map<Integer, Operator<Variable>> getVertexOpMap() {
		return vertexOpMap;
	}

	public Map<Operator<Variable>, Integer> getOpVertexMap() {
		return opVertexMap;
	}

	public Map<Constant, Integer> getConstantVertexMap() {
		return constantVertexMap;
	}

	public Map<Variable, Integer> getVariableVertexMap() {
		return variableVertexMap;
	}
	
	public Map<Producer, Integer> getProdVertexMap() {
		return prodVertexMap;
	}

	public Map<Consumer, Integer> getConsVertexMap() {
		return consVertexMap;
	}

	public Map<Integer, Producer> getVertexProdMap() {
		return vertexProdMap;
	}

	public Map<Integer, Consumer> getVertexConsMap() {
		return vertexConsMap;
	}

}
