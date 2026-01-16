package com.ldm.gestionaltos.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import com.ldm.gestionaltos.R;

public class ScanFragment extends Fragment {

    public ScanFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_scan, container, false);

        v.findViewById(R.id.btnGoCreateRack).setOnClickListener(view -> androidx.navigation.Navigation.findNavController(view)
                .navigate(R.id.createRackFragment));

        v.findViewById(R.id.btnScanRack).setOnClickListener(view -> androidx.navigation.Navigation.findNavController(view).navigate(R.id.scanRackFragment));

        return v;
    }

}
