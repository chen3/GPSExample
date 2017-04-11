package cn.qiditu.gpsexample;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.GnssStatus;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.RequiresPermission;
import android.util.Log;

import java.util.Iterator;

import cn.qiditu.property.Property;
import cn.qiditu.property.WriteProperty;
import cn.qiditu.signalslot.signals.Signal0;
import cn.qiditu.signalslot.slots.Slot0;
import cn.qiditu.utility.Lazy;
import cn.qiditu.utility.Timer;

public class LocationRecordService extends Service {

    public LocationRecordService() {
        timer.writeInterval.set(2000);
        timer.writeSingleShot.set(true);
        timer.timeOut.connect(new Slot0() {
            @Override
            public void accept() {
                LocationRecordService.this.stop();
            }
        });
        timer.timeOut.connect(gpsLocationTimeOut);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @SuppressWarnings("WeakerAccess")
    public class LocalBinder extends Binder {
        @SuppressWarnings("WeakerAccess")
        public LocationRecordService getService() {
            return LocationRecordService.this;
        }
    }

    private final WriteProperty<Boolean> writeIsRunning = new WriteProperty<>();
    public final Property<Boolean> isRunning = new Property<>(writeIsRunning, false);

    private final WriteProperty<Float> writeDistance = new WriteProperty<>();
    public final Property<Float> distance = new Property<>(writeDistance, 0f);

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void startWithNotTimeOut() {
        start(false);
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void start() {
        start(true);
    }

    public final Signal0 gpsLocationTimeOut = new Signal0(this);
    private Timer timer = new Timer();

    private Location lastLocation;

    @SuppressWarnings({"MissingPermission", "deprecation"})
    private void start(boolean timeOut) {
        // 获取位置管理服务
        LocationManager locationManager =
                (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
        // 获取到GPS_PROVIDER
        lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        writeDistance.set(0f);
        writeGpsSatellitesNumber.set(0);
        updateLocation(lastLocation);
        // 设置监听器，自动更新的最小时间为间隔N秒(1秒为1*1000，这样写主要为了方便)或最小位移变化超过N米
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0,
                locationListener);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locationManager.registerGnssStatusCallback(gnssStatusCallback.get());
        }
        else {
            locationManager.addGpsStatusListener(gpsStatusListener);
        }
        writeIsRunning.set(true);
        if(timeOut) {
            timer.start();
        }
    }

    @SuppressWarnings("deprecation")
    public void stop() {
        // 获取位置管理服务
        LocationManager locationManager =
                (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(locationListener);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locationManager.unregisterGnssStatusCallback(gnssStatusCallback.get());
        }
        else {
            locationManager.removeGpsStatusListener(gpsStatusListener);
        }
        Boolean value = timer.active.get();
        if(value == null ? false : value) {
            timer.stop();
        }
        writeIsRunning.set(false);
    }

    private void updateLocation(@Nullable Location location) {
        if (location != null) {
            if(lastLocation == null) {
                lastLocation = location;
            }
            else {
                float[] result = new float[1];
                Location.distanceBetween(lastLocation.getLatitude(), lastLocation.getLongitude(),
                        location.getLatitude(), location.getLongitude(),
                        result);
                Float value = distance.get();
                float newValue = value == null ? 0 : value;
                newValue += result[0];
                lastLocation = location;
                writeDistance.set(newValue);
            }
        } else {
            Log.i("GPS", "无法获取地理信息");
        }
    }

    private final WriteProperty<Integer> writeGpsSatellitesNumber = new WriteProperty<>();
    public final Property<Integer> gpsSatellitesNumber =
                                        new Property<>(writeGpsSatellitesNumber, 0);

    private Lazy<GnssStatus.Callback> gnssStatusCallback =
            new Lazy<>(new Lazy.LazyFunc<GnssStatus.Callback>() {
        @RequiresApi(api = Build.VERSION_CODES.N)
        @Nullable
        @Override
        public GnssStatus.Callback init() {
            return new GnssStatus.Callback() {
                @Override
                public void onSatelliteStatusChanged(GnssStatus status) {
                    int count = status.getSatelliteCount();
                    Log.i("GnssStatusCallback", String.valueOf(count));
                    writeGpsSatellitesNumber.set(count);
                }
            };
        }
    });

    @SuppressWarnings("deprecation")
    private GpsStatus.Listener gpsStatusListener = new GpsStatus.Listener() {
        @SuppressWarnings("MissingPermission")
        @Override
        public void onGpsStatusChanged(int event) {
            if(event != GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
                return;
            }
            GpsStatus status = ((LocationManager)LocationRecordService.this.getSystemService(Context.LOCATION_SERVICE)).getGpsStatus(null);
            Iterator<GpsSatellite> i = status.getSatellites().iterator();
            int count = 0;
            while(i.hasNext()) {
                count++;
                i.next();
            }
            Log.i("GnssStatusCallback", String.valueOf(count));
            writeGpsSatellitesNumber.set(count);
        }
    };

    private LocationListener locationListener = new LocationListener() {
        /**
         * 位置信息变化时触发
         */
        public void onLocationChanged(Location location) {
            Log.i("GPS", "LocationChanged");
            updateLocation(location);
        }

        /**
         * GPS状态变化时触发
         */
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.i("GPS", "StatusChanged");
        }

        /**
         * GPS开启时触发
         */
        @SuppressWarnings("MissingPermission")
        public void onProviderEnabled(String provider) {
            // 获取位置管理服务
            LocationManager locationManager =
                                (LocationManager)LocationRecordService.this.
                                                    getSystemService(Context.LOCATION_SERVICE);
            //获取到GPS_PROVIDER
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            updateLocation(location);
        }

        /**
         * GPS禁用时触发
         */
        public void onProviderDisabled(String provider) {
            updateLocation(null);
        }
    };

}
