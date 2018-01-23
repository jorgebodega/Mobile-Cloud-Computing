package fi.aalto.narcolepticninjas.shareapicture;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import fi.aalto.narcolepticninjas.shareapicture.model.FirebasePicture;
//import fi.aalto.narcolepticninjas.shareapicture.model.Picture;

public class AlbumActivity extends AppCompatActivity {

    // TODO Currently we always download the original picture from the backend (uri points to it). This is not fully according to specs.
    // Probably we should in this album activity use the lowest quality image.

    // TODO Eventlisteners not removed currently.

    private static final String TAG = "AlbumActivity";
    private static final String BUNDLE_SORT_FACTOR = "BUNDLE_SORT_FACTOR";

    private String mSortFactor;
    private DatabaseReference mDatabase;
    private ValueEventListener mImageListener;
    private String mGroupId;
    private DataSnapshot mDatasnapshot;
    private StorageReference mStorage;
    private DatabaseReference mImageListenerNode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album);

        mDatabase = FirebaseDatabase.getInstance().getReference();
        mStorage = FirebaseStorage.getInstance().getReference();

        // Set sorting initially based on people (if faces present)
        mSortFactor = "face";
        if (savedInstanceState != null) {
            mSortFactor = savedInstanceState.getString(BUNDLE_SORT_FACTOR);
        }

    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent intent = getIntent();
        String group_id = intent.getStringExtra("group_id");
        this.mGroupId = group_id;

        if (group_id.equals("private")) {
            processPrivate();
        } else {
            processFirebase(group_id);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        if (!mGroupId.equals("private")) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.menu_album, menu);
            return true;
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Logger.d(TAG, "onOptionsItemSelected(%d)", item.getItemId());
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.by_author:
                mSortFactor = "uploader";
                showPictures(mDatasnapshot.getChildren());
                return true;
            case R.id.by_peopleDetection:
                mSortFactor = "face";
                showPictures(mDatasnapshot.getChildren());
                return true;
        }
        return false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString(BUNDLE_SORT_FACTOR, mSortFactor);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Logger.d(TAG, "onStop()");
        if (mDatabase != null) {
            if (mImageListener != null) {
                Logger.d(TAG, "Removing listener");
                mImageListenerNode.removeEventListener(mImageListener);
            }
        }

        mImageListener = null;
        mImageListenerNode = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mDatabase = null;
    }

    private void processFirebase(String group_id) {

        // LISTENING DATABASE
        if (mImageListener != null) {
            mImageListenerNode.removeEventListener(mImageListener);
        }
        mImageListenerNode = mDatabase.child("images").child(group_id);
        mImageListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.d(TAG, "onDataChange: Backend data change" );
                Iterable<DataSnapshot> children = dataSnapshot.getChildren();

                // Save the snapshot for sort changes
                mDatasnapshot = dataSnapshot;
                showPictures(children);

            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Getting Post failed, log a message
                Log.w(TAG, "loadPost:onCancelled", databaseError.toException());
                // ...
            }
        };
        mImageListenerNode.addValueEventListener(mImageListener);
    }

    private void processPrivate() {
        Log.d(TAG, "onCreate: Displaying private pictures");

        ArrayList<FirebasePicture> pictures = new ArrayList<>();

        HashMap<String, ArrayList<FirebasePicture>> groups = new HashMap<>();
        groups.put("Local Pictures", pictures);

        File privatePath = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "private");
        if (!privatePath.isDirectory()) {
            Logger.d(TAG, "path %s is not a directory; no private images stored", privatePath);

            // Render an empty group to show a message that there is no images
            renderImageGroups(groups);
            return;
        }

        for (File file : privatePath.listFiles()) {
            pictures.add(new FirebasePicture(file.toURI().toString(), "local"));
        }

        renderImageGroups(groups);
    }

    private void showPictures(Iterable<DataSnapshot> children) {

        HashMap<String, ArrayList<FirebasePicture>> imageGroups = new HashMap<>();

        switch (mSortFactor) {
            case "face":

                ArrayList<FirebasePicture> peoplePictures = new ArrayList<FirebasePicture>();
                ArrayList<FirebasePicture> noPeoplePictures = new ArrayList<FirebasePicture>();

                for (DataSnapshot ds : children) {
                    final FirebasePicture picture_metadata = ds.getValue(FirebasePicture.class);
                    picture_metadata.firebase_id = ds.getKey();
                    picture_metadata.type = "firebase";

                    if (picture_metadata.faces == 0) {
                        noPeoplePictures.add(picture_metadata);
                    } else {
                        peoplePictures.add(picture_metadata);
                    }

                }

                imageGroups.put("No People", noPeoplePictures);
                imageGroups.put("People", peoplePictures);

                renderImageGroups(imageGroups);

                break;
            case "uploader":

                // Create image groups for all uploaders found in the data
                for (DataSnapshot ds : children) {
                    FirebasePicture picture_metadata = ds.getValue(FirebasePicture.class);
                    picture_metadata.firebase_id = ds.getKey();
                    picture_metadata.type = "firebase";

                    if (!imageGroups.containsKey(picture_metadata.uploader_name)) {
                        imageGroups.put(picture_metadata.uploader_name, new ArrayList<FirebasePicture>());
                    }

                    imageGroups.get(picture_metadata.uploader_name).add(picture_metadata);
                }

                renderImageGroups(imageGroups);
                break;
            default:
                Logger.wtf(TAG, "onDataChange: Unexpected sort factor. Not doing anything");
                break;
        }
    }

    private void renderImageGroups(HashMap<String, ArrayList<FirebasePicture>> imageGroups) {

        // First, remove previously rendered views
        ((LinearLayout) findViewById(R.id.group_container)).removeAllViews();

        // Then, render all groups
        for (String key: imageGroups.keySet()) {
            createImageGroup(key, imageGroups.get(key));
        }
    }

    private void createImageGroup(String title, final ArrayList<FirebasePicture> pictures) {

        LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LinearLayout root = (LinearLayout) inflater.inflate(R.layout.album_category, null);

        // Set the group label
        ((TextView) root.findViewById(R.id.group_label)).setText(title);


        // Configure the image grid
        AlbumListAdapter adapter = new AlbumListAdapter(AlbumActivity.this, R.layout.picture_element, pictures, getImageWidth(), mGroupId);

        GridView piclist = root.findViewById(R.id.lvPictureList);
        piclist.setAdapter(adapter);
        piclist.setEmptyView(root.findViewById(R.id.no_images));

        // Set a listener for each photo to start ViewPictureActivity
        piclist.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(AlbumActivity.this, ViewPictureActivity.class);

                String path = pictures.get(position).uri;
                intent.putExtra("path", path);

                intent.putExtra("type", pictures.get(position).type);
                intent.putExtra("group_id", mGroupId);
                intent.putExtra("picture_id", pictures.get(position).firebase_id);

                                // ListEntry entry = (ListEntry) parent.getItemAtPosition(position);
                Log.d(TAG, "onItemClick: About to launch View Picture Activity");
                startActivity(intent);
            }
        });

        ((LinearLayout) findViewById(R.id.group_container)).addView(root);
    }

    private int getImageWidth() {
        DisplayMetrics metrics = Helpers.getDisplayMetrics(this);

        int screenWidth = metrics.widthPixels;
        return Math.round(screenWidth / 4f);
    }

}
