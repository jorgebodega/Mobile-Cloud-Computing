package fi.aalto.narcolepticninjas.shareapicture;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.firebase.ui.database.FirebaseListAdapter;
import com.firebase.ui.database.FirebaseListOptions;
import com.google.firebase.database.*;
import fi.aalto.narcolepticninjas.shareapicture.model.Group;

import java.text.DateFormat;
import java.util.Date;
import java.util.Objects;

public class GroupInfoFragment extends Fragment {
    private static final String TAG = GroupInfoFragment.class.getSimpleName();

    private DatabaseReference mDatabase;
    private ValueEventListener mGroupMetadataListener;
    private FirebaseListAdapter<String> mGroupMemberAdapter;
    private DatabaseReference mGroupMetadataListenerNode;

    public GroupInfoFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDatabase = FirebaseDatabase.getInstance().getReference();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_group_info, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        Logger.d(TAG, "onStart() called");
        String groupId = Helpers.getActiveGroup(getActivity());
        if (groupId == null) {
            Logger.d(TAG, "No active group. Not attaching listeners");
            return;
        }

        mGroupMetadataListenerNode = mDatabase.child("groups").child(groupId);
        mGroupMetadataListener = mGroupMetadataListenerNode.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Group group = dataSnapshot.getValue(Group.class);
                Logger.d(TAG, "Got group: %s", group);

                if (!isAdded()) {
                    Logger.d(TAG, "Ignoring update while Fragment not attached");
                    return;
                }

                if (group == null) {
                    Logger.d(TAG, "No group found. It must have been deleted");
                    handleGroupDeleted();
                    return;
                }

                DateFormat df = android.text.format.DateFormat.getLongDateFormat(getActivity());
                DateFormat tf = android.text.format.DateFormat.getTimeFormat(getActivity());

                Date expiry = group.getExpiryDate();
                String expiryLabel = getResources().getString(
                    R.string.manage_group_expiry,
                    df.format(expiry),
                    tf.format(expiry)
                );
                ((TextView) getView().findViewById(R.id.manage_group_name)).setText(group.name);
                ((TextView) getView().findViewById(R.id.manage_group_expiry)).setText(expiryLabel);

                if (Objects.equals(Helpers.getActiveUserId(), group.admin)) {
                    // User is the owner of the group. Show delete, hide leave
                    getView().findViewById(R.id.manage_group_delete_btn).setVisibility(View.VISIBLE);
                    getView().findViewById(R.id.manage_group_leave_btn).setVisibility(View.GONE);
                } else {
                    // User is just a member. Show leave, hide delete
                    getView().findViewById(R.id.manage_group_delete_btn).setVisibility(View.GONE);
                    getView().findViewById(R.id.manage_group_leave_btn).setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Logger.w(TAG, "group:onCancelled", databaseError.toException());
                if (databaseError.getCode() == DatabaseError.PERMISSION_DENIED) {
                    Logger.d(TAG, "No permission to access the group. The group must have been deleted");
                    handleGroupDeleted();
                    return;
                }
            }
        });

        Query query = mDatabase.child("group_members").child(groupId).orderByValue();
        FirebaseListOptions<String> options = new FirebaseListOptions.Builder<String>()
            .setQuery(query, String.class)
            .setLayout(R.layout.member_list_item)
            .build();

        mGroupMemberAdapter = new FirebaseListAdapter<String>(options) {
            @Override
            protected void populateView(View v, String value, int position) {
                ((TextView) v).setText(value);
            }
        };

        ((ListView) getActivity().findViewById(R.id.manage_group_members))
            .setAdapter(mGroupMemberAdapter);
        ((ListView) getActivity().findViewById(R.id.manage_group_members))
            .setEmptyView(getActivity().findViewById(R.id.manage_group_loading_members));

        mGroupMemberAdapter.startListening();
    }

    @Override
    public void onStop() {
        super.onStop();
        Logger.d(TAG, "onStop() called");
        if (mGroupMetadataListener != null && mDatabase != null) {
            mGroupMetadataListenerNode.removeEventListener(mGroupMetadataListener);
            mGroupMetadataListener = null;
            mGroupMetadataListenerNode = null;
        }

        if (mGroupMemberAdapter != null) {
            mGroupMemberAdapter.stopListening();
            mGroupMemberAdapter = null;
        }
    }

    private void handleGroupDeleted() {
        if (!isAdded()) {
            return;
        }
        Toast.makeText(getActivity(), "Group Deleted", Toast.LENGTH_SHORT).show();
        Helpers.setActiveGroup(getActivity(), null, null);
        getActivity().finish();
    }
}
