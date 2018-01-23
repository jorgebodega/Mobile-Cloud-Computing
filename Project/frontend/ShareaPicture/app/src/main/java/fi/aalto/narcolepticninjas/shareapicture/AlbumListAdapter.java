package fi.aalto.narcolepticninjas.shareapicture;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.squareup.picasso.Picasso;
import fi.aalto.narcolepticninjas.shareapicture.model.FirebasePicture;

import java.util.List;

/**
 * Created by Ari on 6.12.2017.
 */

public class AlbumListAdapter extends ArrayAdapter {

    private static final String TAG = "AlbumListAdapter";
    private final int layoutResource;
    private final LayoutInflater layoutInflater;
    private final int width;
    private final String mGroupId;
    private List<FirebasePicture> pictures;
    private Context context;
    private StorageReference mStorage;

    public AlbumListAdapter(Context context, int resource, List<FirebasePicture> pictures, int width, String group_id) {

        super(context, resource);

        Log.d(TAG, "AlbumListAdapter: In constructor");

        this.layoutResource = resource;
        this.layoutInflater = LayoutInflater.from(context);
        this.pictures = pictures;

        this.context = context;
        this.width = width;

        this.mStorage = FirebaseStorage.getInstance().getReference();
        this.mGroupId = group_id;

    }

    @Override
    public int getCount() {
        return this.pictures.size();
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {

        final int local_position = position;

        // luodaan ViewHolder
        final ViewHolder viewHolder;

        if (convertView == null) {
            convertView = layoutInflater.inflate(layoutResource, parent, false);
            viewHolder = new ViewHolder(convertView);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }
        viewHolder.ivPhoto.getLayoutParams().height = width;
        viewHolder.ivPhoto.getLayoutParams().width = width;

        switch (pictures.get(position).type) {
            case "firebase":

                mStorage.child("images").child(mGroupId).child("image").child(pictures.get(position).firebase_id)
                        .child("low.jpg").getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {
                        // Got the download URL for 'users/me/profile.png'
                        Log.d(TAG, "onSuccess: uri" + uri);
                        Picasso.with(context).load(uri.toString()).resize(width, width).centerCrop().into(viewHolder.ivPhoto);
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        Log.e(TAG, "onFailure: Could not get download url for picture from Firebase", exception );
                    }
                });
                return convertView;
//                break; // not necessary ?
            case "local":

                FirebasePicture currentPhoto = pictures.get(position);
                viewHolder.ivPhoto.getLayoutParams().height = width;
                viewHolder.ivPhoto.getLayoutParams().width = width;
                Picasso.with(context).load(currentPhoto.getPhotoUrl()).resize(width, width).centerCrop().into(viewHolder.ivPhoto);

                return convertView;
            default:
                Log.d(TAG, "getView: Missing Picture type information.");
                return convertView;
        }

    }

    private class ViewHolder {
        final ImageView ivPhoto;

        public ViewHolder(View v) {
            this.ivPhoto = (ImageView) v.findViewById(R.id.ivPhoto);
        }

    }

}
