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

package au.rmit.agtgrp.mrr.utils.collections;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HashBijection <K, V> implements Bijection<K, V> {

	private final Map<K, V> keyToVal;
	private final Map<V, K> valToKey;
	
	public HashBijection() {
		keyToVal = new HashMap<K, V>();
		valToKey = new HashMap<V, K>();	
	}

	@Override
	public int size() {
		return keyToVal.size();
	}

	@Override
	public boolean isEmpty() {
		return keyToVal.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return keyToVal.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return valToKey.containsKey(value);
	}

	@Override
	public V get(Object key) {
		return keyToVal.get(key);
	}
	
	@Override
	public K getKey(V value) {
		return valToKey.get(value);
	}

	@Override
	public V put(K key, V value) {
		keyToVal.put(key, value);
		valToKey.put(value, key);
		return value;
	}

	@Override
	public V remove(Object key) {
		V v = keyToVal.remove(key);
		valToKey.remove(v);
		return v;
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		for (java.util.Map.Entry<? extends K, ? extends V> entry : m.entrySet())
			this.put(entry.getKey(), entry.getValue());
	}

	@Override
	public void clear() {
		keyToVal.clear();
		valToKey.clear();
		
	}

	@Override
	public Set<K> keySet() {
		return keyToVal.keySet();
	}

	@Override
	public Collection<V> values() {
		return valToKey.keySet();
	}

	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		return keyToVal.entrySet();
	}
	
	
	
}
