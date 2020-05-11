package au.rmit.agtgrp.mrr.auto;

import java.util.List;

import au.rmit.agtgrp.mrr.utils.FormattingUtils;

public class Group {

	private final List<Permutation> permutations;

	public Group(List<Permutation> permutations) {
		this.permutations = permutations;
	}

	public List<Permutation> getPermutations() {
		return permutations;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(FormattingUtils.toString(permutations, ","));
		return sb.toString();
	}
	
}
