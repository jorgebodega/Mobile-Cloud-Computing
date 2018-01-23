package fi.aalto.narcolepticninjas.shareapicture;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.IOException;

import fi.aalto.narcolepticninjas.shareapicture.backend.BackendApi;
import fi.aalto.narcolepticninjas.shareapicture.backend.BackendApiResponseCallback;
import okhttp3.Call;
import okhttp3.Response;

public class ManageGroupsActivity extends AppCompatActivity {

    private static final String TAG = ManageGroupsActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_groups);

        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        String groupId = Helpers.getActiveGroup(this);
        Logger.d(TAG, "onResume(): Currently active group is: %s", groupId);

        if (groupId == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.manage_groups_root, new NoActiveGroupFragment())
                .commit();
        } else {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.manage_groups_root, new GroupInfoFragment())
                .commit();
        }
    }

    public void doCreateGroup(View view) {
        startActivity(CreateGroupActivity.class);
    }

    public void doJoinGroup(View view) {
        startActivity(JoinToGroupActivity.class);
    }

    private void startActivity(Class<?> cls) {
        Logger.d(TAG, "Starting activity %s", cls.getSimpleName());
        startActivity(new Intent(this, cls));
    }

    public void doAddUser(View view) {
        Logger.d(TAG, "Showing QR code for joining a group");

        // TODO: Start an activity that displays the QR code needed to join the group
        startActivity(GetGroupJoinCodeActivity.class);
    }

    public void doLeaveGroup(View view) {
        Logger.d(TAG, "Leaving currently active group");

        String groupId = Helpers.getActiveGroup(this);
        if (groupId == null) {
            handleGroupNoLongerActive();
            return;
        }

        // Leave the group but don't wait for confirmation. Whatever the server
        // thinks about the user is not that important as the client state is
        // what matters.
        BackendApi.leaveGroup(groupId, new LeaveOrDeleteGroupResponseCallback());
        handleGroupReset();
    }

    public void doDeleteGroup(View view) {
        Logger.d(TAG, "Deleting currently active group immediately");

        String groupId = Helpers.getActiveGroup(this);
        if (groupId == null) {
            handleGroupNoLongerActive();
            return;
        }

        // Delete the group but don't wait for a confirmation. If the operation fails,
        // cleanup will handle it after a while anyway. So there's no need to make
        // this too robust.
        BackendApi.deleteGroup(groupId, new LeaveOrDeleteGroupResponseCallback());
        handleGroupReset();
    }

    private class LeaveOrDeleteGroupResponseCallback extends BackendApiResponseCallback {
        @Override
        public void onFailure(Call call, IOException e) {
            Logger.e(TAG, "Operation failed", e);
        }

        @Override
        public void onResponse(final Call call, final Response response, final JSONObject body) {
            if (response.code() > 299) {
                Logger.e(TAG, "Server returned error code %d", response.code());
                return;
            }

            Logger.d(TAG, "Operation successful");
        }
    }

    private void handleGroupNoLongerActive() {
        Toast.makeText(this, "Group is no longer active...", Toast.LENGTH_LONG).show();
        finish();
    }

    private void handleGroupReset() {
        Toast.makeText(this, "Group removed...", Toast.LENGTH_LONG).show();
        Helpers.setActiveGroup(this, null, null);
        finish();
    }

}
