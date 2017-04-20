package cn.qiditu.gpsexample;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.RequiresPermission;
import android.util.Log;

import java.util.Iterator;

import cn.qiditu.property.ReadProperty;
import cn.qiditu.property.ReadWriteProperty;
import cn.qiditu.signalslot.signals.Signal0;
import cn.qiditu.signalslot.slots.Slot0;
import cn.qiditu.signalslot.slots.Slot1;
import cn.qiditu.utility.Lazy;
import cn.qiditu.utility.Timer;

public class LocationRecordService extends Service {

    private final ReadWriteProperty<Float> lastNotifyDistance = new ReadWriteProperty<>(0f);
    private static final float notifyDistance = 3000f;

    public LocationRecordService() {
        timer.interval.set(2000);
        timer.singleShot.set(true);
        timer.timeOut.connect(new Slot0() {
            @Override
            public void accept() {
                LocationRecordService.this.stop();
            }
        });
        timer.timeOut.connect(gpsLocationTimeOut);
        distance.changed().connect(new Slot1<Float>() {
            @Override
            public void accept(@Nullable Float aFloat) {
                final float value = aFloat == null ? 0f : aFloat;
                Float lastNotifyDistance = LocationRecordService.this.lastNotifyDistance.get();
                final float lastDistance = lastNotifyDistance == null ? 0f : lastNotifyDistance;
                if((int)(value / LocationRecordService.notifyDistance)
                        > (int)(lastDistance / LocationRecordService.notifyDistance)) {
                    LocationRecordService.this.lastNotifyDistance.set(value);
                    LocationRecordService.this.notifyDistance();
                }
            }
        });
    }

    private final Lazy<NotificationManager> notificationManagerLazy =
            new Lazy<>(new Lazy.LazyFunc<NotificationManager>() {
                @NonNull
                @Override
                public NotificationManager init() {
                    return (NotificationManager)LocationRecordService.this
                            .getSystemService(NOTIFICATION_SERVICE);
                }
            });

    @SuppressWarnings("deprecation")
    private void notifyDistance() {
        Notification.Builder builder = new Notification.Builder(this);
        builder.setWhen(System.currentTimeMillis());
        builder.setSmallIcon(R.mipmap.ic_launcher);
        Intent intent = new Intent(this.getApplicationContext(), MainActivity.class);
        builder.setContentIntent(
                    PendingIntent.getActivity(this.getApplicationContext(), 0, intent, 0));
        builder.setContentText(this.getString(R.string.movingDistanceMoreThanThreeKM));
        builder.setAutoCancel(true);
        Notification notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            notification = builder.build();
        } else {
            notification = builder.getNotification();
        }
        notificationManagerLazy.get().notify(0, notification);
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

    private final ReadWriteProperty<Boolean> isRunning = new ReadWriteProperty<>(false);
    @NonNull
    public ReadProperty<Boolean> isRunning() {
        return isRunning;
    }

    private final ReadWriteProperty<Float> distance = new ReadWriteProperty<>(0f);
    @NonNull
    public ReadProperty<Float> distance() {
        return distance;
    }

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
        distance.set(0f);
        gpsSatellitesNumber.set(0);
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
        isRunning.set(true);
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
        Boolean value = timer.active().get();
        if(value == null ? false : value) {
            timer.stop();
        }
        isRunning.set(false);
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
                distance.set(newValue);
            }
        } else {
            Log.i("GPS", "无法获取地理信息");
        }
    }

    private final ReadWriteProperty<Integer> gpsSatellitesNumber = new ReadWriteProperty<>(0);
    @NonNull
    public ReadProperty<Integer> gpsSatellitesNumber() {
        return gpsSatellitesNumber;
    }

    private Lazy<GnssStatus.Callback> gnssStatusCallback =
            new Lazy<>(new Lazy.LazyFunc<GnssStatus.Callback>() {
        @RequiresApi(api = Build.VERSION_CODES.N)
        @NonNull
        @Override
        public GnssStatus.Callback init() {
            return new GnssStatus.Callback() {
                @Override
                public void onSatelliteStatusChanged(GnssStatus status) {
                    int count = status.getSatelliteCount();
                    Log.i("GnssStatusCallback", String.valueOf(count));
                    LocationRecordService.this.gpsSatellitesNumber.set(count);
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
            LocationManager manager =
                    ((LocationManager)LocationRecordService.this.getSystemService(
                                                                    Context.LOCATION_SERVICE));
            GpsStatus status = manager.getGpsStatus(null);
            Iterator<GpsSatellite> i = status.getSatellites().iterator();
            int count = 0;
            while(i.hasNext()) {
                count++;
                i.next();
            }
            Log.i("GnssStatusCallback", String.valueOf(count));
            LocationRecordService.this.gpsSatellitesNumber.set(count);
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
