package cn.qiditu.gpsexample;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.text.DecimalFormat;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnNeverAskAgain;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class MainActivity extends AppCompatActivity {

    @BindView(R.id.displayDistance)
    TextView tvDisplayDistance;
    @BindView(R.id.root_layout)
    ViewGroup rootLayout;
    @BindView(R.id.btn_start)
    Button btnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        notLocationServiceDialog = new AlertDialog.Builder(this)
                .setPositiveButton(R.string.goto_system_setting,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(@NonNull DialogInterface dialog, int which) {
                                MainActivity.this.startActivity(
                                        new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .setCancelable(false)
                .setMessage(R.string.not_location_service);

        checkLocationService();
    }

    private boolean isRunning = false;
    @OnClick(R.id.btn_start)
    void onBtnStartLocationClicked() {
        if(isRunning) {
            LocationManager locationManager =
                    (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            locationManager.removeUpdates(locationListener);
            btnStart.setText(R.string.start);
            isRunning = false;
        }
        else {
            MainActivityPermissionsDispatcher.getLocationWithCheck(this);
        }
    }

    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    @SuppressWarnings("MissingPermission")
    void getLocation() {
        isRunning = true;
        btnStart.setText(R.string.stop);
        // 获取位置管理服务
        LocationManager locationManager =
                (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        // 获取到GPS_PROVIDER
        lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        distance = 0;
        updateLocation(lastLocation);
        // 设置监听器，自动更新的最小时间为间隔N秒(1秒为1*1000，这样写主要为了方便)或最小位移变化超过N米
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0,
                locationListener);
    }

    private LocationListener locationListener = new LocationListener() {
        /**
         * 位置信息变化时触发
         */
        public void onLocationChanged(Location location) {
            MainActivity.this.updateLocation(location);
        }

        /**
         * GPS状态变化时触发
         */
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        /**
         * GPS开启时触发
         */
        @SuppressWarnings("MissingPermission")
        public void onProviderEnabled(String provider) {
            // 获取位置管理服务
            LocationManager locationManager =
                    (LocationManager)MainActivity.this.getSystemService(Context.LOCATION_SERVICE);
            //获取到GPS_PROVIDER
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            MainActivity.this.updateLocation(location);
        }

        /**
         * GPS禁用时触发
         */
        public void onProviderDisabled(String provider) {
            MainActivity.this.updateLocation(null);
        }
    };

    @OnNeverAskAgain(Manifest.permission.ACCESS_FINE_LOCATION)
    void onGetLocationNeverAskAgain() {
        Snackbar.make(rootLayout, R.string.get_permission_fail, Snackbar.LENGTH_LONG)
                .setAction(R.string.goto_system_setting, gotoSetting)
                .show();
    }

    private View.OnClickListener gotoSetting = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent localIntent = new Intent();
            localIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            localIntent.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
            Uri uri = Uri.fromParts("package", MainActivity.this.getPackageName(), null);
            localIntent.setData(uri);
            MainActivity.this.startActivity(localIntent);
        }
    };

    @OnShowRationale(Manifest.permission.ACCESS_FINE_LOCATION)
    void showRationaleForLocation(@NonNull final PermissionRequest request) {
        showRationaleDialog(R.string.permission_location_rationale, request);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // NOTE: delegate the permission handling to generated method
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode,
                grantResults);
    }

    private void showRationaleDialog(@StringRes int messageResId,
                                     @NonNull final PermissionRequest request) {
        new AlertDialog.Builder(this)
                .setPositiveButton(R.string.allow, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(@NonNull DialogInterface dialog, int which) {
                        request.proceed();
                    }
                })
                .setNegativeButton("拒绝", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(@NonNull DialogInterface dialog, int which) {
                        request.cancel();
                    }
                })
                .setCancelable(false)
                .setMessage(messageResId)
                .show();
    }

    private void checkLocationService() {
        LocationManager locationManager =
                (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
        if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            notLocationServiceDialog.show();
        }
        else {
            btnStart.setEnabled(true);
        }
    }

    private AlertDialog.Builder notLocationServiceDialog;

    private Location lastLocation;
    private float distance;
    private static DecimalFormat distanceFormat = new DecimalFormat("0.00");
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
                distance += result[0];
                lastLocation = location;
                tvDisplayDistance.setText(distanceFormat.format(distance));
            }
        } else {
            Log.i("GPS", "无法获取地理信息");
//            tvDisplayDistance.setText("无法获取地理信息");
        }
    }

}
