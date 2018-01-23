package fi.aalto.narcolepticninjas.shareapicture;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ViewPictureActivity extends AppCompatActivity {

    private static final String TAG = "ViewPictureActivity";
    private Target mTarget;
    private StorageReference mStorage;
    private static final int PERMISSION_REQUEST_CODE = 1;

    private String mGroupId;
    private String mImageId;
    private String mImageType;
    private String mLocalPath;
    private File mTempFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_picture);

        mStorage = FirebaseStorage.getInstance().getReference();

        Intent intent = getIntent();
        mImageType = intent.getStringExtra("type");
        mGroupId = intent.getStringExtra("group_id");
        mImageId = intent.getStringExtra("picture_id");
        mLocalPath = intent.getStringExtra("path");

        Logger.d(TAG, "Displaying the following picture: type=%s, group_id=%s, image_id=%s, local_path=%s", mImageType, mGroupId, mImageId, mLocalPath);
        switch (mImageType) {
            case "firebase":
                String resolution = Helpers.chooseResolutionForUpload(ViewPictureActivity.this);
                displayFirebase(mGroupId, mImageId, resolution);
                break;
            case "local":
                displayImage(mLocalPath);
                break;
            default:
                Log.d(TAG, "onItemClick: Unexpected image type");
                finish();
        }
    }

    private void displayImage(String path) {
        final SubsamplingScaleImageView imageView = findViewById(R.id.imageView);

        Uri uri = Uri.parse(path);
        if (uri.getScheme().equals("file")) {
            imageView.setImage(ImageSource.uri(uri));
            return;
        }

        Logger.wtf(TAG, "Image URI has unsupported scheme: %s", uri);
    }

    private void displayFirebase(final String group_id, final String picture_id, final String resolution) {

        String filename = "original.jpg";
        switch (resolution) {
            case "low":
                filename = "low.jpg";
                break;
            case "high":
                filename = "high.jpg";
                break;
        }

        File outputDir = getCacheDir();
        final File tempFile;
        try {
            tempFile = File.createTempFile("prefix", "extension", outputDir);
        } catch (IOException e) {
            Logger.e(TAG, "Failed to create temp file", e);
            Toast.makeText(this, R.string.toast_generic_error, Toast.LENGTH_LONG).show();
            return;
        }

        tempFile.deleteOnExit();

        mStorage
            .child("images")
            .child(group_id)
            .child("image")
            .child(picture_id)
            .child(filename)
            .getFile(tempFile)
            .addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                    displayImage("file://" + tempFile.getAbsolutePath());
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception exception) {

                    switch (resolution) {
                        case "original":
                            Log.d(TAG, "onFailure: Original not found. Trying high.");
                            displayFirebase(group_id, picture_id, "high");
                            break;
                        case "high":
                            Log.d(TAG, "onFailure: High not found. Trying low.");
                            displayFirebase(group_id, picture_id, "low");
                            break;
                        default:
                            Log.e(TAG, "onFailure: Could not get download url for picture from Firebase", exception );
                    }

                }
            });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.download_image, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.download);

        if (mImageType.equals("local")) {
            item.setEnabled(false);
        } else {
            item.setEnabled(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Logger.d(TAG, "onOptionsItemSelected(%d)", item.getItemId());
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.download:
                if (Build.VERSION.SDK_INT >= 23)
                {
                    if (checkPermission())
                    {
                        downloadFile();
                    } else {
                        requestPermission();
                    }
                }
                else
                {
                    downloadFile();
                }
                return true;
        }
        return false;
    }

    public void downloadFile() {

        if (mImageType.equals("local")) {
            Logger.d(TAG, "Not downloading local file");
            // XXX: The file is already on-disk. We will not do this.
            return;
        }

        Logger.d(TAG, "Downloading remote file");
        Toast.makeText(this, "Starting download...", Toast.LENGTH_SHORT).show();
        mStorage
            .child("images")
            .child(mGroupId)
            .child("image")
            .child(mImageId)
            .child("original.jpg")
            .getDownloadUrl()
            .addOnSuccessListener(new OnSuccessListener<Uri>() {
                @Override
                public void onSuccess(Uri uri) {
                    downloadUri(uri);
                }
            }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                Toast.makeText(ViewPictureActivity.this, R.string.toast_generic_error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void downloadUri(Uri uri) {
        Logger.d(TAG, "Going to download image from URI: %s", uri);
        DownloadManager mgr = (DownloadManager) ViewPictureActivity.this.getSystemService(Context.DOWNLOAD_SERVICE);

        DownloadManager.Request request = new DownloadManager.Request(uri);

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + ".jpg";

        request.setAllowedNetworkTypes(
            DownloadManager.Request.NETWORK_WIFI
                | DownloadManager.Request.NETWORK_MOBILE)
            .setAllowedOverRoaming(false).setTitle("Image is being downloaded...")
            .setDescription("Something useful. No, really.")
            .setDestinationInExternalPublicDir("/Share-a-Picture", imageFileName);
        mgr.enqueue(request);
    }

    private boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(ViewPictureActivity.this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (result == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            return false;
        }
    }

    private void requestPermission() {

        if (ActivityCompat.shouldShowRequestPermissionRationale(ViewPictureActivity.this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Toast.makeText(ViewPictureActivity.this, "Write External Storage permission allows us to do store images. Please allow this permission in App Settings.", Toast.LENGTH_LONG).show();
        } else {
            ActivityCompat.requestPermissions(ViewPictureActivity.this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.e("value", "Permission Granted, Now you can use local drive .");
                    downloadFile();
                } else {
                    Log.e("value", "Permission Denied, You cannot use local drive .");
                }
                break;
        }
    }

}
