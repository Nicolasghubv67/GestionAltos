package com.ldm.gestionaltos.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.ldm.gestionaltos.R;

public class HelpFragment extends Fragment {

    public HelpFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_help, container, false);

        MaterialButton btnGoScan = v.findViewById(R.id.btnGoScan);
        MaterialButton btnGoRacks = v.findViewById(R.id.btnGoRacks);
        MaterialButton btnGoCreateRack = v.findViewById(R.id.btnGoCreateRack);

        btnGoScan.setOnClickListener(view ->
                Navigation.findNavController(view).navigate(R.id.scanFragment)
        );

        btnGoRacks.setOnClickListener(view ->
                Navigation.findNavController(view).navigate(R.id.racksFragment)
        );

        btnGoCreateRack.setOnClickListener(view ->
                Navigation.findNavController(view).navigate(R.id.createRackFragment)
        );

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (requireActivity().findViewById(R.id.topAppBar) != null) {
            requireActivity().findViewById(R.id.topAppBar).setVisibility(View.GONE);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (requireActivity().findViewById(R.id.topAppBar) != null) {
            requireActivity().findViewById(R.id.topAppBar).setVisibility(View.VISIBLE);
        }
    }

}
