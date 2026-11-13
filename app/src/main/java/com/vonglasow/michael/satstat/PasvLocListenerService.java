/*
 * Copyright © 2013–2016 Michael von Glasow.
 *
 * This file is part of LSRN Tools.
 *
 * LSRN Tools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSRN Tools is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSRN Tools.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.vonglasow.michael.satstat;

import com.vonglasow.michael.satstat.ui.MainActivity;
import com.vonglasow.michael.satstat.utils.PermissionHelper;

import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.MGRSRef;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback;
import androidx.core.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import com.vonglasow.michael.satstat.utils.SatInfo;

import android.content.pm.ServiceInfo;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;
import com.hzi.UTM;

public class PasvLocListenerService extends Service implements LocationListener, OnSharedPreferenceChangeListener, OnRequestPermissionsResultCallback {

	public static final String TAG = "PasvLocListenerService";

	// The unique ID for the notification
	private static final int ONGOING_NOTIFICATION = 1;

	// The notification channel ID (required on Android 8+)
	private static final String CHANNEL_ID = "gps_status";

	// GPS status values
	private static final int GPS_INACTIVE = 0;
	private static final int GPS_SEARCH = 1;
	private static final int GPS_FIX = 2;

	private int mStatus = GPS_INACTIVE;

	private boolean prefUnitType = true;
	private boolean prefKnots = false;
	private int prefCoord = Const.KEY_PREF_COORD_DECIMAL;
	private boolean mNotifyFix = false;
	private boolean mNotifySearch = false;

	private LocationManager mLocationManager;
	private NotificationCompat.Builder mBuilder;
	private SharedPreferences mSharedPreferences;
	private BroadcastReceiver mGpsStatusReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context c, Intent intent) {
			if (intent == null) return;
			if (Const.GPS_ENABLED_CHANGE.equals(intent.getAction()) && !intent.getBooleanExtra("enabled", true)) {
				// GPS_ENABLED_CHANGE, enabled=false: GPS disabled, dismiss notification
				mStatus = GPS_INACTIVE;
				stopForeground(true);
			} else if (Const.GPS_FIX_CHANGE.equals(intent.getAction()) && intent.getBooleanExtra("enabled", false)) {
				// GPS_FIX_CHANGE, enabled=true: GPS got fix, will be taken care of in onLocationChanged
				mStatus = GPS_FIX;
			} else if (intent.getAction() != null) {
				// GPS_ENABLED_CHANGE, enabled=true: GPS enabled
				// GPS_FIX_CHANGE, enabled=false: GPS lost fix
				mStatus = GPS_SEARCH;
				showStatusNoLocation();
			} else {
				// explicit start without an action: derive initial state from the provider
				mStatus = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ? GPS_SEARCH : GPS_INACTIVE;
				if (mStatus == GPS_SEARCH) showStatusNoLocation();
			}
		}
	};

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	@SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
	public void onCreate() {
		super.onCreate(); //do we need that here?

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
																  getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
			channel.setShowBadge(false);
			((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
		}

		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
		mNotifyFix = mSharedPreferences.getBoolean(Const.KEY_PREF_NOTIFY_FIX, mNotifyFix);
		mNotifySearch = mSharedPreferences.getBoolean(Const.KEY_PREF_NOTIFY_SEARCH, mNotifySearch);
		if (mNotifyFix || mNotifySearch)
			requestPermissions();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			registerReceiver(mGpsStatusReceiver, new IntentFilter(Const.GPS_ENABLED_CHANGE), Context.RECEIVER_NOT_EXPORTED);
			registerReceiver(mGpsStatusReceiver, new IntentFilter(Const.GPS_FIX_CHANGE), Context.RECEIVER_NOT_EXPORTED);
		} else {
			registerReceiver(mGpsStatusReceiver, new IntentFilter(Const.GPS_ENABLED_CHANGE));
			registerReceiver(mGpsStatusReceiver, new IntentFilter(Const.GPS_FIX_CHANGE));
		}
	}

	@Override
	public void onDestroy() {
		stopForeground(true);
		unregisterReceiver(mGpsStatusReceiver);
		mLocationManager.removeUpdates(this);
		mLocationManager.unregisterGnssStatusCallback(mGnssStatusCallback);
		mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
	}

	private GnssStatus mLastGnssStatus = null;
	private int mTtff = 0;

	private final GnssStatus.Callback mGnssStatusCallback = new GnssStatus.Callback() {
		@Override
		public void onFirstFix(int ttffMillis) {
			mTtff = ttffMillis;
		}

		@Override
		public void onSatelliteStatusChanged(GnssStatus status) {
			mLastGnssStatus = status;
			if (mNotifySearch && (mStatus != GPS_INACTIVE) && (mStatus != GPS_FIX)) {
				showStatusNoLocation();
			}
		}
	};

	@Override
	public void onLocationChanged(Location location) {
		if (!location.getProvider().equals(LocationManager.GPS_PROVIDER)) return;
		if (mNotifyFix && (mStatus != GPS_INACTIVE)) {
			mStatus = GPS_FIX;
			int satsInView = 0;
			int satsUsed = 0;
			Iterable<SatInfo> sats = SatInfo.fromGnssStatus(mLastGnssStatus);
			for (SatInfo sat : sats) {
				satsInView++;
				if (sat.usedInFix()) {
					satsUsed++;
				}
			}
			double lat = Math.abs(location.getLatitude());
			double lon = Math.abs(location.getLongitude());
			String ns = (location.getLatitude() > 0)?
					getString(R.string.value_N):
					(location.getLatitude() < 0)?
							getString(R.string.value_S):"";
			String ew = (location.getLongitude() > 0)?
					getString(R.string.value_E):
					(location.getLongitude() < 0)?
							getString(R.string.value_W):"";
			String title = "";
			if (prefCoord == Const.KEY_PREF_COORD_DECIMAL) {
				title = String.format("%.5f%s%s %.5f%s%s",
									  lat, getString(R.string.unit_degree), ns,
									  lon, getString(R.string.unit_degree), ew);
			} else if (prefCoord == Const.KEY_PREF_COORD_MIN) {
				double decY = lat;
				double degY = (int) decY;
				double minY = Math.abs(60.0 * (decY - degY));
				double decX = lon;
				double degX = (int) decX;
				double minX = Math.abs(60.0 * (decX - degX));
				title = String.format("%.0f%s %.3f' %s %.0f%s %.3f' %s",
									  degY, getString(R.string.unit_degree), minY + /*rounding*/ 0.0005, ns,
									  degX, getString(R.string.unit_degree), minX + /*rounding*/ 0.0005, ew);
			} else if (prefCoord == Const.KEY_PREF_COORD_SEC) {
				double decY = lat;
				double degY = (int) decY;
				double tmp = 60.0 * (decY - degY);
				double minY = (int) Math.abs(tmp);
				double secY = Math.abs(60.0 * (tmp - minY));
				double decX = lon;
				double degX = (int) decX;
				tmp = 60.0 * (decX - degX);
				double minX = (int) Math.abs(tmp);
				double secX = Math.abs(60.0 * (tmp - minX));
				title = String.format("%.0f%s %.0f' %.1f\" %s %.0f%s %.0f' %.1f\" %s",
									  degY, getString(R.string.unit_degree), minY, secY + /*rounding*/ 0.05, ns,
									  degX, getString(R.string.unit_degree), minX, secX + /*rounding*/ 0.05, ew);
			} else if (prefCoord == Const.KEY_PREF_COORD_MGRS) {
				title = new LatLng(location.getLatitude(), location.getLongitude()).toMGRSRef().toString(MGRSRef.PRECISION_1M);
			} else if (prefCoord == Const.KEY_PREF_COORD_UTM) {
				title = UTM.lat_lon_to_utm(location.getLatitude(), location.getLongitude(), this.getApplicationContext());
			}

			String text = "";
			if (location.hasAltitude()) {
				text = text + String.format("%.0f%s",
											(location.getAltitude() * (prefUnitType ? 1 : 3.28084)),
											getString(((prefUnitType) ? R.string.unit_meter : R.string.unit_feet)));
			}
			if (location.hasSpeed()) {
				text = text + (text.equals("")?"":", ") + String.format("%.0f%s",
																		(location.getSpeed() * (prefKnots ? 1.943844 : prefUnitType ? 3.6 : 2.23694)),
																		getString(((prefKnots) ? R.string.unit_kn : (prefUnitType) ? R.string.unit_km_h : R.string.unit_mph)));
			}
			if (location.hasAccuracy()) {
				text = text + (text.equals("")?"":", ") + String.format("\u03b5 = %.0f%s",
																		(location.getAccuracy() * (prefUnitType ? 1 : 3.28084)),
																		getString(((prefUnitType) ? R.string.unit_meter : R.string.unit_feet)));
			}
			text = text + (text.equals("")?"":", ") + String.format("%d/%d",
																	satsUsed,
																	satsInView);
			text = text + (text.equals("")?"":",\n") + String.format("TTFF %d s",
																	 mTtff / 1000);
			mBuilder.setSmallIcon(R.drawable.ic_stat_notify_location);
			mBuilder.setContentTitle(title);
			mBuilder.setContentText(text);
			mBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(text));

			startForegroundSafely(mBuilder.build());
		} else {
			stopForeground(true);
		}
	}


	@Override
	public void onProviderDisabled(String provider) {
		// nop

	}

	@Override
	public void onProviderEnabled(String provider) {
		// nop

	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		boolean isGranted = false;
		for (int i = 0; i < grantResults.length; i++)
			if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION) && (grantResults[i] == PackageManager.PERMISSION_GRANTED))
				isGranted = true;
		if (isGranted) {
			requestLocationUpdates();
			if (mNotifySearch && (mStatus != GPS_INACTIVE))
				showStatusNoLocation();
		}
		else
			Log.w("PasvLocListenerService", "ACCESS_FINE_LOCATION permission not granted. Location notifications will not be available.");
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
										  String key) {
		if (key.equals(Const.KEY_PREF_NOTIFY_FIX) || key.equals(Const.KEY_PREF_NOTIFY_SEARCH)) {
			mNotifyFix = sharedPreferences.getBoolean(Const.KEY_PREF_NOTIFY_FIX, mNotifyFix);
			mNotifySearch = sharedPreferences.getBoolean(Const.KEY_PREF_NOTIFY_SEARCH, mNotifySearch);
			if (!(mNotifyFix || mNotifySearch)) {
				stopSelf();
			} else
				requestPermissions();
		} else if (key.equals(Const.KEY_PREF_UNIT_TYPE)) {
			prefUnitType = sharedPreferences.getBoolean(Const.KEY_PREF_UNIT_TYPE, prefUnitType);
		} else if (key.equals(Const.KEY_PREF_KNOTS)) {
			prefKnots = sharedPreferences.getBoolean(Const.KEY_PREF_KNOTS, prefKnots);
		} else if (key.equals(Const.KEY_PREF_COORD)) {
			prefCoord = Integer.valueOf(sharedPreferences.getString(Const.KEY_PREF_COORD, Integer.toString(prefCoord)));
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		prefUnitType = mSharedPreferences.getBoolean(Const.KEY_PREF_UNIT_TYPE, prefUnitType);
		prefKnots = mSharedPreferences.getBoolean(Const.KEY_PREF_KNOTS, prefKnots);
		prefCoord = Integer.valueOf(mSharedPreferences.getString(Const.KEY_PREF_COORD, Integer.toString(prefCoord)));
		mNotifyFix = mSharedPreferences.getBoolean(Const.KEY_PREF_NOTIFY_FIX, mNotifyFix);
		mNotifySearch = mSharedPreferences.getBoolean(Const.KEY_PREF_NOTIFY_SEARCH, mNotifySearch);

		if (mLocationManager.getAllProviders().indexOf(LocationManager.PASSIVE_PROVIDER) >= 0) {
			if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
				requestLocationUpdates();
			else
				Log.w("PasvLocListenerService", "ACCESS_FINE_LOCATION permission not granted. Data display will not be available.");
		} else {
			Log.w("PasvLocListenerService", "No passive location provider found. Data display will not be available.");
		}

		mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
				.setSmallIcon(R.drawable.ic_stat_notify_location)
				.setContentTitle(getString(R.string.value_none))
				.setContentText(getString(R.string.value_none))
				.setWhen(0)
				.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

		Intent mainIntent = new Intent(this, MainActivity.class);

		TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
		stackBuilder.addParentStack(MainActivity.class);
		stackBuilder.addNextIntent(mainIntent);

		PendingIntent mainPendingIntent =
				stackBuilder.getPendingIntent(
						0,
						PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
				);

		mBuilder.setContentIntent(mainPendingIntent);

		// if we were started through a broadcast, mGpsStatusReceiver had
		// no way of picking it up, so we need to forward it manually
		mGpsStatusReceiver.onReceive(this, intent);

		return START_STICKY;
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}

	public void showStatusNoLocation() {
		if (mBuilder == null) return; // not initialized yet (broadcast received before onStartCommand)
		if (mNotifySearch && (mStatus != GPS_INACTIVE)) {
			if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
				mBuilder.setSmallIcon(R.drawable.ic_stat_notify_nolocation);
				mBuilder.setContentTitle(getString(R.string.notify_nolocation_title));
				mBuilder.setContentText(getString(R.string.notify_nolocation_body));
				mBuilder.setStyle(null);

				startForegroundSafely(mBuilder.build());
			} else
				requestPermissions();
		} else {
			stopForeground(true);
		}
	}

	private void requestLocationUpdates() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			Log.w(TAG, "ACCESS_FINE_LOCATION permission not granted. Location notifications will not be available.");
			return;
		}
		try {
			mLocationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 0, 0, this);
			mLocationManager.registerGnssStatusCallback(mGnssStatusCallback, new android.os.Handler(android.os.Looper.getMainLooper()));
		} catch (SecurityException e) {
			// permission may have been revoked between the check and the call
			Log.w(TAG, "Permission not granted for location updates: " + e.getMessage());
		}
	}

	private void requestPermissions() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			Log.i("PasvLocListenerService", "ACCESS_FINE_LOCATION permission not granted, asking for it...");

			// TODO proper notification content
			PermissionHelper.requestPermissions(this,
												new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
												Const.PERM_REQUEST_LOCATION_NOTIFICATION,
												getString(R.string.notify_perm_title),
												getString(R.string.notify_perm_body),
												R.drawable.ic_security);
		}
	}

	private void startForegroundSafely(Notification notification) {
		// Android 14: location-type FGS requires a granted location permission at start time
		boolean hasLocation =
				ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
				== PackageManager.PERMISSION_GRANTED
				|| ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
				   == PackageManager.PERMISSION_GRANTED;

		if (hasLocation) {
			try {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
					startForeground(ONGOING_NOTIFICATION, notification,
									ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
				} else {
					startForeground(ONGOING_NOTIFICATION, notification);
				}
				return;
			} catch (SecurityException | IllegalStateException e) {
				// SecurityException: 14+, while-in-use location not satisfied (background start)
				// IllegalStateException: 12+, ForegroundServiceStartNotAllowedException
				Log.w(TAG, "Location FGS start rejected: " + e.getMessage());
			}
		}

		// Graceful degradation: plain notification, no FGS promotion
		NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.notify(ONGOING_NOTIFICATION, notification);
		// IMPORTANT: if this service was started via startForegroundService(),
		// you MUST stopSelf() here instead — otherwise the 5-second
		// "did not start in time" crash applies.
	}
}
