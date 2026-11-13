package com.vonglasow.michael.satstat.data;

import android.telephony.TelephonyManager;
import android.util.Log;

public abstract class CellTower implements Comparable {
	public static final int SOURCE_CELL_LOCATION = 1;
	public static final int SOURCE_NEIGHBORING_CELL_INFO = 2;
	public static final int SOURCE_CELL_INFO = 4;
	public static final int UNKNOWN = -1;
	public static final int DBM_UNKNOWN = 85; // 99 is unknown ASU, hence 99 * 2 - 113
	
	protected int dbm = DBM_UNKNOWN;
	protected int generation = 0;
	protected boolean serving = false;
	protected int source = 0;

	/**
	 * Compares this object with the specified object for order.
	 * 
	 * Note: this class has a natural ordering that is inconsistent with equals. If {@code that} is not a
	 * {@code CellTower}, the result is 0. Otherwise, only a minimal comparison is done, with serving cells
	 * being sorted before others, otherwise cells being sorted by generation (later ones first). No further
	 * comparisons are done and subclasses should override this method to refine the comparison.
	 * 
	 * @param thatTower The other instance to compare to.
	 * @return A negative value if {@code this < that}, a positive value if {@code this > that}, zero otherwise.
	 */
	public int compareTo(Object that) {
		CellTower thatTower = null;
		if (that instanceof CellTower)
			thatTower = (CellTower) that;
		else
			return 0;
		int res = 0;
		if (this.isServing())
			res--;
		if (thatTower.isServing())
			res++;
		if (res != 0)
			return res;
		res = -compareInts(this.generation, thatTower.generation);
		return res;
	}

	/**
	 * Returns the alternate cell identity in text form.
	 * <p>
	 * The alternate cell identity is an alternate identifier, apart from the
	 * globally unique cell identifier, which can be used to identify the cell.
	 * <p>
	 * Subclasses for network families that use alternate identifiers must
	 * override this method to provide a string in the following form:
	 * <p>
	 * {@code network:text-id[-id]*}
	 * <p>
	 * {@code network} is a string which uniquely identifies the network family.
	 * It is followed by a colon and a {@code text} which marks the identifier
	 * as an alternate identifier, a dash and a sequence of {@code id}s in
	 * hierarchical order (top to bottom), separated by dashes. Leading zeroes
	 * are stripped from {@code id}s. The {@code id} structure is specific to
	 * the network family.
	 * <p>
	 * Network families that do not use alternate identifiers should inherit
	 * the default implementation, which returns {@code null}. 
	 */
	public String getAltText() {
		return null;
	}

	public int getDbm() {
		return dbm;
	}

	public int getGeneration() {
		return generation;
	}
	
	/**
	 * Returns the network generation of a phone network type.
	 * @param networkType The network type as returned by {@link TelephonyManager.getNetworkType}
     * @return 2, 3 or 4 for 2G, 3G or 4G; 0 for unknown
	 */
	public static int getGenerationFromNetworkType(int networkType) {
		switch (networkType) {
		case TelephonyManager.NETWORK_TYPE_CDMA:
		case TelephonyManager.NETWORK_TYPE_EDGE:
		case TelephonyManager.NETWORK_TYPE_GPRS:
		case TelephonyManager.NETWORK_TYPE_IDEN:
			return 2;
		case TelephonyManager.NETWORK_TYPE_1xRTT:
		case TelephonyManager.NETWORK_TYPE_EHRPD:
		case TelephonyManager.NETWORK_TYPE_EVDO_0:
		case TelephonyManager.NETWORK_TYPE_EVDO_A:
		case TelephonyManager.NETWORK_TYPE_EVDO_B:
		case TelephonyManager.NETWORK_TYPE_HSDPA:
		case TelephonyManager.NETWORK_TYPE_HSPA:
		case TelephonyManager.NETWORK_TYPE_HSPAP:
		case TelephonyManager.NETWORK_TYPE_HSUPA:
		case TelephonyManager.NETWORK_TYPE_UMTS:
			return 3;
		case TelephonyManager.NETWORK_TYPE_LTE:
			return 4;
		default:
			return 0;
		}
	}

	/**
	 * Returns the source from which the cell was obtained.
	 * @return
	 */
	public int getSource() {
		return source;
	}

	/**
	 * Returns the cell identity in text form.
	 * <p>
	 * Subclasses must override this method to provide a string in the following form:
	 * <p>
	 * {@code network:id[-id]*}
	 * <p>
	 * {@code network} is a string which uniquely identifies the network family.
	 * It is followed by a colon and a sequence of {@code id}s in hierarchical
	 * order (top to bottom), separated by dashes. Leading zeroes are stripped
	 * from {@code id}s. The {@code id} structure is specific to the network 
	 * family. 
	 */
	public abstract String getText();
	
	/**
	 * Whether the cell was included in the last update from any of the sources.
	 * <p>
	 * When an update is received from a source, cells that were received in an
	 * earlier update from the same source have the flag for that source reset
	 * but are still kept in the list until the next update. Such cells should
	 * be considered stale and not be displayed in any list of active cells. 
	 * @return {@code true} if the cell has its flag for at least one source set, {@code false} if not
	 */
	public boolean hasSource() {
		return (source >= 0);
	}

	public boolean isCellInfo() {
		return ((source & SOURCE_CELL_INFO) == SOURCE_CELL_INFO);
	}
	
	public boolean isCellLocation() {
		return ((source & SOURCE_CELL_LOCATION) == SOURCE_CELL_LOCATION);
	}
	
	public boolean isNeighboringCellInfo() {
		return ((source & SOURCE_NEIGHBORING_CELL_INFO) == SOURCE_NEIGHBORING_CELL_INFO);
	}
	
	/**
	 * Whether the device is currently registered with this cell.
	 * <p>
	 * If the cell was updated through a {@link android.telephony.CellLocation},
	 * this method will always return {@code true}.
	 */
	public boolean isServing() {
		return (serving || ((this.source & SOURCE_CELL_LOCATION) != 0));
	}
	
	/**
	 * Determines a "loose match" for two parts of a cell ID.
	 * 
	 * A "loose match" will return true if one of its two arguments is {@link #UNKNOWN}, or if both
	 * arguments are truly equal.
	 * 
	 * Any part of a cell identification (e.g. MCC, MNC, any area ID, cell ID, scrambling code) can
	 * be compared in this manner as long as it assigns a value of {@link #UNKNOWN} to values which
	 * are not known, and only to those.
	 * 
	 * @param l
	 * @param r
	 * @return True for a match, false otherwise
	 */
	public static boolean matches(int l, int r) {
		if ((l == UNKNOWN) || (r == UNKNOWN))
			return true;
		return (l == r);
	}

	public void setCellInfo(boolean value) {
		if (value)
			this.source = this.source | SOURCE_CELL_INFO;
		else
			this.source = this.source & ~SOURCE_CELL_INFO;
	}

	public void setCellLocation(boolean value) {
		if (value)
			this.source = this.source | SOURCE_CELL_LOCATION;
		else
			this.source = this.source & ~SOURCE_CELL_LOCATION;
	}

	public void setDbm(int dbm) {
		this.dbm = dbm;
	}

	public void setGeneration(int generation) {
		if (this instanceof CellTowerLte)
			Log.d(this.getClass().getSimpleName(), String.format("Setting network type to %d for cell %s (%s)", generation, this.getText(), this.getAltText()));
		this.generation = generation;
	}

	public void setNeighboringCellInfo(boolean value) {
		if (value)
			this.source = this.source | SOURCE_NEIGHBORING_CELL_INFO;
		else
			this.source = this.source & ~SOURCE_NEIGHBORING_CELL_INFO;
	}

    /**
     * Sets the network generation based on the phone network type.
     * <p>
     * The value set here cannot be retrieved directly, but subsequent calls to
     * {@link #getGeneration()} will return the corresponding generation.
     * @param networkType The network type as returned by {@link TelephonyManager.getNetworkType}
     */
	public void setNetworkType(int networkType) {
		if (this instanceof CellTowerLte)
			Log.d(this.getClass().getSimpleName(), String.format("Changing network type for cell %s (%s)", this.getText(), this.getAltText()));
		this.generation = getGenerationFromNetworkType(networkType);
	}
	
	public void setServing(boolean serving) {
		this.serving = serving;
	}

	/**
	 * Compares two integer values for sorting, placing {@link #UNKNOWN} last.
	 * @param lhs
	 * @param rhs
	 * @return A negative value if {@code lhs < rhs}, a positive value if {@code lhs > rhs}, zero otherwise.
	 */
	protected static int compareInts(int lhs, int rhs) {
		if (lhs == rhs)
			return 0;
		if (lhs == CellTower.UNKNOWN)
			return 1;
		if (rhs == CellTower.UNKNOWN)
			return -1;
		return Integer.compare(lhs, rhs);
	}
}
