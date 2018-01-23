package fi.aalto.narcolepticninjas.shareapicture.model;

import com.google.firebase.database.IgnoreExtraProperties;

/**
 * Created by goal on 7.12.2017.
 */

@IgnoreExtraProperties
public class FirebasePicture {
    public Long faces;
    public String upload_time;
    public String uploader;
    public String uri;
    public String uploader_name;
    public String firebase_id;
    public String type;

    public FirebasePicture() {
    }

    public FirebasePicture(Long faces, String upload_time, String uploader, String uri, String uploader_name) {
        this.faces = faces;
        this.upload_time = upload_time;
        this.uploader = uploader;
        this.uri = uri;
        this.uploader_name = uploader_name;
    }

    public FirebasePicture(String uri, String type) {
        this.faces = Long.valueOf(0);
        this.upload_time = "Not set";
        this.uploader = "Not set";
        this.uri = uri;
        this.uploader_name = "Not set";
        this.type = type;
    }

    public String getPhotoUrl() {
        return this.uri;
    }
}

