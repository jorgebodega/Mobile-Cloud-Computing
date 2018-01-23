package fi.aalto.narcolepticninjas.shareapicture;

import android.util.Log;

/**
 * A wrapper around the default logging class that allows messages to be more easily
 * formatted.
 */
public class Logger {
    public static void d(String TAG, String msg) {
        Log.d(TAG, msg);
    }
    public static void d(String TAG, String msg, Throwable tr) {
        Log.d(TAG, msg, tr);
    }
    public static void d(String TAG, String msg, Object ...params) {
        Log.d(TAG, String.format(msg, params));
    }

    public static void i(String TAG, String msg) {
        Log.i(TAG, msg);
    }
    public static void i(String TAG, String msg, Throwable tr) {
        Log.i(TAG, msg, tr);
    }
    public static void i(String TAG, String msg, Object ...params) {
        Log.i(TAG, String.format(msg, params));
    }

    public static void w(String TAG, String msg) {
        Log.w(TAG, msg);
    }
    public static void w(String TAG, String msg, Throwable tr) {
        Log.w(TAG, msg, tr);
    }
    public static void w(String TAG, String msg, Object ...params) {
        Log.w(TAG, String.format(msg, params));
    }

    public static void e(String TAG, String msg) {
        Log.e(TAG, msg);
    }
    public static void e(String TAG, String msg, Throwable tr) {
        Log.e(TAG, msg, tr);
    }
    public static void e(String TAG, String msg, Object ...params) {
        Log.e(TAG, String.format(msg, params));
    }

    public static void wtf(String TAG, String msg) {
        Log.wtf(TAG, msg);
    }
    public static void wtf(String TAG, String msg, Throwable tr) {
        Log.wtf(TAG, msg, tr);
    }
    public static void wtf(String TAG, String msg, Object ...params) {
        Log.wtf(TAG, String.format(msg, params));
    }
}