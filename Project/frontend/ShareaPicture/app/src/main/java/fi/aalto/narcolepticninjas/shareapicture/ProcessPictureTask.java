package fi.aalto.narcolepticninjas.shareapicture;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.SparseArray;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

public class ProcessPictureTask extends AsyncTask<Uri, Void, Bitmap> {
    private static final String TAG = ProcessPictureTask.class.getSimpleName();

    private Context mContext;

    public ProcessPictureTask(Context context) {
        mContext = context;
    }

    @Override
    protected Bitmap doInBackground(Uri... uris) {
        Bitmap image = decodeImage(uris[0]);
        boolean containsBarcodes = detectBarcodes(image);
        if (containsBarcodes) {
            Logger.d(TAG, "Image contains barcodes; saving original to disk");

            // Save image to external storage
            saveToExternalStorage(image);
            return null;
        }

        String resolution = chooseResolutionForUpload();
        Logger.d(TAG, "Resizing image to %s for upload", resolution);

        switch (resolution) {
            case "high":
                return resize(image, 1280, 960);
            case "low":
                return resize(image, 640, 480);
            case "original":
                return image;
            default:
                Logger.w(TAG, "Got unexpected size for image: %s. Fallback to original", resolution);
                return image;
        }
    }

    private Bitmap resize(Bitmap image, int maxWidth, int maxHeight) {
        int width = image.getWidth();
        int height = image.getHeight();

        float scaleW = (float) maxWidth / (float) width;
        float scaleH = (float) maxHeight / (float) height;
        float scale = Math.min(scaleW, scaleH);

        int dstWidth = Math.round(width * scale);
        int dstHeight = Math.round(height * scale);

        Logger.d(TAG, "Scaling image from %dx%d to %dx%d", width, height, dstWidth, dstHeight);
        return Bitmap.createScaledBitmap(image, dstWidth, dstHeight, true);
    }

    /**
     * Decodes the image from the given URI into a Bitmap.
     *
     * @param imageUri the URI of the image to load from disk
     *
     * @return the Bitmap of the image
     */
    private Bitmap decodeImage(Uri imageUri) {
        InputStream imageStream = null;
        try {
            imageStream = mContext.getContentResolver().openInputStream(imageUri);
        } catch (FileNotFoundException e) {
            Logger.e(TAG, "File not found from %s", imageUri.toString(), e);
            return null;
        }

        return BitmapFactory.decodeStream(imageStream);
    }

    /**
     * Detect if the given image bitmap contains barcodes.
     *
     * @param image the image to scan
     * @return true if barcodes are present, false otherwise
     */
    private Boolean detectBarcodes(Bitmap image) {
        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(mContext)
            .setBarcodeFormats(Barcode.QR_CODE)
            .build();

        if (!barcodeDetector.isOperational()) {
            Logger.d(TAG, "detectBarcodes(): BarcodeDetector not operational");
            return false;
        }

        Frame frame = new Frame.Builder().setBitmap(image).build();
        SparseArray<Barcode> barcodes = barcodeDetector.detect(frame);
        Logger.d(TAG, "detectBarcodes(): Barcode count: %d", barcodes.size());

        barcodeDetector.release();

        return barcodes.size() > 0;
    }

    /**
     * Determine the correct size of the image to upload for the current network
     * conditions.
     *
     * @return one of 'original', 'high' or 'low'
     */
    @NonNull
    private String chooseResolutionForUpload() {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
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

    /**
     * Saves a picture to private folder in app's external storage to path getExternalFilesDir(Environment.DIRECTORY_PICTURES) + "/private"
     * Note: External storage is only accessible only by ShareaPicture app. Permission wise this is simple option but has limitations. Alternatives exist.
     * Note: Original photo is not removed.
     * @param image
     */
    private void saveToExternalStorage(Bitmap image) {

        // Check for availability of external storage. Mostly for reference as is used just previously
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            Log.e(TAG, "saveToExternalStorage: External storage not available");
            return;
        }

        // Get the path to the directory as File and create directory if necessary
        File path = new File(mContext.getExternalFilesDir(
                Environment.DIRECTORY_PICTURES), "private");
        if (!path.exists()) {
            path.mkdirs();
        }
    
        // Create Id for local picture which is used as file name
        String localPictureId = UUID.randomUUID().toString() + ".jpeg";

        try {
            // Create the file for new image
            File file = new File(path,localPictureId);
            file.createNewFile();

            // Convert bitmap to bytearray
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            image.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            byte[] bitmapdata = bos.toByteArray();

            // Write the bytearray to file via stream
            FileOutputStream outputStream = null;
            outputStream = new FileOutputStream(file, false);
            outputStream.write(bitmapdata);
            outputStream.flush();
            outputStream.close();

//            For testing purposes:
//            File[] files = path.listFiles();
//            for (int i = 0; i < files.length; i++ ) {
//                Log.d(TAG, "saveToExternalStorage: " + files[i]);
//            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * UNTESTED Uri based variant of moving/saving the picture. For reference. Not in use.
     * @param uri
     */
    private void saveToExternalStorage(Uri uri) {

        // Check for availability of external storage
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            Log.e(TAG, "saveToExternalStorage: External storage not available");
            return;
        }

        // Get the path to the directory as File
        File path = new File(mContext.getExternalFilesDir(
                Environment.DIRECTORY_PICTURES), "private");
        if (!path.exists()) {
            path.mkdirs();
        }

        String localPictureId = UUID.randomUUID().toString() + ".jpeg";

        try {
            // Create the file for new image
            File file = new File(path,"myfile");
            file.createNewFile();

            InputStream in =  mContext.getContentResolver().openInputStream(uri);
            byte[] buffer = new byte[in.available()];
            in.read(buffer);

            // Write the bytearray to file via stream
            FileOutputStream outputStream = null;
            outputStream = new FileOutputStream(file, false);
            outputStream.write(buffer);
            outputStream.flush();
            outputStream.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
