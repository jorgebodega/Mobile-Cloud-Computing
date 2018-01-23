package fi.aalto.narcolepticninjas.shareapicture;

import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import fi.aalto.narcolepticninjas.shareapicture.backend.BackendApi;
import fi.aalto.narcolepticninjas.shareapicture.backend.BackendApiResponseCallback;
import okhttp3.Call;
import okhttp3.Response;

public class JoinToGroupActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = JoinToGroupActivity.class.getSimpleName();
    private static final int RC_CAMERA_PERMISSION_REQUEST = 73;

    SurfaceView surfaceView_camera;
    TextView txt_qrCodeStatus;

    private CameraSource mCameraSource;
    private BarcodeDetector mDetector;
    private boolean mSurfaceReady;
    private boolean mJoinInProgress;

    // QR code reading with Vision API
    // https://code.tutsplus.com/tutorials/reading-qr-codes-using-the-mobile-vision-api--cms-24680

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_to_group);

        surfaceView_camera = (SurfaceView) findViewById(R.id.surfaceView_camera);
        surfaceView_camera.getHolder().addCallback(new SurfaceCallback());
        txt_qrCodeStatus = (TextView) findViewById(R.id.txt_qrCodeStatus);
        mJoinInProgress = false;

        int rc = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraPreviewIfReady();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCameraPreviewIfRunning();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseCameraPreviewIfRunning();
    }

    private void createCameraSource() {
        mDetector = createBarcodeDetector();
        mCameraSource = new CameraSource.Builder(this, mDetector)
            .setRequestedPreviewSize(640, 480)
            .setFacing(CameraSource.CAMERA_FACING_BACK)
            .build();
    }

    private BarcodeDetector createBarcodeDetector() {
        Logger.d(TAG, "createBarcodeDetector() called");
        BarcodeDetector detector = new BarcodeDetector.Builder(this)
            .setBarcodeFormats(Barcode.QR_CODE)
            .build();
        detector.setProcessor(new BarcodeDetectorProcessor());
        return detector;
    }

    private class BarcodeDetectorProcessor implements Detector.Processor<Barcode> {
        @Override
        public void release() {
            Logger.d(TAG, "BarcodeDetector released");
        }

        @Override
        public void receiveDetections(Detector.Detections<Barcode> detections) {

            final SparseArray<Barcode> barcodes = detections.getDetectedItems();
            if (barcodes.size() != 0 && !mJoinInProgress) {
                //Logger.d(TAG, "Received barcode detections");
                String value = barcodes.valueAt(0).displayValue;

                String[] split = Uri.decode(value).split("&id=");
                //Logger.d(TAG, "split: %s id %s", split[0], split[1]);
                if (split.length < 2) {
                    txt_qrCodeStatus.post(new Runnable() {    // Use the post method of the TextView
                        public void run() {
                        txt_qrCodeStatus.setText(    // Update the TextView
                                "Invalid QR Code: \n" +
                                barcodes.valueAt(0).displayValue
                        );
                        }
                    });
                    return;
                }

                txt_qrCodeStatus.post(new Runnable() {    // Use the post method of the TextView
                    public void run() {
                    txt_qrCodeStatus.setText(    // Update the TextView
                            "QR Code read, joining group..."
                    );
                    }
                });

                String token = split[0];
                String groupId = split[1];

                mJoinInProgress = true;
                BackendApi.joinGroup(token, groupId, new BackendApiResponseCallback() {
                    private void showError() {
                        Toast.makeText(JoinToGroupActivity.this, "Error occurred", Toast.LENGTH_LONG).show();
                    }
                    @Override
                    public void onResponse(final Call call, final Response response, final JSONObject body) {

                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            public void run() {
                                if (response.code() > 299) {
                                    Logger.d(TAG, "Response code was " + response.code());
                                    showError();
                                    txt_qrCodeStatus.post(new Runnable() {    // Use the post method of the TextView
                                        public void run() {
                                        txt_qrCodeStatus.setText(    // Update the TextView
                                                "Unable to join group with QR Code\nGot response code " + response.code()
                                        );
                                        }
                                    });
                                    return;
                                }

                                String groupId;
                                String expiry;
                                try {
                                    groupId = body.getString("group_id");
                                    expiry = body.getString("expiry");
                                } catch (JSONException e) {
                                    onFailure(call, new IOException("No group_id in response!", e));
                                    return;
                                }

                                Helpers.setActiveGroup(JoinToGroupActivity.this, groupId, expiry);
                                Toast.makeText(JoinToGroupActivity.this, getString(R.string.join_group_success), Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        });
                    }

                    @Override
                    public void onFailure(Call call, IOException e) {
                        Logger.e(TAG, "Join group failed", e);
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            public void run() {
                                showError();
                            }
                        });
                    }
                });
            }
        }
    }

    private class SurfaceCallback implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            Logger.d(TAG, "SurfaceCallback::surfaceCreated()");
            mSurfaceReady = true;

            startCameraPreviewIfReady();
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {}

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            Logger.d(TAG, "SurfaceCallback::surfaceDestroyed()");

            mSurfaceReady = false;
        }
    }

    private void requestCameraPermission() {
        Logger.d(TAG, "Missing camera permission. Requesting access to camera");

        final String[] permissions = new String[]{android.Manifest.permission.CAMERA};
        ActivityCompat.requestPermissions(this, permissions, RC_CAMERA_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != RC_CAMERA_PERMISSION_REQUEST) {
            Logger.wtf(TAG, "onRequestPermissionsResult(): Unexpected requestCode %d", requestCode);
            return;
        }

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Logger.i(TAG, "Access to camera granted");
            createCameraSource();
            startCameraPreviewIfReady();
        } else {
            new AlertDialog.Builder(this)
                .setTitle("Access to Camera Required")
                .setMessage("You need to allow camera access to join a group.")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        requestCameraPermission();
                    }
                })
                .create().show();
        }
    }

    private void startCameraPreviewIfReady() {
        if (mCameraSource == null) {
            // Not ready yet (we might be asking for permission). Will return here later.
            Logger.d(TAG, "startCameraPreviewIfReady() - no camera source available");
            return;
        }

        if (!mSurfaceReady) {
            Logger.d(TAG, "startCameraPreviewIfReady() - surface is not ready");
            return;
        }

        try {
            mCameraSource.start(surfaceView_camera.getHolder());
        } catch (IOException |SecurityException e) {
            Logger.e(TAG, "Failed to start camera preview", e);
        }
    }


    private void stopCameraPreviewIfRunning() {
        if (mCameraSource != null) {
            mCameraSource.stop();
        }
    }


    private void releaseCameraPreviewIfRunning() {
        if (mCameraSource != null) {
            mCameraSource.release();
            mCameraSource = null;
        }
    }
}
