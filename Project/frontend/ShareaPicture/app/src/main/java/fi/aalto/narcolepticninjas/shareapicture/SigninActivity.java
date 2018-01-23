package fi.aalto.narcolepticninjas.shareapicture;

import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.ErrorCodes;
import com.firebase.ui.auth.IdpResponse;

import java.util.Arrays;
import java.util.List;

/**
 * An activity that ensures the user is signed in to the application.
 */
public class SigninActivity extends AppCompatActivity {
    private static final String TAG = SigninActivity.class.getSimpleName();
    private static final int RC_SIGN_IN = 123;

    // List of authentication providers. Right now only email + password
    List<AuthUI.IdpConfig> providers = Arrays.asList(
        new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build());

    // Trigger the sign-in process automatically for the first time
    boolean mTriggerAutoSignin = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signin);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // This is done here as onActivityResult() will be called before this
        // method. This allows us to catch errors from the auth and display them
        // instead of just retrying it immediately
        if (Helpers.isUserAuthenticated()) {
            Logger.d(TAG, "User already authenticated; returning to previous activity");
            finish();
        } else if (mTriggerAutoSignin) {
            Logger.d(TAG, "User not authenticated; starting Firebase Auth");
            startSignIn();
        } else {
            // Something went wrong with the previous attempt. Wait for user to click
            // the try-again button.
            findViewById(R.id.sign_in_container).setVisibility(View.VISIBLE);
            findViewById(R.id.sign_in_button).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    startSignIn();
                }
            });
        }
    }

    private void startSignIn() {
        Intent intent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .build();
        startActivityForResult(intent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != RC_SIGN_IN) {
            // Unexpected result for a request we didn't make
            Logger.w(TAG, "Got unexpected result for request %d", requestCode);
            return;
        }

        if (resultCode == Activity.RESULT_OK) {
            Logger.d(TAG, "Sign-in successful; returning back to previous activity");
            finish();
        } else {
            Logger.w(TAG, "Sign-in failed: resultCode=%d", resultCode);

            IdpResponse response = IdpResponse.fromResultIntent(data);
            if (response != null && response.getErrorCode() == ErrorCodes.NO_NETWORK) {
                // No network connection
                ((TextView) findViewById(R.id.sign_in_title))
                    .setText(R.string.label_sign_in_error);
                ((TextView) findViewById(R.id.sign_in_explanation))
                    .setText(R.string.label_sign_in_error_network);
            } else if (resultCode == Activity.RESULT_CANCELED) {
                // User cancelled
                ((TextView) findViewById(R.id.sign_in_title))
                    .setText(R.string.label_sign_in_required);
                ((TextView) findViewById(R.id.sign_in_explanation))
                    .setText(R.string.label_sign_in_required_explanation);
            } else {
                // Unknown error
                ((TextView) findViewById(R.id.sign_in_title))
                    .setText(R.string.label_sign_in_error);
                ((TextView) findViewById(R.id.sign_in_explanation))
                    .setText(R.string.label_sign_in_error_unknown);
            }

            mTriggerAutoSignin = false;
        }
    }
}

