package fi.aalto.narcolepticninjas.shareapicture;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;

import android.util.DisplayMetrics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.util.Date;

public class Helpers {
    private static final String TAG = Helpers.class.getSimpleName();

    public static boolean isUserAuthenticated() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();

        return user != null && !user.isAnonymous();
    }

    /**
     * Converts a Date object to an UTC ISO string (2017-11-23T10:00:00Z).
     * @param date the date to convert
     * @return the date as an ISO string
     */
    public static String dateToIsoString(Date date) {
        DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
        Logger.d(TAG, "Got date");
        return fmt.withZone(DateTimeZone.UTC).print(new DateTime(date));
    }

    /**
     * Parses the given string as ISO date
     * @param date the date to parse
     * @return the Date object if it was parsed correctly
     */
    @Nullable
    public static Date isoStringToDate(String date) {
        DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
        return fmt.parseDateTime(date).toDate();
    }

    public static void setActiveGroup(Context context, String groupId, String expiry) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPref.edit();
        if (groupId == null) {
            editor.remove(Constants.ACTIVE_GROUP_ID);
            editor.remove(Constants.ACTIVE_GROUP_EXPIRY);
            context.stopService(new Intent(MainActivity.getAppContext(), MyService.class));
        } else {
            editor.putString(Constants.ACTIVE_GROUP_ID, groupId);
            editor.putString(Constants.ACTIVE_GROUP_EXPIRY, expiry);
            context.startService(new Intent(MainActivity.getAppContext(), MyService.class));
        }
        editor.apply();
    }

    @Nullable
    public static String getActiveGroup(Context context) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        String expiry = sharedPref.getString(Constants.ACTIVE_GROUP_EXPIRY, null);
        if (expiry == null) {
            return null;
        }

        Date expiryDate = Helpers.isoStringToDate(expiry);
        if (expiryDate == null) {
            Logger.w(TAG, "Failed to parse expiry date %s!", expiry);
            return null;
        }

        Date now = new Date();
        if (expiryDate.before(now)) {
            Logger.d(TAG, "Group has expired at %s (now %s)", expiryDate, now);
            setActiveGroup(context, null, null);
            return null;
        }

        return sharedPref.getString(Constants.ACTIVE_GROUP_ID, null);
    }

    public static String getActiveUserId() {
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    public static String getActiveUserName() {
        return FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
    }

    public static DisplayMetrics getDisplayMetrics(Activity activity) {
        DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return metrics;
    }

    public static String chooseResolutionForUpload(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        String wifiSetting = sharedPref.getString("wifi_settings", "original" );
        String mobileSetting = sharedPref.getString("mobile_settings", "high");

        if (activeNetwork == null) {
            Logger.w(TAG, "chooseResolutionForUpload: no network connection. Fallback to mobile");
            return mobileSetting;
        }

        boolean isWiFi = activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
        boolean isMobile = activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE;
        Logger.d(TAG, "chooseResolutionForUpload: isWiFi = %b, isMobile = %b", isWiFi, isMobile);

        if (isWiFi) {
            return wifiSetting;
        } else if (isMobile) {
            return mobileSetting;
        } else {
            Logger.w(TAG, "chooseResolutionForUpload: Unexpected network type %s (%d). Fallback to mobile",
                    activeNetwork.getTypeName(), activeNetwork.getType());
            return mobileSetting;
        }
    }

}
