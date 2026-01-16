package com.ldm.gestionaltos.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.ldm.gestionaltos.R;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanRackFragment extends Fragment {

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

    public ScanRackFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_scan_rack, container, false);
        previewView = v.findViewById(R.id.previewView);
        tvHint = v.findViewById(R.id.tvHint);

        // Configurar toolbar
        MaterialToolbar topAppBar = v.findViewById(R.id.topAppBar);
        topAppBar.setNavigationIcon(R.drawable.ic_arrow_back);
        // Tinte blanco para que se vea sobre la cámara
        topAppBar.setNavigationIconTint(Color.WHITE);

        topAppBar.setNavigationOnClickListener(view ->
                Navigation.findNavController(view).navigateUp()
        );

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        cameraExecutor = Executors.newSingleThreadExecutor();

        return v;
    }

    // Ocultar la Toolbar de MainActivity al entrar, mostrarla al salir
    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null && getActivity() instanceof AppCompatActivity) {
            // Ocultamos la barra global para usar la bonita
            if (((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
                ((AppCompatActivity) getActivity()).getSupportActionBar().hide();
            }
        }
        handled = false;
        ensureCameraPermissionAndStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (getActivity() != null && getActivity() instanceof AppCompatActivity) {
            // La mostramos de nuevo para otros fragments que la usen
            if (((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
                ((AppCompatActivity) getActivity()).getSupportActionBar().show();
            }
        }
    }
    @Override
    public void onPause() {
        super.onPause();
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
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();

                analysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                assert cameraProvider != null;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(getViewLifecycleOwner(), cameraSelector, preview, analysis);

            } catch (Exception e) {
                tvHint.setText(R.string.error_iniciando_c_mara);
                Log.e("ScanRackFragment", "startCamera error", e);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (handled) {
            imageProxy.close();
            return;
        }

        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        InputImage inputImage = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );

        barcodeScanner.process(inputImage)
                .addOnSuccessListener(this::handleBarcodes)
                .addOnFailureListener(e -> Log.e("ScanRackFragment", "scan error", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }


    private void handleBarcodes(List<Barcode> barcodes) {
        if (handled) return;
        if (barcodes == null || barcodes.isEmpty()) return;

        Barcode b = barcodes.get(0);
        String raw = b.getRawValue();
        if (raw == null) return;

        // Esperamos "gestionaltos://rack/E340"
        String rackId = parseRackId(raw);

        // 1) QR NO válido -> avisar y volver atrás
        if (rackId == null) {
            handled = true;
            Toast.makeText(getContext(), "QR no válido (no es un rack)", Toast.LENGTH_SHORT).show();

            View view = getView();
            if (view != null) Navigation.findNavController(view).navigateUp();
            return;
        }

        handled = true;

        // 2) QR válido -> comprobar existencia en BBDD antes de navegar
        FirebaseFirestore.getInstance()
                .collection("racks")
                .document(rackId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;
                    View view = getView();
                    if (view == null) return;

                    if (!doc.exists()) {
                        Toast.makeText(getContext(),
                                "Ese rack no existe. Tienes que crearlo primero.",
                                Toast.LENGTH_LONG).show();
                        Navigation.findNavController(view).navigateUp();
                        return;
                    }

                    // 3) Existe -> navegar al detalle
                    Bundle args = new Bundle();
                    args.putString("rackId", rackId);
                    Navigation.findNavController(view).navigate(R.id.rackDetailFragment, args);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    View view = getView();
                    if (view != null) {
                        Toast.makeText(getContext(), "Error consultando BBDD", Toast.LENGTH_SHORT).show();
                        Navigation.findNavController(view).navigateUp();
                    }
                });
    }



    private String parseRackId(String raw) {
        try {
            Uri uri = Uri.parse(raw);
            if (!"gestionaltos".equals(uri.getScheme())) return null;
            if (!"rack".equals(uri.getHost())) return null;

            // path = "/E340"
            String path = uri.getPath();
            if (path == null || path.length() < 2) return null;

            String rack = path.substring(1).toUpperCase();
            if (!rack.matches("^E\\d{3}$")) return null;

            return rack;
        } catch (Exception e) {
            return null;
        }
    }
}
