package fi.aalto.narcolepticninjas.shareapicture;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Writer;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class GetGroupJoinCodeActivity extends AppCompatActivity {

    private static final String TAG = GetGroupJoinCodeActivity.class.getSimpleName();

    TextView txt_info;
    ImageView img_qrCode;

    private DatabaseReference mDatabase;
    private ValueEventListener mGroupMetadataListener;
    private DatabaseReference mGroupMetadataListenerNode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_get_group_join_code);

        txt_info = findViewById(R.id.txt_info);
        img_qrCode = findViewById(R.id.img_qrCode);
        mDatabase = FirebaseDatabase.getInstance().getReference();
    }

    @Override
    public void onStart() {
        super.onStart();
        Logger.d(TAG, "onStart() called");
        final String groupId = Helpers.getActiveGroup(this);
        mGroupMetadataListenerNode = mDatabase.child("tokens").child(groupId);
        mGroupMetadataListener = mGroupMetadataListenerNode.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String token = dataSnapshot.getValue(String.class);
                String qrCodeString = token + "&id=" + groupId;
                txt_info.setText("Join the group using this QR code:");
                try {
                    generateQRCode(qrCodeString, img_qrCode);
                } catch (WriterException e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(), "Unable to generate QR Code.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Logger.w(TAG, "group:onCancelled", databaseError.toException());
            }
        });
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
    }

    public void generateQRCode(String token, ImageView imgV) throws WriterException {
        String token_utf8 = Uri.encode(token, "utf-8");
        Writer qrWriter = new QRCodeWriter();

        DisplayMetrics metrics = Helpers.getDisplayMetrics(this);
        int DIM = Math.min(metrics.widthPixels, 500);

        BitMatrix bMtx = qrWriter.encode(token_utf8, BarcodeFormat.QR_CODE, DIM, DIM);
        Bitmap bmImage = Bitmap.createBitmap(DIM, DIM, Bitmap.Config.ARGB_8888);

        for (int width = 0; width < DIM; width++) {
            for (int height = 0; height < DIM; height++) {
                bmImage.setPixel(width, height, bMtx.get(width, height) ? Color.BLACK : Color.WHITE);
            }
        }

        imgV.setImageBitmap(bmImage);
    }
}
