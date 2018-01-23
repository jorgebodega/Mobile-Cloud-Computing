package fi.aalto.narcolepticninjas.shareapicture;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;


public class PictureActivity extends AppCompatActivity {
    private static final String TAG = PictureActivity.class.getSimpleName();
    private static final int REQUEST_TAKE_PHOTO = 1;
    private static final int RC_CAMERA_PERMISSION_REQUEST = 73;

    // stores uri to picture taken
    Uri mCurrentPhotoURI;

    // for writing directly to Firebase database
    private DatabaseReference mDatabase;
    private StorageReference mStorage;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picture);

        mDatabase = FirebaseDatabase.getInstance().getReference();
        mStorage = FirebaseStorage.getInstance().getReference();

        int rc = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            dispatchTakePictureIntent();
        } else {
            requestCameraPermission();
        }
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            Log.d(TAG, "dispatchTakePictureIntent: Here");
            try {
                photoFile = createImageFile();
                Log.d(TAG, "dispatchTakePictureIntent: Created");
            } catch (IOException ex) {
                // Error occurred while creating the File
                Log.d(TAG, "dispatchTakePictureIntent: " + ex.getMessage());
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.android.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI); //
                mCurrentPhotoURI = photoURI;
                Log.d(TAG, "dispatchTakePictureIntent: " + takePictureIntent.getExtras());
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);

            } else {
                Log.d(TAG, "dispatchTakePictureIntent: photoFile is null");
            }
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
            dispatchTakePictureIntent();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Access to Camera Required")
                    .setMessage("You need to allow camera access to take a picture.")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            requestCameraPermission();
                        }
                    })
                    .create().show();
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";

        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        Log.d(TAG, "createImageFile: " + storageDir);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Logger.d(TAG, "onActivityResult(requestCode=%d, resultCode=%d)", requestCode, resultCode);
        if (requestCode == REQUEST_TAKE_PHOTO) {
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "onActivityResult: Starting to process image");
                (new ProcessPictureTask(this) {
                    @Override
                    protected void onPreExecute() {
                        super.onPreExecute();
                        // TODO: Show a progress spinner or something here
                    }

                    @Override
                    protected void onPostExecute(Bitmap bitmap) {
                        super.onPostExecute(bitmap);
                        if (bitmap == null) {
                            Logger.d(TAG, "Private image detected. Not doing the upload");
                            Toast.makeText(
                                PictureActivity.this, getString(R.string.picture_activity_toast_private_image), Toast.LENGTH_LONG).show();
                            finish();
                        } else {
                            Logger.d(TAG, "Image processing finished. Starting to upload");
                            sendPhotoToBackend(bitmap);
                        }
                    }
                }).execute(mCurrentPhotoURI);
            } else if (resultCode == RESULT_CANCELED) {
                Logger.d(TAG, "onActivityResult: user cancelled image capture");
                finish();
            }
        }
    }



    private void sendPhotoToBackend(Bitmap image) {
        final String groupId = Helpers.getActiveGroup(this);
        final String imageKeyPrefix = String.format("/images/%s", groupId);

        final DatabaseReference dbRef = mDatabase.child(imageKeyPrefix).push();
        final String imageId = dbRef.getKey();

        Logger.d(TAG, "Uploading image: groupId=%s, imageId=%s", groupId, imageId);

        final byte[] data = encodeImage(image);

        final String imageKey = String.format("/images/%s/image/%s/original.jpg", groupId, imageId);
        final StorageReference imageRef = mStorage.child(imageKey);
        UploadTask uploadTask = imageRef.putBytes(data);
        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
            // Handle unsuccessful uploads
            Logger.e(TAG, "Upload failed", exception);
            showError();
            finish();
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
            Logger.d(TAG, "Image upload succeeded. Updating metadata in RTDB");
            String imageUrl = taskSnapshot.getDownloadUrl().toString();

            HashMap<String, Object> metadata = new HashMap<>();
            metadata.put("upload_time", Helpers.dateToIsoString(new Date()));
            metadata.put("uploader", Helpers.getActiveUserId());
            metadata.put("uri", imageUrl);
            metadata.put("faces", 0);
            metadata.put("uploader_name", Helpers.getActiveUserName());

            dbRef.setValue(metadata).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Logger.e(TAG, "Failed to update metadata in RTDB!", e);
                    showError();
                    finish();
                }
            }).addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Logger.d(TAG, "RTDB updated!");
                    Toast.makeText(PictureActivity.this, "Image uploaded!", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
            }
        }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                int progress = (int) Math.round(100.0 * taskSnapshot.getBytesTransferred() / taskSnapshot.getTotalByteCount());
                Logger.d(TAG, "Setting progress to %d", progress);
                ((ProgressBar) findViewById(R.id.picture_upload_progress)).setProgress(progress);
            }
        });
    }

    private void showError() {
        Toast.makeText(PictureActivity.this, getString(R.string.picture_activity_toast_upload_failed), Toast.LENGTH_LONG).show();
    }

    private byte[] encodeImage(Bitmap image) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        return baos.toByteArray();
    }
}
