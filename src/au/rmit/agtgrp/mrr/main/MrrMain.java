package au.rmit.agtgrp.mrr.main;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;

import au.rmit.agtgrp.mrr.encoder.CnfEncoderOptions;
import au.rmit.agtgrp.mrr.encoder.EqualityObj;
import au.rmit.agtgrp.mrr.encoder.PcToWcnfEncoder;
import au.rmit.agtgrp.mrr.encoder.PrecedenceObj;
import au.rmit.agtgrp.mrr.encoder.CnfEncoderOptions.AcyclicityOpt;
import au.rmit.agtgrp.mrr.encoder.CnfEncoderOptions.AsymmetryOpt;
import au.rmit.agtgrp.mrr.encoder.CnfEncoderOptions.CausalStructureOpt;
import au.rmit.agtgrp.mrr.encoder.CnfEncoderOptions.EqualityOpt;
import au.rmit.agtgrp.mrr.encoder.CnfEncoderOptions.OutputOpt;
import au.rmit.agtgrp.mrr.fol.function.Constant;
import au.rmit.agtgrp.mrr.fol.symbol.Variable;
import au.rmit.agtgrp.mrr.pct.CausalStructureFactory;
import au.rmit.agtgrp.mrr.pct.PoclPlan;
import au.rmit.agtgrp.mrr.pddl.Operator;
import au.rmit.agtgrp.mrr.pddl.ParallelPlan;
import au.rmit.agtgrp.mrr.pddl.Plan;
import au.rmit.agtgrp.mrr.pddl.PddlProblem.PlanResult;
import au.rmit.agtgrp.mrr.pddl.parser.PddlParser;
import au.rmit.agtgrp.mrr.pddl.parser.PddlParserException;
import au.rmit.agtgrp.mrr.sat.SatFormula;
import au.rmit.agtgrp.mrr.sat.WeightedSatFormula;
import au.rmit.agtgrp.mrr.utils.FileUtils;
import au.rmit.agtgrp.mrr.utils.FormattingUtils;
import au.rmit.agtgrp.mrr.utils.collections.Bijection;
import au.rmit.agtgrp.mrr.utils.collections.Pair;
import au.rmit.agtgrp.mrr.utils.collections.graph.DirectedGraph;
import au.rmit.agtgrp.mrr.utils.collections.graph.GraphUtils;

public class MrrMain {

	public static void main(String[] args) throws IOException, InterruptedException {
		MrrOptions options = new MrrOptions();
		options.parse(args);
		
		if (Action.DECODE.equals(options.action)) {		
			decode(options);
		}
		else {
			encode(options);
		}
	}

	private static void encode(MrrOptions options) throws IOException {
		long start = System.currentTimeMillis();

		System.out.println("Loading PDDL");
		Plan plan = options.getPlan();		

		System.out.println("Lifting input plan");
		PoclPlan pcoPlan = CausalStructureFactory.getMinimalPcoPlan(plan, !options.algorithm.csOpt.equals(CausalStructureOpt.REORDER), options.algorithm.ground);

		System.out.println("Encoding WCNF");
		DirectedGraph<Operator<Variable>> customPrecGraph = null;
		if (plan instanceof ParallelPlan && !options.algorithm.csOpt.equals(CausalStructureOpt.REORDER)) {
			System.out.println("Computing parallel plan ordering");
			options.algorithm.csOpt = CausalStructureOpt.CUSTOM;
			customPrecGraph = getParallelPlanOrdering((ParallelPlan) plan);	
		}

		CnfEncoderOptions opts = new CnfEncoderOptions(options.algorithm.asymm, options.algorithm.eq,
				options.algorithm.acyc, options.algorithm.csOpt, OutputOpt.PARTIAL_ORDER, false, 0,
				options.algorithm.optTransClosure, options.verbose, customPrecGraph);	
		PcToWcnfEncoder enc = new PcToWcnfEncoder(opts);	
		WeightedSatFormula wcnf = enc.encodeConstraints(pcoPlan);

		long encTime = System.currentTimeMillis() - start;
		System.out.println("Encoding time: " + FormattingUtils.DF_3.format(((double) encTime)/1000));

		Map<PrecedenceObj, Integer> precPropMap = enc.getPropositionMap().getPrecedencePropositionMap();
		Map<Integer, PrecedenceObj> propPrecMap = new HashMap<Integer, PrecedenceObj>();		
		for (PrecedenceObj prec : precPropMap.keySet())
			propPrecMap.put(precPropMap.get(prec), prec);

		Bijection<EqualityObj, Integer> eqPropMap = enc.getPropositionMap().getVarEqualityPropositionMap();
		Map<Integer, EqualityObj> propBindMap = new HashMap<Integer, EqualityObj>();
		for (Entry<EqualityObj, Integer> entry : eqPropMap.entrySet()) {
			propBindMap.put(entry.getValue(), entry.getKey());
		}

		// save wcnf
		System.out.println("Writing weighted CNF to " + options.wcnfFile);
		wcnf.writeToFile(options.wcnfFile);	
		FileUtils.serialize(propPrecMap, new File(options.wcnfFile.getAbsolutePath() + ".prec.dat"));
		FileUtils.serialize(propBindMap, new File(options.wcnfFile.getAbsolutePath() + ".bind.dat"));
	
	}


	private static void decode(MrrOptions options) {
		System.out.println("Loading PDDL");
		Plan plan = options.getPlan();

		System.out.println("Decoding model");		
		// get all ordering constraints
		DirectedGraph<Operator<Variable>> precGraph = new DirectedGraph<Operator<Variable>>();

		Set<Variable> initVars = new HashSet<Variable>(plan.getInitialAction().getParameters());
		Map<Variable, Constant> bindings = new HashMap<Variable, Constant>(plan.getSubstitution().getMap()); // init to original
		
		Map<Integer, PrecedenceObj> propPrecMap = FileUtils.deserialize(new File(options.wcnfFile.getAbsolutePath() + ".prec.dat"));		
		Map<Integer, EqualityObj> propBindMap = FileUtils.deserialize(new File(options.wcnfFile.getAbsolutePath() + ".bind.dat"));

		int[] soln = SatFormula.loadModel(options.model);
		for (int prop : soln) {
			if (prop > 0) {
				if (propPrecMap.containsKey(prop)) {
					PrecedenceObj prec = propPrecMap.get(prop);	
					if (!prec.getFirst().equals(plan.getInitialAction()) && !prec.getSecond().equals(plan.getGoalAction())) {
						GraphUtils.addAndCloseTransitive(precGraph, prec.getFirst(), prec.getSecond());
					}
				} else if (propBindMap.containsKey(prop)){
					EqualityObj bind = propBindMap.get(prop);
					if (initVars.contains(bind.getFirst())) {
						Constant c = plan.getSubstitution().apply(bind.getFirst());
						bindings.put(bind.getSecond(), c);						
					}
					else if (initVars.contains(bind.getSecond())) {
						Constant c = plan.getSubstitution().apply(bind.getSecond());
						bindings.put(bind.getFirst(), c);
					}
				}
			}
		}
		
		int relSize = precGraph.getAllEdges().size();
		System.out.println("Order relation size: " + relSize);

		// calculate flex value	
		double flex = getFlex(relSize, plan.getPlanSteps().size()-2);
		System.out.println("Flex: " + flex);

		// print out pop
		GraphUtils.transitiveReduction(precGraph, precGraph);
		System.out.println("Printing POP to " + options.popFile);
		FileUtils.writeFile(options.popFile, formatPopString(plan.getPlanSteps(), precGraph, bindings));		
	}


	public static String formatPopString(List<Operator<Variable>> steps, DirectedGraph<Operator<Variable>> precGraph, Map<Variable, Constant> bindings) {
		StringBuilder popSb = new StringBuilder();
		
		popSb.append("** Operators\n");
		for (Operator<Variable> op : steps)
			popSb.append(op.formatParameters() + "\n");

		popSb.append("** Ordering\n");
		List<Pair<Operator<Variable>, Operator<Variable>>> edges = new ArrayList<Pair<Operator<Variable>, Operator<Variable>>>(precGraph.getAllEdges());
		edges.sort(new Comparator<Pair<Operator<Variable>, Operator<Variable>>>() {
			@Override
			public int compare(Pair<Operator<Variable>, Operator<Variable>> o1,
					Pair<Operator<Variable>, Operator<Variable>> o2) {

				int c = o1.getFirst().getName().compareTo(o2.getFirst().getName());
				if (c == 0)
					c = o1.getSecond().getName().compareTo(o2.getSecond().getName());
				return c;
			}
		});

		for (Pair<Operator<Variable>, Operator<Variable>> edge : edges)
			popSb.append(edge.getFirst().getName() + " < " + edge.getSecond().getName() + "\n");

		popSb.append("** Binding\n");
		for (Operator<Variable> op : steps) {
			for (Variable var : op.getVariables())
				popSb.append(var.getName() + "=" + bindings.get(var).getName() + "\n");
		}
		
		return popSb.toString();
	}

	
	public static DirectedGraph<Operator<Variable>> getParallelPlanOrdering(ParallelPlan pplan) {
		DirectedGraph<Operator<Variable>> customPrecGraph = new DirectedGraph<Operator<Variable>>();
		List<Operator<Variable>> prevStep = null;
		for (List<Operator<Variable>> step : pplan.getParallelSteps()) {
			if (prevStep != null) {
				for (Operator<Variable> prevOp : prevStep) {
					for (Operator<Variable> op : step)
						GraphUtils.addAndCloseTransitive(customPrecGraph, prevOp, op);
				}
			}
			prevStep = step;
		}
		return customPrecGraph;
	}

	public static double getFlex(int relSize, int nOps) {
		int den = 0;
		for (int i = 1; i <= nOps-1; i++) 
			den+=i;

		if (den == 0) // happens if plan has one step
			den = 1;

		double flex = 1 - (((double) relSize) / den);
		return flex;
	}

	public static enum OptAlgorithm {

		MD_ORIG 		(true, CausalStructureOpt.DEORDER,	AsymmetryOpt.NONE, EqualityOpt.NONE, AcyclicityOpt.ATOM, false), 	// Muise's minimum deorder
		MR_ORIG 		(true, CausalStructureOpt.REORDER, 	AsymmetryOpt.NONE, EqualityOpt.NONE, AcyclicityOpt.ATOM, false), 	// Muise's minimum reorder 		

		MD 		(true, CausalStructureOpt.DEORDER,	AsymmetryOpt.NONE, EqualityOpt.NONE, AcyclicityOpt.ATOM, true), 	// minimum deorder
		MR 		(true, CausalStructureOpt.REORDER, 	AsymmetryOpt.NONE, EqualityOpt.NONE, AcyclicityOpt.ATOM, true), 	// minimum reorder 		
		MR_OPSB	(true, CausalStructureOpt.REORDER, 	AsymmetryOpt.OP_TYPES, EqualityOpt.NONE, AcyclicityOpt.ATOM, true), 	// minimum reorder 		

		MRD 	 (false, CausalStructureOpt.DEORDER, AsymmetryOpt.NONE, EqualityOpt.ATOM, AcyclicityOpt.ATOM, true),	// minimum reinstantiated deorder
		MRD_CSSB (false, CausalStructureOpt.DEORDER, AsymmetryOpt.INIT_STATE, EqualityOpt.ATOM, AcyclicityOpt.ATOM, true),	// minimum reinstantiated deorder w/SB
		
		MRR 	(false, CausalStructureOpt.REORDER, AsymmetryOpt.NONE, EqualityOpt.ATOM, AcyclicityOpt.ATOM, true),	// minimum reinstantiated reorder
		MRR_OPSB (false, CausalStructureOpt.REORDER, AsymmetryOpt.OP_TYPES, EqualityOpt.ATOM, AcyclicityOpt.ATOM, true),
		MRR_CSSB (false, CausalStructureOpt.REORDER, AsymmetryOpt.STRUCT,   EqualityOpt.ATOM, AcyclicityOpt.ATOM, true); // reinstantiated explanation-based order generalisation

		public final boolean ground;
		public AsymmetryOpt asymm;
		public EqualityOpt eq;
		public AcyclicityOpt acyc;
		public CausalStructureOpt csOpt;
		public boolean optTransClosure;

		private OptAlgorithm(boolean ground, CausalStructureOpt csOpt, AsymmetryOpt asymm, EqualityOpt equal, AcyclicityOpt acyc, boolean filterLinks) {
			this.ground = ground;
			this.csOpt = csOpt;
			this.asymm = asymm;
			this.eq = equal;
			this.acyc = acyc;
			this.optTransClosure = filterLinks;
		}

	}

	public static enum Action {
		ENCODE, DECODE
	}

	public static class MrrOptions {

	@Option(name = "--help", usage = "print this help message", help = true, metaVar = "OPT")
	public boolean help;

	@Option(name = "--verbose", usage = "verbose", metaVar = "OPT")
	public boolean verbose;

	@Option(name = "--domain", usage = "domain file", required = true)
	public File domainFile;

	@Option(name = "--problem", usage = "problem file", required = true)
	public File problemFile;

	@Option(name = "--plan", usage = "plan file", required = true)
	public File planFile;

	@Option(name = "--pop", usage = "out file")
	public File popFile = new File("optimised.pop");

	@Option(name = "--wcnf", usage = "output wcnf file")
	public File wcnfFile = new File("encoded.wcnf");

	@Option(name = "--model", usage = "model file")
	public File model = null;

	@Option(name = "--enc", usage = "optimisation encoding")
	public OptAlgorithm algorithm = null;

	@Option(name = "--action", usage = "encode to MaxSAT or decode solution to POP", required = true)
	public Action action = null;

	private Plan plan;
	
	public void parse(String[] args) {
		ParserProperties properties = ParserProperties.defaults();
		properties.withOptionSorter(null);

		CmdLineParser optionParser = new CmdLineParser(this, properties);

		StringWriter usage = new StringWriter();
		optionParser.printUsage(usage, null);

		List<String> optionStrs = new ArrayList<String>();
		for (String arg : args)
			optionStrs.add(arg);

		try {
			optionParser.parseArgument(optionStrs);

		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			System.err.println(usage);
			System.exit(1);
		}

		// print help message if requested
		if (this.help) {
			System.out.println(usage);
			System.exit(0);
		}

		// load pddl from supplied files
		loadPlan();
	}

	private void loadPlan() {

		PddlParser pddlParser = new PddlParser(true, false);

		try {
			pddlParser.parse(domainFile, problemFile);
			String planFileName = planFile.getName();
			if (planFileName.endsWith(".lama") || planFileName.endsWith(".ss") || planFileName.endsWith(".bfws")) {
				pddlParser.parseFDPlan(planFile);
			}
			else if (planFileName.endsWith(".m")) {
				pddlParser.parseMadagascarPlan(planFile);
			}
			else {
				throw new IllegalArgumentException("Unknown plan type: " + planFile);
			}
			
			
		}  catch (PddlParserException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		} catch (Exception e) {
			System.err.println("Unexpected error while parsing PDDL");
			e.printStackTrace();
			System.exit(1);
		}

		// check that the plan is valid
		PlanResult pr = pddlParser.getProblem().validatePlan(pddlParser.getPlan());
		if (!pr.isValid) {
			System.err.println("Input plan is not valid");
			System.err.println(pr.message);
			System.exit(1);
		}
		
		plan = pddlParser.getPlan();		
	}
	
	public Plan getPlan() {
		if (plan == null)
			loadPlan();
		return plan;
	}

		

	}

}
