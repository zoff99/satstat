package com.vonglasow.michael.satstat.utils;

import android.location.GnssStatus;
import java.util.ArrayList;
import java.util.List;

public class SatInfo {
    private final int prn;
    private final int constellationType;
    private final float snr;
    private final float elevation;
    private final float azimuth;
    private final boolean usedInFix;
    private final boolean hasAlmanac;
    private final boolean hasEphemeris;

    public SatInfo(GnssStatus status, int index) {
        this.constellationType = status.getConstellationType(index);
        this.prn = toLegacyPrn(this.constellationType, status.getSvid(index));
        this.snr = status.getCn0DbHz(index);
        this.elevation = status.getElevationDegrees(index);
        this.azimuth = status.getAzimuthDegrees(index);
        this.usedInFix = status.usedInFix(index);
        this.hasAlmanac = status.hasAlmanacData(index);
        this.hasEphemeris = status.hasEphemerisData(index);
    }

    // Maps GnssStatus SVID + constellation to legacy NMEA PRN numbers expected by GpsSnrView
    private static int toLegacyPrn(int constellation, int svid) {
        switch (constellation) {
            case GnssStatus.CONSTELLATION_GPS:
                return svid; // 1-32
            case GnssStatus.CONSTELLATION_SBAS:
                return svid - 87; // NMEA 33-64 (SVID 120-151)
            case GnssStatus.CONSTELLATION_GLONASS:
                return svid + 64; // NMEA 65-96
            case GnssStatus.CONSTELLATION_QZSS:
                return svid; // 193-200
            case GnssStatus.CONSTELLATION_BEIDOU:
                return svid + 200; // NMEA 201-235
            case GnssStatus.CONSTELLATION_GALILEO:
                return svid + 300; // 301-336
            case GnssStatus.CONSTELLATION_IRNSS:
                return svid + 400; // 401-414 (IRNSS/NavIC, 14 satellites)
            default:
                return svid;
        }
    }

    public static List<SatInfo> fromGnssStatus(GnssStatus status) {
        List<SatInfo> list = new ArrayList<>();
        if (status != null) {
            for (int i = 0; i < status.getSatelliteCount(); i++) {
                list.add(new SatInfo(status, i));
            }
        }
        return list;
    }

    public int getPrn() { return prn; }
    public int getConstellationType() { return constellationType; }
    public float getSnr() { return snr; }
    public float getElevation() { return elevation; }
    public float getAzimuth() { return azimuth; }
    public boolean usedInFix() { return usedInFix; }
    public boolean hasAlmanac() { return hasAlmanac; }
    public boolean hasEphemeris() { return hasEphemeris; }
}
