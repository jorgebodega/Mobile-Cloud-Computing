package fi.aalto.narcolepticninjas.shareapicture;

import android.app.Application;
import net.danlew.android.joda.JodaTimeAndroid;

public class ShareAPictureApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        JodaTimeAndroid.init(this);
    }
}