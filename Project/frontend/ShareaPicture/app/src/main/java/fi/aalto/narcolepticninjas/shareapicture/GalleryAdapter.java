package fi.aalto.narcolepticninjas.shareapicture;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.util.LinkedList;

import fi.aalto.narcolepticninjas.shareapicture.model.FirebasePicture;
import fi.aalto.narcolepticninjas.shareapicture.model.Group;
/**
 * Created by Jorge on 01/12/2017.
 */


public class GalleryAdapter extends BaseAdapter {

    private static final String TAG = MenuActivity.class.getSimpleName();
    private final int imageWidth;
    private final int imageHeight;

    private Context context;
    private LinkedList<DataSnapshot> album;
    private final static String PRIVATE_IMAGE = "ic_cloud_off_black_24dp";
    private final static String PUBLIC_IMAGE = "ic_cloud_queue_black_24dp";

    public GalleryAdapter(Context context, LinkedList<DataSnapshot> album, int cardWidth) {
        this.imageWidth = cardWidth;
        this.imageHeight = Math.round(9f / 16f * cardWidth);
        this.context = context;
        this.album = album;
    }

    @Override
    public DataSnapshot getItem(int position) {
        return album.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getCount() {
        return album.size();
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View item = inflater.inflate(R.layout.gallery_card, parent, false);

        // Views of the cards
        final CardView card = item.findViewById(R.id.card_view);
        final TextView lblName = card.findViewById(R.id.info_text_name);
        final TextView lblUsers = card.findViewById(R.id.info_text_nimages);
        final ImageView lblPrincipal = card.findViewById(R.id.album_photo);

        // Set the card layout params depending on the screen size
        lblPrincipal.getLayoutParams().width = imageWidth;
        lblPrincipal.getLayoutParams().height = imageHeight;


        // Private album is always first. If that's the one, render it separately
        if (position == 0) {
            return localFiles(item);
        }

        //Starting to do querys, to obtain information from the database
        final String actualKey = getItem(position).getKey();
        DatabaseReference db = FirebaseDatabase.getInstance().getReference();

        db.child("groups").child(actualKey).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Group album = dataSnapshot.getValue(Group.class);
                lblName.setText(album.name);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d(TAG, "onCancelled:", databaseError.toException());
            }
        });

        db.child("images").child(actualKey).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Logger.d(TAG, "Found %d images for group %s", dataSnapshot.getChildrenCount(), actualKey);
                int images = (int) dataSnapshot.getChildrenCount();
                lblUsers.setText(context.getResources().getQuantityString(R.plurals.number_of_images, images, images));

                if (dataSnapshot.getChildrenCount() > 0) {
                    FirebasePicture pic = dataSnapshot.getChildren().iterator().next().getValue(FirebasePicture.class);
                    Picasso.with(context).load(pic.getPhotoUrl()).resize(imageWidth, imageHeight).centerCrop().placeholder(R.drawable.ic_photo_black_24dp).into(lblPrincipal);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d(TAG, "onCancelled:");
            }
        });

        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, AlbumActivity.class);
                intent.putExtra("group_id", actualKey);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        });
        return (item);
    }

    /**
     * Add a new view for the private album, locally storaged.
     *
     * @param view View
     * @return view Modified view with "private" album info.
     */
    private View localFiles(View view){
        File privatePath = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "private");
        if(!privatePath.exists()) privatePath.mkdir();
        if (!privatePath.isDirectory()) {
            Logger.d(TAG, "path %s is not a directory; no private images stored", privatePath);
        }
        File[] localFiles = privatePath.listFiles();
        TextView lblName = view.findViewById(R.id.info_text_name);
        lblName.setText(R.string.lbl_private);

        TextView lblFiles = view.findViewById(R.id.info_text_nimages);
        lblFiles.setText(context.getResources().getQuantityString(R.plurals.number_of_images, localFiles.length, localFiles.length));

        ImageView lblPrincipal = view.findViewById(R.id.album_photo);
        if (localFiles.length != 0) {
            String path = "file://" + localFiles[0].getAbsolutePath();
            Picasso.with(context)
                .load(path)
                .placeholder(R.drawable.ic_photo_black_24dp)
                .resize(imageWidth, imageHeight)
                .centerCrop()
                .into(lblPrincipal);
        }


        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, AlbumActivity.class);
                intent.putExtra("group_id", "private");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        });

        return view;
    }
}
