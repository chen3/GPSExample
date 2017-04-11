package cn.qiditu.gpsexample;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.LocationManager;
import android.net.Uri;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.Locale;

import butterknife.BindString;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import cn.qiditu.property.Property;
import cn.qiditu.property.WriteProperty;
import cn.qiditu.signalslot.slots.Slot0;
import cn.qiditu.signalslot.slots.Slot1;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnNeverAskAgain;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class MainActivity extends AppCompatActivity {

    public MainActivity() {
        super();
        service.changed.connect(new Slot1<LocationRecordService>() {
            @Override
            public void accept(@Nullable LocationRecordService service) {
                if(service == null) {
                    return;
                }

                final Slot1<Float> slotDistance = new Slot1<Float>() {
                    @Override
                    public void accept(@Nullable Float aFloat) {
                        tvDisplayDistance.setText(
                                MainActivity.distanceFormat.format(aFloat == null ? 0 : aFloat));
                    }
                };
                service.distance.changed.connect(slotDistance);
                slotDistance.accept(service.distance.get());

                final Slot1<Boolean> slotIsRunning = new Slot1<Boolean>() {
                    @Override
                    public void accept(@Nullable Boolean aBoolean) {
                        MainActivity.this.updateButtonState(aBoolean);
                    }
                };
                service.isRunning.changed.connect(slotIsRunning);
                slotIsRunning.accept(service.isRunning.get());

                final Slot1<Integer> slotGpsSatellitesNumber = new Slot1<Integer>() {
                    @Override
                    public void accept(@Nullable Integer integer) {
                        final String str = String.format(Locale.getDefault(),
                                MainActivity.this.gpsSatellitesNumberDisplay,
                                integer == null ? 0 : integer);
                        MainActivity.this.gpsSatellitesNumber.setText(str);
                    }
                };
                service.gpsSatellitesNumber.changed.connect(slotGpsSatellitesNumber);
                slotGpsSatellitesNumber.accept(service.gpsSatellitesNumber.get());

                service.gpsLocationTimeOut.connect(new Slot0() {
                    @Override
                    public void accept() {
                        MainActivity.this.locationTimeOutDialog.show();
                    }
                });
            }
        }, 1);
    }

    private AlertDialog.Builder locationTimeOutDialog;

    private static final DecimalFormat distanceFormat = new DecimalFormat("0.00");

    @BindView(R.id.gpsSatellitesNumber)
    TextView gpsSatellitesNumber;
    @BindView(R.id.displayDistance)
    TextView tvDisplayDistance;
    @BindView(R.id.root_layout)
    ViewGroup rootLayout;
    @BindView(R.id.btn_start)
    Button btnStart;
    @BindString(R.string.gpsSatellitesNumberDisplay)
    String gpsSatellitesNumberDisplay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        init();

        this.startService(serviceIntent);
        this.bindService(serviceIntent, conn, 0);
    }

    private Intent serviceIntent;

    private void init() {
        serviceIntent = new Intent(this, LocationRecordService.class);
        notLocationServiceDialog = new AlertDialog.Builder(this)
                .setMessage(R.string.not_location_service)
                .setPositiveButton(R.string.goto_system_setting,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(@NonNull DialogInterface dialog, int which) {
                                MainActivity.this.startActivity(
                                        new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .setCancelable(false);
        serviceNotFound = Snackbar.make(rootLayout, R.string.serviceNotFound, Snackbar.LENGTH_LONG);
        permissionFail =
                Snackbar.make(rootLayout, R.string.get_permission_fail,
                        Snackbar.LENGTH_LONG)
                        .setAction(R.string.goto_system_setting,
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                            Intent localIntent = new Intent();
                            localIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            localIntent.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
                            final String packageName = MainActivity.this.getPackageName();
                            Uri uri = Uri.fromParts("package", packageName, null);
                            localIntent.setData(uri);
                            MainActivity.this.startActivity(localIntent);
                        }
                });
        locationTimeOutDialog = new AlertDialog.Builder(this)
                .setMessage(R.string.gpsLocationServiceTimeOut)
                .setPositiveButton(R.string.startWithNotTimeOut,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(@NonNull DialogInterface dialog, int which) {
                                MainActivityPermissionsDispatcher.
                                        getLocationWithNotTimeOutWithCheck(MainActivity.this);
                            }
                        })
                .setNegativeButton(R.string.ok, null);
    }

    private final WriteProperty<LocationRecordService> writeService = new WriteProperty<>();
    private final Property<LocationRecordService> service = new Property<>(writeService);

    private ServiceConnection conn = new ServiceConnection() {
        /**
         * {@inheritDoc}
         */
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LocationRecordService tService =
                                ((LocationRecordService.LocalBinder)service).getService();
            MainActivity.this.writeService.set(tService);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onServiceDisconnected(ComponentName name) {
            writeService.set(null);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        checkLocationService();
    }

    @Override
    protected void onDestroy() {
        this.unbindService(conn);
        super.onDestroy();
    }

    private void updateButtonState(@Nullable Boolean isRunning) {
        btnStart.setText(isRunning == null ? R.string.stop
                                        : (isRunning ? R.string.stop : R.string.start));
    }

    private Snackbar serviceNotFound;
    @OnClick(R.id.btn_start)
    void onBtnStartLocationClicked() {
        boolean isRunning;
        LocationRecordService tService = service.get();
        if(tService == null) {
            serviceNotFound.show();
            return;
        }
        Boolean value = tService.isRunning.get();
        isRunning = value == null ? false : value;
        if(isRunning) {
            tService.stop();
        }
        else {
            MainActivityPermissionsDispatcher.getLocationWithCheck(this);
        }
    }

    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    @SuppressWarnings("MissingPermission")
    void getLocationWithNotTimeOut() {
        LocationRecordService tService = service.get();
        if(tService == null) {
            serviceNotFound.show();
            return;
        }
        tService.startWithNotTimeOut();
    }

    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    @SuppressWarnings("MissingPermission")
    void getLocation() {
        LocationRecordService tService = service.get();
        if(tService == null) {
            serviceNotFound.show();
            return;
        }
        tService.start();
    }

    private Snackbar permissionFail;
    @OnNeverAskAgain(Manifest.permission.ACCESS_FINE_LOCATION)
    void onGetLocationNeverAskAgain() {
        permissionFail.show();
    }

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

    private AlertDialog.Builder notLocationServiceDialog;
    private void checkLocationService() {
        LocationManager locationManager =
                (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
        if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            btnStart.setEnabled(false);
            notLocationServiceDialog.show();
        }
        else {
            btnStart.setEnabled(true);
        }
    }

}
