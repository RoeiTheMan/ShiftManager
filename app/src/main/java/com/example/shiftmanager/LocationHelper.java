package com.example.shiftmanager;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

/**
 * The app's use of a phone capability: GPS.
 *
 * The final project requires at least one phone capability, and location is the one that
 * actually fits a shift app -- a manager stamps the real position onto a shift when
 * publishing it, and employees then see how far each shift is from where they are.
 *
 * Everything to do with permission and with reading the position is gathered here so the
 * screens do not each repeat it. Android requires location permission to be asked for at
 * runtime, not just declared in the manifest, so there are two steps: check, then request.
 */
public class LocationHelper {

    /** Passed to requestPermissions so we can recognise the answer when it comes back. */
    public static final int PERMISSION_REQUEST_CODE = 1001;

    private final Activity activity;
    private final FusedLocationProviderClient locationClient;

    public LocationHelper(@NonNull Activity activity) {
        this.activity = activity;
        this.locationClient = LocationServices.getFusedLocationProviderClient(activity);
    }

    /** Has the user already granted us location access? */
    public boolean hasPermission() {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Shows the system permission dialog. The answer arrives in onRequestPermissionsResult. */
    public void requestPermission() {
        ActivityCompat.requestPermissions(
                activity,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                PERMISSION_REQUEST_CODE);
    }

    /** True when a permission result belongs to us and the user said yes. */
    public static boolean isPermissionGranted(int requestCode, @NonNull int[] grantResults) {
        return requestCode == PERMISSION_REQUEST_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Asks for the phone's current position.
     *
     * Two deliberate choices here, both learned from this not working the first time:
     *
     * PRIORITY_HIGH_ACCURACY asks the GPS hardware directly. The balanced setting leans on
     * wifi and mobile-network positioning, which an emulator has no real source for, so a
     * request could sit unanswered forever and the feature looked broken when it was not.
     *
     * If that still comes back with nothing, it falls back to the last known position.
     * A fresh device may have no cached location at all, which is why that is the fallback
     * rather than the first choice, but between the two something is almost always found.
     *
     * The callback still receives null when neither works, so callers must handle that
     * rather than assuming success.
     */
    public void fetchCurrentLocation(@NonNull final Callback<Location> callback) {
        if (!hasPermission()) {
            callback.onError(new SecurityException("Location permission not granted"));
            return;
        }

        try {
            locationClient.getCurrentLocation(
                            Priority.PRIORITY_HIGH_ACCURACY,
                            new CancellationTokenSource().getToken())
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            callback.onSuccess(location);
                        } else {
                            fetchLastKnownLocation(callback);
                        }
                    })
                    .addOnFailureListener(error -> fetchLastKnownLocation(callback));
        } catch (SecurityException e) {
            // Permission was revoked between the check above and the call itself.
            callback.onError(e);
        }
    }

    /** Fallback used when a live reading is unavailable. May still hand back null. */
    private void fetchLastKnownLocation(@NonNull final Callback<Location> callback) {
        try {
            locationClient.getLastLocation()
                    .addOnSuccessListener(callback::onSuccess)
                    .addOnFailureListener(callback::onError);
        } catch (SecurityException e) {
            callback.onError(e);
        }
    }

    /** Formats a position for showing back to the user after it is captured. */
    public static String describe(@Nullable Location location) {
        if (location == null) {
            return "";
        }
        return String.format(java.util.Locale.US, "(%.4f, %.4f)",
                location.getLatitude(), location.getLongitude());
    }
}
