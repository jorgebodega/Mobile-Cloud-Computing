package fi.aalto.narcolepticninjas.shareapicture;

/**
 * Created by sujay_khandagale on 05/12/17.
 */

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

import com.google.firebase.database.*;
import fi.aalto.narcolepticninjas.shareapicture.model.Group;

import java.util.Date;

import static android.support.v4.app.NotificationManagerCompat.IMPORTANCE_HIGH;

public class MyService extends Service {
    private static final String TAG = MyService.class.getSimpleName();
    private DatabaseReference mMemberListenerNode;
    private DatabaseReference mImageListenerNode;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private DatabaseReference mDatabase;
    private ChildEventListener mMemberListener;
    private ValueEventListener mImageListener;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.d(TAG, "Starting background service");

        String groupId = Helpers.getActiveGroup(this);
        if (groupId == null) {
            Logger.w(TAG, "Service started without active group!");
            return START_STICKY;
        }

        mDatabase = FirebaseDatabase.getInstance().getReference();
        if (mMemberListener != null) {
            mMemberListenerNode.removeEventListener(mMemberListener);
        }
        mMemberListenerNode = mDatabase.child("group_members").child(groupId);
        mMemberListener = mMemberListenerNode.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                Logger.d(TAG, "mMemberListener:onChildAdded");
                String name = dataSnapshot.getValue(String.class);
                String uid = dataSnapshot.getKey();
                // Only show notification if 1) we have a name and 2) the user is not the
                // current user
                if (name != null && !uid.equals(Helpers.getActiveUserId())) {
                    addNotification(getResources().getString(R.string.notification_new_member, name), ManageGroupsActivity.class);
                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                Logger.d(TAG, "mMemberListener:onChildChanged");
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                Logger.d(TAG, "mMemberListener:onChildRemoved");
                String name = dataSnapshot.getValue(String.class);
                if (name != null) {
                    addNotification(getResources().getString(R.string.notification_old_member, name), ManageGroupsActivity.class);
                }

            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {
                Logger.d(TAG, "mMemberListener:onChildMoved");
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Logger.d(TAG, "mMemberListener:onCancelled", databaseError.toException());
                if (databaseError.getCode() == DatabaseError.PERMISSION_DENIED) {
                    // Group deleted, maybe by the owner. Reset the state
                    Helpers.setActiveGroup(MyService.this, null, null);
                }
            }
        });

        if (mImageListener != null) {
            mImageListenerNode.removeEventListener(mImageListener);
        }
        mImageListenerNode = mDatabase.child("images").child(groupId);
        mImageListener = mImageListenerNode.addValueEventListener(new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot snapshot) {
                //Do something using DataSnapshot say call Notification
                if(snapshot.getValue() != null) {
                    addNotification("You have new images in your group.", GalleryActivity.class);
                    Logger.d(TAG, "The snapshot data: %s", snapshot.getValue().toString());
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Logger.w(TAG,"mImageListener::onCancelled", error.toException());
                if (error.getCode() == DatabaseError.PERMISSION_DENIED) {
                    // Group deleted, maybe by the owner. Reset the state
                    Helpers.setActiveGroup(MyService.this, null, null);
                }
            }
        });

        mDatabase.child("groups").child(groupId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Group group = dataSnapshot.getValue(Group.class);
                if (group == null) {
                    Logger.wtf(TAG, "Failed to parse group info.");
                    return;
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Logger.w(TAG,"mGroupListener::onCancelled", databaseError.toException());
            }
        });

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.d(TAG, "onDestroy()");
        if (mDatabase != null) {
            if (mMemberListener != null) {
                mMemberListenerNode.removeEventListener(mMemberListener);
            }

            if (mImageListener != null) {
                mImageListenerNode.removeEventListener(mImageListener);
            }
        }


        mImageListener = null;
        mMemberListener = null;
        mDatabase = null;
    }

    private void addNotification(String str, final Class<? extends Activity>  ActivityToGo) {
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, ActivityToGo), 0);
        Resources r = getResources();
        Notification notification = new NotificationCompat.Builder(this)
                .setSmallIcon(android.R.drawable.ic_menu_report_image)
                .setContentTitle("Photo Share")
                .setContentText(str)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(IMPORTANCE_HIGH)
                .build();

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(0, notification);
    }
}
