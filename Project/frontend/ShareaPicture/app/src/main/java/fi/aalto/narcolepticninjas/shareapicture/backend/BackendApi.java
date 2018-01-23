package fi.aalto.narcolepticninjas.shareapicture.backend;

import android.support.annotation.NonNull;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import fi.aalto.narcolepticninjas.shareapicture.Helpers;
import fi.aalto.narcolepticninjas.shareapicture.Logger;
import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;

public class BackendApi {
    private static final String TAG = BackendApi.class.getSimpleName();
    public static final String BACKEND_URI = "https://mcc-fall-2017-g14.appspot.com";
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    public static final OkHttpClient client = new OkHttpClient();

    public static void createGroup(final String name, final Date expiry, final BackendApiResponseCallback handler) {
        JSONObject data = new JSONObject();
        try {
            data.put("name", name);
            data.put("expiry", Helpers.dateToIsoString(expiry));
            doPost("groups", data, handler);
        } catch (JSONException e) {
            handleJsonBodyError(e, handler);
        }
    }

    public static void joinGroup(final String token, final String groupId, final BackendApiResponseCallback handler) {
        JSONObject data = new JSONObject();
        try {
            data.put("token", token);
            doPost(String.format("groups/%s/join", groupId), data, handler);
        } catch (JSONException e) {
            handleJsonBodyError(e, handler);
        }
    }

    public static void leaveGroup(final String groupId, final BackendApiResponseCallback handler) {
        JSONObject data = new JSONObject();
        try {
            doPost(String.format("groups/%s/leave", groupId), data, handler);
        } catch (JSONException e) {
            handleJsonBodyError(e, handler);
        }
    }

    public static void deleteGroup(final String groupId, final BackendApiResponseCallback handler) {
        JSONObject data = new JSONObject();
        try {
            doPost(String.format("groups/%s/delete", groupId), data, handler);
        } catch (JSONException e) {
            handleJsonBodyError(e, handler);
        }
    }

    private static void doPost(String endpoint, JSONObject data, final BackendApiResponseCallback handler) throws JSONException {
        final RequestBody body = RequestBody.create(JSON, data.toString());
        final String url = BACKEND_URI + "/" + endpoint;

        Logger.d(TAG, "POST %s with data %s", url, data.toString(2));

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        user.getIdToken(false)
            .addOnSuccessListener(new OnSuccessListener<GetTokenResult>() {
                @Override
                public void onSuccess(GetTokenResult result) {
                    String token = result.getToken();
                    if (token == null) {
                        Logger.e(TAG, "Firebase returned null access token!");
                        handler.onFailure(null, new IOException("Null access token returned"));
                        return;
                    }

                    Logger.d(TAG, "Making a request to backend with token %s...", token.substring(0, 6));
                    Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .header("Authorization", token)
                        .build();

                    client.newCall(request).enqueue(handler);
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Logger.e(TAG, "Failed to fetch a Firebase Access Token", e);
                    handler.onFailure(null, new IOException("Failed to fetch a Firebase Access Token", e));
                }
            });
    }

    private static void handleJsonBodyError(JSONException e, Callback handler) {
        Logger.e(TAG, "Failed to construct request body", e);
        handler.onFailure(null, new IOException("Failed to construct request body", e));
    }
}
