package fi.aalto.narcolepticninjas.shareapicture;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.GridView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.LinkedList;

public class GalleryActivity extends AppCompatActivity {

    private static final String TAG = GalleryActivity.class.getSimpleName();

    private GridView mGrid;
    private DatabaseReference mDatabase;
    private ValueEventListener mUserGroupsListener;
    private DatabaseReference mUserGroupsListenerNode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        mGrid = findViewById(R.id.gallery_view);
        mDatabase = FirebaseDatabase.getInstance().getReference();
    }

    @Override
    protected void onStart() {
        super.onStart();

        Logger.d(TAG, "Starting to listen for user groups changes");
        mUserGroupsListenerNode = mDatabase.child("users").child(Helpers.getActiveUserId()).child("groups");
        mUserGroupsListener = mUserGroupsListenerNode.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                LinkedList<DataSnapshot> dataList = new LinkedList<>();
                dataList.add(0, null);

                for(DataSnapshot ds : dataSnapshot.getChildren()){
                    dataList.add(ds);
                }

                mGrid.setAdapter(new GalleryAdapter(getApplicationContext(), dataList, getCardWidth()));
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Logger.d(TAG, "mUserGroupsListener:onCancelled", databaseError.toException());
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mDatabase != null) {
            if (mUserGroupsListener != null) {
                mUserGroupsListenerNode.removeEventListener(mUserGroupsListener);
            }
        }

        mUserGroupsListener = null;
        mUserGroupsListenerNode = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mDatabase = null;
    }

    private int getCardWidth() {
        DisplayMetrics metrics = Helpers.getDisplayMetrics(this);
        int width = metrics.widthPixels;

        return Math.round((float) (width - 24) / 2f);
    }
}


