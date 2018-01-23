package fi.aalto.narcolepticninjas.shareapicture;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.DatePicker;
import android.widget.TimePicker;
import android.widget.Toast;

import fi.aalto.narcolepticninjas.shareapicture.backend.BackendApi;
import fi.aalto.narcolepticninjas.shareapicture.backend.BackendApiResponseCallback;
import okhttp3.Call;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

public class CreateGroupActivity
    extends AppCompatActivity
    implements
        DatePickerDialog.OnDateSetListener,
        TimePickerDialog.OnTimeSetListener,
        View.OnFocusChangeListener {

    public static class TimePickerFragment extends DialogFragment {
        private Calendar expiry = null;
        public TimePickerFragment withExpiry(Calendar expiry) {
            this.expiry = expiry;
            return this;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the current time as the default values for the picker
            final Calendar c = (expiry == null) ? Calendar.getInstance() : expiry;
            int hour = c.get(Calendar.HOUR_OF_DAY);
            int minute = c.get(Calendar.MINUTE);
            // Create a new instance of TimePickerDialog and return it
            TimePickerDialog picker = new TimePickerDialog(getActivity(), (TimePickerDialog.OnTimeSetListener) getActivity(), hour, minute,
                android.text.format.DateFormat.is24HourFormat(getActivity()));
            return picker;
        }
    }


    public static class DatePickerFragment extends DialogFragment {
        private Calendar expiry = null;
        public DatePickerFragment withExpiry(Calendar expiry) {
            this.expiry = expiry;
            return this;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the current time as the default values for the picker
            final Calendar c = (expiry == null) ? Calendar.getInstance() : expiry;
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);

            // Create a new instance of TimePickerDialog and return it
            DatePickerDialog picker = new DatePickerDialog(getActivity(), (DatePickerDialog.OnDateSetListener) getActivity(), year, month, day);
            picker.getDatePicker().setMinDate(new Date().getTime());
            return picker;
        }
    }

    private static final String TAG = CreateGroupActivity.class.getSimpleName();
    private final Calendar expiry = Calendar.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }

        findViewById(R.id.group_expiry_date_input_et).setOnFocusChangeListener(this);
        findViewById(R.id.group_expiry_time_input_et).setOnFocusChangeListener(this);

        // Clear seconds (we don't want them) and make the default expiry be two
        // hours in the future
        expiry.set(Calendar.SECOND, 0);
        expiry.add(Calendar.HOUR_OF_DAY, 2);

        updateExpiryInputs();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_create_group, menu);

        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_save_group:
                Logger.d(TAG, "Saving new group: name=%s, expiry=%s", getGroupName(), expiry.getTime().toString());

                // Check if the group name is valid (i.e. non-empty string)
                final String name = getGroupName();
                if (name.length() == 0) {
                    Logger.d(TAG, "Group name is empty; showing error");
                    ((TextInputLayout) findViewById(R.id.group_name_input))
                        .setError(getString(R.string.create_group_error_name_missing));
                    ((TextInputLayout) findViewById(R.id.group_name_input))
                        .setErrorEnabled(true);
                    return false;
                } else {
                    ((TextInputLayout) findViewById(R.id.group_name_input))
                        .setErrorEnabled(false);
                }

                // Check if the expiry values are valid
                if (((TextInputLayout) findViewById(R.id.group_expiry_time_input)).isErrorEnabled()) {
                    Logger.d(TAG, "Tried to create a group with errors in the form; skipping");
                    return false;
                }

                // Send the group info to Backend
                BackendApi.createGroup(getGroupName(), expiry.getTime(), new BackendApiResponseCallback() {

                    private void showError() {
                        Toast.makeText(CreateGroupActivity.this, getString(R.string.toast_generic_error), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onFailure(Call call, IOException e) {
                        Logger.e(TAG, "Create group failed", e);
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            public void run() {
                                showError();
                            }
                        });
                    }

                    @Override
                    public void onResponse(final Call call, final Response response, final JSONObject body) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            public void run() {
                                if (response.code() > 299) {
                                    showError();
                                    return;
                                }

                                String groupId;
                                String expiry;
                                try {
                                    groupId = body.getString("group_id");
                                    expiry = body.getString("expiry");
                                } catch (JSONException e) {
                                    onFailure(call, new IOException("No group_id in response!", e));
                                    return;
                                }

                                Helpers.setActiveGroup(CreateGroupActivity.this, groupId, expiry);
                                Toast.makeText(CreateGroupActivity.this, getString(R.string.create_group_success), Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        });
                    }
                });

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onFocusChange(View view, boolean focused) {
        Logger.d(TAG, "onFocusChange(view=%d, focused=%b)", view.getId(), focused);
        switch (view.getId()) {
            case R.id.group_expiry_time_input_et:
                if (focused) doOpenTimePicker(view);
                break;
            case R.id.group_expiry_date_input_et:
                if (focused) doOpenDatePicker(view);
                break;
            default:
                break;
        }
    }

    @Override
    public void onDateSet(DatePicker datePicker, int year, int month, int date) {
        Logger.d(TAG, "User chose %d-%d-%d", year, month, date);

        this.expiry.set(year, month, date);
        updateExpiryInputs();
    }

    @Override
    public void onTimeSet(TimePicker view, int hour, int minute) {
        // Do something with the time chosen by the user
        Logger.d(TAG, "User chose %d:%d", hour, minute);

        this.expiry.set(Calendar.HOUR_OF_DAY, hour);
        this.expiry.set(Calendar.MINUTE, minute);
        updateExpiryInputs();
    }


    public void doOpenTimePicker(View view) {
        Logger.d(TAG, "Opening time picker");
        (new TimePickerFragment().withExpiry(expiry)).show(getSupportFragmentManager(), "timePicker");
    }

    public void doOpenDatePicker(View view) {
        Logger.d(TAG, "Opening date picker");
        (new DatePickerFragment().withExpiry(expiry)).show(getSupportFragmentManager(), "datePicker");
    }

    private void updateExpiryInputs() {
        String date = DateFormat.getDateInstance().format(this.expiry.getTime());
        String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(this.expiry.getTime());

        ((TextInputEditText) findViewById(R.id.group_expiry_date_input_et)).setText(date);
        ((TextInputEditText) findViewById(R.id.group_expiry_time_input_et)).setText(time);

        findViewById(R.id.group_expiry_date_input_et).clearFocus();
        findViewById(R.id.group_expiry_time_input_et).clearFocus();

        if (this.expiry.before(Calendar.getInstance())) {
            ((TextInputLayout) findViewById(R.id.group_expiry_time_input))
                .setError(getString(R.string.create_group_error_expiry_in_past));
            ((TextInputLayout) findViewById(R.id.group_expiry_time_input))
                .setErrorEnabled(true);
        } else {
            ((TextInputLayout) findViewById(R.id.group_expiry_time_input))
                .setErrorEnabled(false);
        }
    }

    public String getGroupName() {
        return ((TextInputEditText) findViewById(R.id.group_name_input_et)).getText().toString().trim();
    }
}
