package fi.aalto.narcolepticninjas.shareapicture.backend;

import fi.aalto.narcolepticninjas.shareapicture.Logger;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public abstract class BackendApiResponseCallback implements Callback {


    private static final String TAG = BackendApiResponseCallback.class.getSimpleName();

    /**
     * Handles the backend response.
     *
     * @param call the internal call object
     * @param response the response object
     * @param body parsed response body
     */
    public abstract void onResponse(final Call call, final Response response, final JSONObject body);

    @Override
    public void onResponse(Call call, Response response) throws IOException {
        Logger.d(TAG, "Got a response %d %s", response.code(), response.message());

        final ResponseBody body = response.body();
        if (body == null) {
            onFailure(call, new IOException("Request body missing!"));
            return;
        }

        String rawBody;
        try {
            rawBody = body.string();
        } catch (IOException e) {
            onFailure(call, e);
            return;
        }

        Logger.d(TAG, "Response body was: %s", rawBody);

        JSONObject jsonBody;
        try {
            jsonBody = new JSONObject(rawBody);
        } catch (JSONException e) {
            onFailure(call, new IOException("Failed to parse body as JSON", e));
            return;
        }

        onResponse(call, response, jsonBody);
    }
}
