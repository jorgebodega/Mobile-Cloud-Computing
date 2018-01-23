package fi.aalto.narcolepticninjas.shareapicture;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;

public class MenuActivity extends AppCompatActivity {
    private static final String TAG = MenuActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Logger.d(TAG, "onResume()");
        Logger.d(TAG, "Active group is %s", Helpers.getActiveGroup(this));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Logger.d(TAG, "onOptionsItemSelected(%d)", item.getItemId());
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_settings:
                startActivity(SettingsActivity.class);
                return true;
            case R.id.menu_logout:
                Logger.d(TAG, "Logout selected");
                FirebaseAuth.getInstance().signOut();

                startActivity(MainActivity.class);
                return true;
        }

        return false;
    }

    public void doManageGroups(View view) {
        startActivity(ManageGroupsActivity.class);
    }

    public void doShowGallery(View view) {
        startActivity(GalleryActivity.class);
    }

    public void doTakePhoto(View view) {
        if (Helpers.getActiveGroup(this) == null) {
            Toast.makeText(this, getString(R.string.toast_take_pic_no_group), Toast.LENGTH_LONG).show();
            return;
        }

        startActivity(PictureActivity.class);
    }

    private void startActivity(Class<?> cls) {
        Logger.d(TAG, "Starting activity %s", cls.getSimpleName());
        startActivity(new Intent(getBaseContext(), cls));
    }
}
