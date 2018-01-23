package fi.aalto.narcolepticninjas.shareapicture.model;

import com.google.firebase.database.IgnoreExtraProperties;
import fi.aalto.narcolepticninjas.shareapicture.Helpers;

import java.util.Date;

@IgnoreExtraProperties
public class Group {
    public String name;
    public String expiry;
    public String created;
    public String admin;

    public Group() {}
    public Group(String name, String expiry, String created, String admin) {
        this.name = name;
        this.expiry = expiry;
        this.created = created;
        this.admin = admin;
    }

    public Date getExpiryDate() {
        return Helpers.isoStringToDate(expiry);
    }

    public Date getCreatedDate() {
        return Helpers.isoStringToDate(created);
    }

    @Override
    public String toString() {
        return String.format("Group(name=%s, expiry=%s, created=%s, admin=%s)", name, expiry, created, admin);
    }
}
