package fi.aalto.narcolepticninjas.shareapicture;


import android.content.Intent;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class NoActiveGroupFragment extends Fragment {
    private static final String TAG = NoActiveGroupFragment.class.getSimpleName();

    public NoActiveGroupFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Logger.d(TAG, "onCreateView()");
        return inflater.inflate(R.layout.fragment_no_active_group, container, false);
    }
}
