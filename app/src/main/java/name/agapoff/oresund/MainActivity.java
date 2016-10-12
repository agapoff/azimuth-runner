package name.agapoff.oresund;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    public final static String LATITUDE = "name.agapoff.oresund.LATITUDE";
    public final static String LONGITUDE = "name.agapoff.oresund.LONGITUDE";
    private static final String TAG = "MainActivity";

    // Threshold to consider location as obsolete (in ms)
    private static final int OBSOLETE_POSITION_THRESHOLD = 60000;
    // Threshold to consider location as not very reliable
    private static final int WARNING_POSITION_THRESHOLD = 30000;
    private static final int WARNING_ACCURACY_THRESHOLD = 30;   // in meters

    // The minimum distance to change updates in meters
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 4;

    // The minimum time beetwen updates in milliseconds
    private static final long MIN_TIME_BW_UPDATES = 2000;

    // Rotate Animation duration
    private static final int ROTATE_DURATION = 300;

    // Magnetic heading will be avaraged for this period in ms
    // Optimal value - half of ROTATE_DURATION
    private static final int COMPASS_DELAY = 50;

    private LocationManager locationManager;
    private CustomKeyboard customKeyboard;
    private SharedPreferences preferences;
    private SensorManager sensorManager;

    private Coordinates currentLocation;
    private Coordinates targetLocation;
    private Sensor magnetometer;
    private Sensor accelerometer;
    private float[] mGravity = new float[3];
    private float[] mGeomagnetic = new float[3];

    private double currentAccuracy = 9999d;
    private double targetDistance, targetBearing;
    private int satellitesInFix = 0;
    private int satellitesTotal = 0;
    private String currentSatIconColor = "red";
    private long lastLocationUpdate;
    private float currentDegree = 0f;
    private float currentHeading = 0f;
    private float currentHeadingAvg;
    private int currentHeadingCount = 0;
    private long lastHeadingUpdate = 0;
    private boolean rotationInProgress = false;
    private boolean rotationQueued = false;
    private boolean isBatterySavingMode = false;
    private boolean isTrueNorthEnabled = false;
    private float magneticDeclination = 0f;
    private boolean isMagneticDeclinationCalculated = false;


    private ImageView image;
    private TextView tvDebug;
    private TextView tvDistance;
    private TextView tvSatStatus;
    private ImageView ivSatIcon;
    public Switch GPSswitch;

    // Variables for smoothing the compass
    private float smoothFactorCompass = 0.5f; // 1 is no smoothing and 0 is never updating
    private float smoothThresholdCompass = 30f; // the distance is big enough to turn immediatly (0 is jump always, 360 is never jumping)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        image = (ImageView) findViewById(R.id.imageViewCompass);
        Toolbar mToolbar = (Toolbar) findViewById(R.id.toolbar);

        // TextView that will tell the user what degree is he heading
        tvDebug = (TextView) findViewById(R.id.tvDebug);
        tvDistance = (TextView) findViewById(R.id.distance);
        tvSatStatus = (TextView) findViewById(R.id.sat_status);
        ivSatIcon = (ImageView) findViewById(R.id.sat_icon);

        // initialize your android device sensor capabilities
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        //getSupportActionBar().setDisplayShowHomeEnabled(false);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        //getSupportActionBar().setIcon(R.drawable.oresund);
        //getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);

        ViewPager mViewPager = (ViewPager) findViewById(R.id.viewpager);
        setupViewPager(mViewPager);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);
        tabLayout.getTabAt(0).setIcon(R.drawable.ic_compass2);
        tabLayout.getTabAt(1).setIcon(R.drawable.ic_coords);
        tabLayout.getTabAt(2).setIcon(R.drawable.ic_anchor);
        tabLayout.getTabAt(3).setIcon(R.drawable.ic_notebook);

        customKeyboard = new CustomKeyboard(this, R.id.keyboardview, R.xml.coordkbd);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (preferences.getBoolean("pref_debug", false)) {
            tvDebug.setVisibility(View.VISIBLE);
        } else {
            tvDebug.setVisibility(View.INVISIBLE);
        }

        isBatterySavingMode = preferences.getBoolean("pref_battery_save", false);
        int sensorSamplingPeriod;
        if (isBatterySavingMode) {
            sensorSamplingPeriod = SensorManager.SENSOR_DELAY_NORMAL;
        } else {
            sensorSamplingPeriod = SensorManager.SENSOR_DELAY_UI;
        }
        sensorManager.registerListener(this, accelerometer, sensorSamplingPeriod);
        sensorManager.registerListener(this, magnetometer, sensorSamplingPeriod);
        if (GPSswitch != null && GPSswitch.isChecked()) enableLocationUpdates();

        isTrueNorthEnabled = preferences.getBoolean("pref_true_north", false);
        if (!isTrueNorthEnabled) {
            magneticDeclination = 0f;
            isMagneticDeclinationCalculated = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // to stop the listener and save battery
        sensorManager.unregisterListener(this);

        if (isBatterySavingMode) disableLocationUpdates();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        // NOTE Trap the back key: when the CustomKeyboard is still visible hide it, only when it is invisible, finish activity
        if (customKeyboard.isCustomKeyboardVisible()) customKeyboard.hideCustomKeyboard();
        else this.finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Handling feedback from enabling the GPS
        if (resultCode == 0) {
            switch (requestCode) {
                case 1:
                    if (isLocationEnabled()) {
                        if (enableLocationUpdates()) GPSswitch.setChecked(true);
                    }
                    break;
            }
        }
        // Handling feedback from loading the location
        else if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                GoToSavedLocation(data.getDoubleExtra("latitude", 0), data.getDoubleExtra("longitude", 0));
            }
        }
    }

    // Handling feedback from granting the permissions
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 666: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (enableLocationUpdates()) GPSswitch.setChecked(true);
                }
            }
        }
    }


    private boolean enableLocationUpdates() {
        if (!checkLocation()) return false;

        try {
            //locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListenerGPS);
            if (isBatterySavingMode)
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2 * MIN_TIME_BW_UPDATES, 2 * MIN_DISTANCE_CHANGE_FOR_UPDATES, locationListenerGPS);
            else
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, locationListenerGPS);
            locationManager.addGpsStatusListener(gpsStatusListener);
        } catch (SecurityException ex) {
            Toast.makeText(MainActivity.this, getString(R.string.error_enabling_location_service) + ": " + ex.getMessage() + " " +
                    locationManager.getAllProviders().toString(), Toast.LENGTH_SHORT).show();
            return false;
        } catch (Exception ex) {
            Toast.makeText(MainActivity.this, getString(R.string.error_enabling_location_service) + ": " + ex.getMessage() + " " +
                    locationManager.getAllProviders().toString(), Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private boolean disableLocationUpdates() {
        try {
            locationManager.removeUpdates(locationListenerGPS);
        } catch (SecurityException ex) {
            Toast.makeText(MainActivity.this, "Error disabling location service: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }

        tvSatStatus.setText("");
        if (! currentSatIconColor.equals("red")) {
            ivSatIcon.setImageResource(R.drawable.sat_red);
            currentSatIconColor = "red";
        }

        return true;
    }

    private boolean isLocationEnabled() {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        //return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        //        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

    }

    private void showAlert() {
        final AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.enable_location)
                .setMessage(R.string.location_is_off)
                .setPositiveButton(R.string.location_settings, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                        Intent myIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        //startActivity(myIntent);
                        startActivityForResult(myIntent, 1);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                    }
                });
        dialog.show();
    }

    @TargetApi(23)
    private boolean checkLocation() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 666);
            return false;
        }
        if (!isLocationEnabled())
            showAlert();
        return isLocationEnabled();
    }

    void closeSoftKeyboard(View view) {
        if (customKeyboard.isCustomKeyboardVisible()) customKeyboard.hideCustomKeyboard();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public void Go(View view) {

        closeSoftKeyboard(view);

        EditText distanceInput = (EditText) findViewById(R.id.setDistance);
        EditText bearingInput = (EditText) findViewById(R.id.setBearing);
        try {
            targetDistance = Float.parseFloat(distanceInput.getText().toString());
        } catch (NumberFormatException ex) {
            Toast.makeText(MainActivity.this, R.string.wrong_distance, Toast.LENGTH_SHORT).show();
            distanceInput.setText("0");
        }
        try {
            targetBearing = Float.parseFloat(bearingInput.getText().toString());
        } catch (NumberFormatException ex) {
            Toast.makeText(MainActivity.this, R.string.wrong_bearing, Toast.LENGTH_SHORT).show();
            bearingInput.setText("0");
        }

        if (currentLocation == null) {
            Toast.makeText(MainActivity.this, R.string.unknown_position, Toast.LENGTH_SHORT).show();
            return;
        }

        if (System.currentTimeMillis() - lastLocationUpdate > OBSOLETE_POSITION_THRESHOLD) {
            Toast.makeText(MainActivity.this, R.string.obsolete_position, Toast.LENGTH_SHORT).show();
            return;
        }

        targetLocation = getCoordinatesByDistanceAndBearingPlain(currentLocation, targetDistance, targetBearing);

        rotateArrow();
    }

    private double parseCoord(String line) {
        int multiplier = line.startsWith("-") ? -1 : 1;
        Pattern r = Pattern.compile("^-?([\\.\\d]+)°?([\\.\\d]*)'?([\\.\\d]*)\"?");
        Matcher m = r.matcher(line);
        if (m.find()) {
            float deg = Float.parseFloat(m.group(1));
            float min = m.group(2) == null || m.group(2).isEmpty() ? 0 : Float.parseFloat(m.group(2));
            float sec = m.group(3) == null || m.group(3).isEmpty() ? 0 : Float.parseFloat(m.group(3));
            //Toast.makeText(MainActivity.this, "e-" + m.group(1) + "-" + m.group(2) + "-" + m.group(3), Toast.LENGTH_SHORT).show();
            return multiplier * (deg + min / 60.0 + sec / 3600.0);
        } else {
            throw new NumberFormatException();
        }
    }

    static String convertCoordToWGS(double degree) {
        DecimalFormat df = new DecimalFormat("#.####");
        df.setRoundingMode(RoundingMode.CEILING);
        int integerPart = (int) degree;
        double fractionalPart = Math.abs(degree % 1);
        return integerPart + "°" + df.format(fractionalPart * 60.0) + "'";
    }

    public void GoToCoords(View view) {

        closeSoftKeyboard(view);

        EditText latitudeInput = (EditText) findViewById(R.id.setLatitude);
        EditText longitudeInput = (EditText) findViewById(R.id.setLongitude);

        double targetLatitude = 0f, targetLongitude = 0f;
        try {
            targetLatitude = parseCoord(latitudeInput.getText().toString());
        } catch (NumberFormatException ex) {
            Toast.makeText(MainActivity.this, R.string.wrong_latitude, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            targetLongitude = parseCoord(longitudeInput.getText().toString());
        } catch (NumberFormatException ex) {
            Toast.makeText(MainActivity.this, R.string.wrong_longitude, Toast.LENGTH_SHORT).show();
            return;
        }

        targetLocation = new Coordinates(targetLatitude, targetLongitude);
        if (targetLocation != null && currentLocation != null) {
            PolarCoordinates whereToGo = getDistanceAndBearingByCoordinatesPlain(currentLocation, targetLocation);
            targetDistance = whereToGo.getDistance();
            targetBearing = whereToGo.getBearing();
        }

        if (currentLocation != null) rotateArrow();
    }

    public final void GoToSavedLocation(double targetLatitude, double targetLongitude) {
        targetLocation = new Coordinates(targetLatitude, targetLongitude);
        if (targetLocation != null && currentLocation != null) {
            PolarCoordinates whereToGo = getDistanceAndBearingByCoordinatesPlain(currentLocation, targetLocation);
            targetDistance = whereToGo.getDistance();
            targetBearing = whereToGo.getBearing();
        }

        if (currentLocation != null) rotateArrow();
    }

    public void dropAnchor(View view) {

        if (currentLocation == null) {
            Toast.makeText(MainActivity.this, R.string.unknown_position, Toast.LENGTH_SHORT).show();
            return;
        }

        if (System.currentTimeMillis() - lastLocationUpdate > OBSOLETE_POSITION_THRESHOLD) {
            Toast.makeText(MainActivity.this, R.string.obsolete_position, Toast.LENGTH_SHORT).show();
            return;
        }

        targetLocation = currentLocation;
        targetDistance = 0;
        targetBearing = 0f;

        rotateArrow();
    }

    private float normalizeDegree(float from, float to) {
        if (Math.abs(to - from) > 180) {
            if (from > to) to += 360;
            else to -= 360;
        } else return to;
        return normalizeDegree(from, to);
    }

    private void rotateArrow() {
        //if (System.currentTimeMillis() < rotationEndTimeMillis) return;
        if (rotationInProgress) {
            rotationQueued = true;
            return;
        }
        if (System.currentTimeMillis() - lastLocationUpdate > OBSOLETE_POSITION_THRESHOLD && ! currentSatIconColor.equals("red"))
            updateSatelliteIcon();

        rotationQueued = false;
        rotationInProgress = true;
        //float newDegree = smoothRotation(-currentHeading + (float) targetBearing);
        float newDegree = -currentHeading + (float) targetBearing;
        //currentDegree = image.getRotation();

        newDegree = normalizeDegree(currentDegree, newDegree);

        RotateAnimation ra = new RotateAnimation(
                currentDegree,
                newDegree,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);

        ra.setDuration(ROTATE_DURATION);

        // set the animation after the end of the reservation status
        ra.setFillAfter(true);

        // Start the animation
        image.startAnimation(ra);

        ra.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation arg0) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation arg0) {
                rotationInProgress = false;
                if (rotationQueued) {
                    //Log.d(TAG, "end " + image.getRot);
                    rotateArrow();
                }
            }
        });

        //rotationEndTimeMillis = System.currentTimeMillis() + nextRotateDuration;
        currentDegree = newDegree;

        CharSequence summary = getString(R.string.heading) + ": " + Math.round(currentHeading);

        if (isTrueNorthEnabled && isMagneticDeclinationCalculated) {
            summary = summary + "\n" + getString(R.string.magnetic_declination) + ": " + magneticDeclination;
        }

        if (currentAccuracy > 1000 || currentLocation == null) {
            summary = summary + "\n" + getString(R.string.accuracy) + ": n/a";
        } else {
            summary = summary + "\n" + getString(R.string.accuracy) + ": " + currentAccuracy + "\n" + getString(R.string.current_latitude) + ": " +
                    currentLocation.getLatitude() + "\n" + getString(R.string.current_longitude) + ": " + currentLocation.getLongitude();
        }

        if (targetLocation != null) {
            tvDistance.setText(Math.round(targetDistance) + " " + getString(R.string.meters));
            summary = summary + "\n" + getString(R.string.bearing) + ": " + targetBearing + "\n" + getString(R.string.target_latitude) + ": " +
                    targetLocation.getLatitude() + "\n" + getString(R.string.target_longitude) + ": " + targetLocation.getLongitude();
        }

        summary = summary + "\n" + getString(R.string.last_location_update) + ": ";
        if (lastLocationUpdate != 0) {
            summary = summary + "" + Math.round(System.currentTimeMillis() - lastLocationUpdate) + " " + getString(R.string.milliseconds);
        } else {
            summary = summary + getString(R.string.never);
        }

        tvDebug.setText(summary);
    }

    private float smoothRotation(float newDegree) {
        if (Math.abs(newDegree - currentDegree) < 180) {
            if (Math.abs(newDegree - currentDegree) > smoothThresholdCompass) {
                return newDegree;
            }
            return currentDegree + smoothThresholdCompass * (newDegree - currentDegree);
        } else {
            if (360f - Math.abs(newDegree - currentDegree) > smoothThresholdCompass) {
                return newDegree;
            }
            if (currentDegree > newDegree) {
                return (currentDegree + smoothFactorCompass * ((360 + newDegree - currentDegree) % 360) + 360) % 360;
            } else {
                return (currentDegree - smoothFactorCompass * ((360 - newDegree + currentDegree) % 360) + 360) % 360;
            }
        }
    }

    GpsStatus.Listener gpsStatusListener = new GpsStatus.Listener() {

        @Override
        public void onGpsStatusChanged(final int event) {
            switch (event) {
                case GpsStatus.GPS_EVENT_STARTED:
                    //Log.d(TAG, "GPS_EVENT_STARTED");
                    break;
                case GpsStatus.GPS_EVENT_FIRST_FIX:
                    //Log.d(TAG, "GPS_EVENT_FIRST_FIX");
                    updateGpsStatus();
                    break;
                case GpsStatus.GPS_EVENT_STOPPED:
                    //Log.d(TAG, "GPS_EVENT_STOPPED");
                    break;
                case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                    //Log.d(TAG, "GPS_EVENT_SATELLITE_STATUS");
                    updateGpsStatus();
                    break;
                default:
                    Log.d(TAG, "GPS_EVENT_UNKNOWN_STATUS");
                    break;
            }
        }
    };

    private void updateGpsStatus() {
        satellitesTotal = 0;
        satellitesInFix = 0;
        try {
            for (GpsSatellite satellite : locationManager.getGpsStatus(null).getSatellites()) {
                satellitesTotal++;
                if (satellite.usedInFix()) {
                    satellitesInFix++;
                }
            }
        } catch (SecurityException ex) {
            Log.e(TAG, "Security Exception " + ex.toString());
        }

        updateSatelliteIcon();
    }

    private void updateSatelliteIcon() {
        CharSequence status = satellitesInFix + " / " + satellitesTotal;
        if (currentAccuracy < 1000 && currentLocation != null &&
                System.currentTimeMillis() - lastLocationUpdate < OBSOLETE_POSITION_THRESHOLD) {
            status = status + "\n" + currentAccuracy + " " + getString(R.string.meters);
        }
        tvSatStatus.setText(status);

        if (currentAccuracy < 1000 && currentLocation != null) {
            if (System.currentTimeMillis() - lastLocationUpdate < OBSOLETE_POSITION_THRESHOLD) {
                if (System.currentTimeMillis() - lastLocationUpdate > WARNING_POSITION_THRESHOLD || currentAccuracy > WARNING_ACCURACY_THRESHOLD) {
                    if (! currentSatIconColor.equals("yellow")) {
                        ivSatIcon.setImageResource(R.drawable.sat_yellow);
                        currentSatIconColor = "yellow";
                    }
                    return;
                }
                if (! currentSatIconColor.equals("green")) {
                    ivSatIcon.setImageResource(R.drawable.sat_green);
                    currentSatIconColor = "green";
                }
                return;
            }
        }
        if (! currentSatIconColor.equals("red")) {
            ivSatIcon.setImageResource(R.drawable.sat_red);
            currentSatIconColor = "red";
        }
        currentAccuracy = 9999d;
    }

    private final LocationListener locationListenerGPS = new LocationListener() {
        public void onLocationChanged(Location location) {
            currentLocation = new Coordinates(location.getLatitude(), location.getLongitude());
            currentAccuracy = location.getAccuracy();
            lastLocationUpdate = System.currentTimeMillis();
            updateSatelliteIcon();
            //Coordinates targetLocation = getCoordinatesByDistanceAndBearingPlain(new Coordinates(currentLatitude, currentLongitude), 1000, 270);

            if (targetLocation != null) {
                PolarCoordinates whereToGo = getDistanceAndBearingByCoordinatesPlain(currentLocation, targetLocation);
                targetDistance = whereToGo.getDistance();
                targetBearing = whereToGo.getBearing();
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    rotateArrow();
                }
            });

            if (isTrueNorthEnabled && !isMagneticDeclinationCalculated) {
                magneticDeclination = new GeomagneticField((float) location.getLatitude(), (float) location.getLongitude(), 0, System.currentTimeMillis()).getDeclination();
                isMagneticDeclinationCalculated = true;
                Log.d(TAG, "Magnetic declination " + magneticDeclination);
            }
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {

        }

        @Override
        public void onProviderEnabled(String s) {

        }

        @Override
        public void onProviderDisabled(String s) {

        }
    };

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // not in use
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        /*
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            mGravity = event.values;
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mGeomagnetic = event.values;
            */
        final float alpha = 0.7f;
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mGravity[0] = alpha * mGravity[0] + (1 - alpha) * event.values[0];
            mGravity[1] = alpha * mGravity[1] + (1 - alpha) * event.values[1];
            mGravity[2] = alpha * mGravity[2] + (1 - alpha) * event.values[2];
        }
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mGeomagnetic[0] = alpha * mGeomagnetic[0] + (1 - alpha) * event.values[0];
            mGeomagnetic[1] = alpha * mGeomagnetic[1] + (1 - alpha) * event.values[1];
            mGeomagnetic[2] = alpha * mGeomagnetic[2] + (1 - alpha) * event.values[2];
        }

        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];
            if (SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic)) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                currentHeading = ((float) Math.toDegrees(orientation[0]) + 360 + magneticDeclination) % 360; // orientation contains: azimut, pitch and roll

                currentHeadingAvg = (currentHeadingAvg * currentHeadingCount + normalizeDegree(currentHeadingAvg, currentHeading)) / ++currentHeadingCount;
                if (System.currentTimeMillis() - lastHeadingUpdate > COMPASS_DELAY) {
                    currentHeading = currentHeadingAvg;
                    rotateArrow();
                    currentHeadingCount = 0;
                    currentHeadingAvg = 180;
                    lastHeadingUpdate = System.currentTimeMillis();
                }
            }
        }
    }

    final class Coordinates {
        private final double latitude;
        private final double longitude;

        public Coordinates(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }
    }

    final class PolarCoordinates {
        private final double distance;
        private final double bearing;

        public PolarCoordinates(double distance, double bearing) {
            this.distance = distance;
            this.bearing = bearing;
        }

        public double getDistance() {
            return distance;
        }

        public double getBearing() {
            return bearing;
        }
    }

    public Coordinates getCoordinatesByDistanceAndBearingPlain(Coordinates coordinates, double distance, double bearing) {
        double x2;
        double y2;
        double y1 = coordinates.getLatitude();
        double x1 = coordinates.getLongitude();
        int metersPerDegree = 111300;

        y2 = y1 + Math.cos(Math.toRadians(bearing)) * distance / metersPerDegree;
        x2 = x1 + Math.sin(Math.toRadians(bearing)) * distance / (metersPerDegree * Math.cos(Math.toRadians(y1)));

        return new Coordinates(y2, x2);
    }

    public PolarCoordinates getDistanceAndBearingByCoordinatesPlain(Coordinates startCoordinates, Coordinates targetCoordinates) {
        double x1 = startCoordinates.getLongitude();
        double y1 = startCoordinates.getLatitude();
        double x2 = targetCoordinates.getLongitude();
        double y2 = targetCoordinates.getLatitude();
        int metersPerDegree = 111300;
        double distance, bearing;

        bearing = Math.atan2((x2 - x1) * Math.cos(Math.toRadians((y1 + y2) / 2)), y2 - y1);
        //distance = ( y2 - y1 ) * metersPerDegree / Math.cos(bearing);
        distance = metersPerDegree * Math.sqrt(Math.pow(y2 - y1, 2) + Math.pow((x2 - x1) * Math.cos(Math.toRadians((y1 + y2) / 2)), 2));

        return new PolarCoordinates(distance, (Math.toDegrees(bearing) + 360) % 360);
    }

    public Coordinates getCoordinatesByDistanceAndBearingSphere(Coordinates coordinates, double distance, double bearing) {
        double x2, y2, xDelta;
        double y1 = coordinates.getLatitude();
        double x1 = coordinates.getLongitude();
        int earthRadius = 6371000;

        y2 = Math.asin(Math.sin(Math.toRadians(y1)) * Math.cos(distance / earthRadius) + Math.cos(Math.toRadians(y1)) * Math.sin(distance / earthRadius) * Math.cos(Math.toRadians(bearing)));
        xDelta = Math.atan2(Math.sin(Math.toRadians(bearing)) * Math.sin(distance / earthRadius) * Math.cos(Math.toRadians(y1)),
                Math.cos(distance / earthRadius) - Math.sin(Math.toRadians(y1)) * Math.sin(Math.toRadians(y1)));
        x2 = x1 + Math.toDegrees(xDelta);

        return new Coordinates(Math.toDegrees(y2), x2);
    }

    public void saveTargetLocation(View view) {

        if (targetLocation == null) {
            Toast.makeText(MainActivity.this, R.string.no_target, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, SaveLocationActivity.class);
        intent.putExtra(LATITUDE, targetLocation.getLatitude());
        intent.putExtra(LONGITUDE, targetLocation.getLongitude());
        startActivity(intent);
    }

    private void saveCurrentLocation(View view) {

        if (currentLocation == null) {
            Toast.makeText(MainActivity.this, R.string.unknown_position, Toast.LENGTH_SHORT).show();
            return;
        }
        if (System.currentTimeMillis() - lastLocationUpdate > OBSOLETE_POSITION_THRESHOLD) {
            Toast.makeText(MainActivity.this, R.string.obsolete_position, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, SaveLocationActivity.class);
        intent.putExtra(LATITUDE, currentLocation.getLatitude());
        intent.putExtra(LONGITUDE, currentLocation.getLongitude());
        startActivity(intent);
    }

    private void loadLocation(View view) {
        Intent intent = new Intent(this, LoadLocationActivity.class);
        startActivityForResult(intent, 1);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        //customKeyboard.registerEditText(R.id.setLatitude);
        //customKeyboard.registerEditText(R.id.setLongitude);

        // GPS Switch should be on by default
        final View switchView = menu.findItem(R.id.gps_switch_item).getActionView();
        GPSswitch = (Switch) switchView.findViewById(R.id.GPSswitch);

        if (enableLocationUpdates()) GPSswitch.setChecked(true);

        GPSswitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (!enableLocationUpdates()) GPSswitch.setChecked(false);
                } else {
                    if (!disableLocationUpdates()) GPSswitch.setChecked(true);
                }
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_help) {
            Intent intent = new Intent(this, HelpActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // Defines the number of tabs by setting appropriate fragment and tab name.
    private void setupViewPager(ViewPager viewPager) {
        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());
        adapter.addFragment(new FragmentCompass(), "COMPASS");
        adapter.addFragment(new FragmentCoords(), "COORDS");
        adapter.addFragment(new FragmentAnchor(), "ANCHOR");
        adapter.addFragment(new FragmentNotebook(), "NOTEBOOK");
        viewPager.setAdapter(adapter);


        // There is some kind of bug - EditView loses the customKeyboard if distant tab is selected
        // So this is a workaround
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageSelected(int position) {
                closeSoftKeyboard(findViewById(android.R.id.content));
                if (position == 1) {
                    customKeyboard.registerEditText(R.id.setLatitude);
                    customKeyboard.registerEditText(R.id.setLongitude);
                }
            }

            @Override
            public void onPageScrolled(int arg0, float arg1, int arg2) {
            }

            @Override
            public void onPageScrollStateChanged(int arg0) {
            }
        });
    }

    // Custom adapter class provides fragments required for the view pager.
    class ViewPagerAdapter extends FragmentPagerAdapter {
        private final List<Fragment> mFragmentList = new ArrayList<>();
        private final List<String> mFragmentTitleList = new ArrayList<>();

        public ViewPagerAdapter(FragmentManager manager) {
            super(manager);
        }

        @Override
        public Fragment getItem(int position) {
            return mFragmentList.get(position);
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        public void addFragment(Fragment fragment, String title) {
            mFragmentList.add(fragment);
            mFragmentTitleList.add(title);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            //return mFragmentTitleList.get(position);
            // return null to display only the icon
            return null;
        }
    }
}
