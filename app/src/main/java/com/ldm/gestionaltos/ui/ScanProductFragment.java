package com.ldm.gestionaltos.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.ldm.gestionaltos.R;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanProductFragment extends Fragment {

    private PreviewView previewView;
    private TextView tvHint;

    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private boolean handled = false;

    private final ActivityResultLauncher<String> requestCameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else tvHint.setText(R.string.permiso_de_c_mara_denegado);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_scan_rack, container, false); // reutilizamos tu layout cámara
        previewView = v.findViewById(R.id.previewView);
        tvHint = v.findViewById(R.id.tvHint);
        tvHint.setText(R.string.apunta_al_c_digo_de_barras_del_producto);

        MaterialToolbar topAppBar = v.findViewById(R.id.topAppBar);
        topAppBar.setNavigationIcon(R.drawable.ic_arrow_back);
        topAppBar.setNavigationIconTint(Color.WHITE);
        topAppBar.setNavigationOnClickListener(view ->
                Navigation.findNavController(view).navigateUp()
        );

        // Escáner de BARCODES (no QR)
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_CODE_128
                )
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        cameraExecutor = Executors.newSingleThreadExecutor();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof AppCompatActivity) {
            if (((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
                ((AppCompatActivity) getActivity()).getSupportActionBar().hide();
            }
        }

        // Reset fuerte del estado
        handled = false;
        tvHint.setText(R.string.apunta_al_c_digo_de_barras_del_producto);

        ensureCameraPermissionAndStart();
    }


    @Override
    public void onStop() {
        super.onStop();
        if (getActivity() instanceof AppCompatActivity) {
            if (((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
                ((AppCompatActivity) getActivity()).getSupportActionBar().show();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (barcodeScanner != null) barcodeScanner.close();
    }

    private void ensureCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                assert cameraProvider != null;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(getViewLifecycleOwner(),
                        CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);

            } catch (Exception e) {
                tvHint.setText(R.string.error_iniciando_c_mara);
                Log.e("ScanProductFragment", "startCamera error", e);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (handled) { imageProxy.close(); return; }
        if (imageProxy.getImage() == null) { imageProxy.close(); return; }

        InputImage inputImage = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );

        barcodeScanner.process(inputImage)
                .addOnSuccessListener(this::handleBarcodes)
                .addOnFailureListener(e -> Log.e("ScanProductFragment", "scan error", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void handleBarcodes(List<Barcode> barcodes) {
        if (handled) return;
        if (barcodes == null || barcodes.isEmpty()) return;

        Barcode b = barcodes.get(0);
        String raw = b.getRawValue();
        if (raw == null || raw.trim().isEmpty()) return;

        handled = true;

        // Devolver resultado al fragment anterior
        Bundle result = new Bundle();
        result.putString("barcode", raw.trim());
        getParentFragmentManager().setFragmentResult("scan_product_for_add", result);
        getParentFragmentManager().setFragmentResult("scan_product_for_search", result);

        View view = getView();
        if (view != null) Navigation.findNavController(view).navigateUp();
    }
}
