package fi.aalto.narcolepticninjas.shareapicture;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int SIGN_IN_REQUEST = 1;

    private static Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        MainActivity.context = getApplicationContext();


        if (!Helpers.isUserAuthenticated()) {
            Logger.i(TAG, "User not logged in; starting SigninActivity");
            startActivityForResult(new Intent(getBaseContext(), SigninActivity.class), SIGN_IN_REQUEST);
            return;
        }
        else {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            Logger.d(TAG, "User already authenticated: name=%s, email=%s", user.getDisplayName(), user.getEmail());
            goToMenu();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SIGN_IN_REQUEST) {
            if (Helpers.isUserAuthenticated()) {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                Logger.d(TAG, "User login successful: name=%s, email=%s", user.getDisplayName(), user.getEmail());
                goToMenu();
            }
            else {
                Logger.d(TAG, "User login failed. resultCode=%d", resultCode);
                Toast.makeText(getBaseContext(), "Login failed.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void goToMenu() {
        Intent intent = new Intent(getBaseContext(), MenuActivity.class);
        startActivity(intent);
        finish();
    }

    public static Context getAppContext() {
        return MainActivity.context;
    }
}
