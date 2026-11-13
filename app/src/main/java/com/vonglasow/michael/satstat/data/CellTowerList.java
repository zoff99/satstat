package com.vonglasow.michael.satstat.data;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

public abstract class CellTowerList<T extends CellTower> extends TreeSet<T> {
	/**
	 * Returns all entries in the list.
	 * <p>
	 * This method returns all entries in the list, with duplicates eliminated.
	 * It is preferred over {@link #values()}, which may return duplicates.
	 * @return
	 */
	public Set<T> getAll() {
		Set<T> result = new TreeSet<T>(this);
		return result;
	}
	
	/**
	 * Removes cells of the specified source.
	 * <p>
	 * This method clears the flags corresponding to {@code source} in the
	 * internal source field of all entries, and removes entries whose source
	 * field is null. Call this method prior to adding new data from a source,
	 * to tell the list that any cell information previously supplied by this
	 * source is no longer current.
	 * @param source Any combination of 
	 * {@link com.michael.vonglasow.satstat.data.CellTower#SOURCE_CELL_LOCATION},
	 * {@link com.michael.vonglasow.satstat.data.CellTower#SOURCE_NEIGHBORING_CELL_INFO}
	 * or {@link com.michael.vonglasow.satstat.data.CellTower#SOURCE_CELL_INFO}.
	 */
	public void removeSource(int source) {
		TreeSet<CellTower> toDelete = new TreeSet<CellTower>();
		Iterator<T> iterator = this.iterator();
		while (iterator.hasNext()) {
			CellTower ct = iterator.next();
			if (ct.source == source)
				iterator.remove();
		}
	}
}
