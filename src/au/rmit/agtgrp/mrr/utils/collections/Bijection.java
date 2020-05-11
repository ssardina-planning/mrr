package au.rmit.agtgrp.mrr.utils.collections;

import java.util.Map;

public interface Bijection<K, V> extends Map<K, V> {

	public K getKey(V value);
	
}
