package com.ldm.gestionaltos.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.provider.MediaStore;
import android.graphics.Canvas;
import android.graphics.Color;
import android.app.AlertDialog;
import android.content.ContentValues;


import androidx.annotation.NonNull;
import androidx.print.PrintHelper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import androidx.navigation.fragment.NavHostFragment;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.zxing.WriterException;
import com.ldm.gestionaltos.R;
import com.ldm.gestionaltos.model.Rack;
import com.ldm.gestionaltos.util.QrUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CreateRackFragment extends Fragment {

    private TextInputLayout tilRackCode;
    private TextInputEditText etRackCode;
    private ImageView ivQr;
    private MaterialToolbar topAppBar;
    private MaterialButton btnGenerate, btnShare;
    private LinearLayout layoutResult;
    private AutoCompleteTextView actSection, actAisle;

    // Listas para los dropdowns
    private final List<String> sectionNames = new ArrayList<>();
    private final Map<String, String> sectionNameToId = new HashMap<>();
    private final List<String> aisleNames = new ArrayList<>();

    private String selectedSectionId = null;
    private String selectedAisleId = null;
    private Bitmap lastQrBitmap;
    private String lastRackCode;

    public CreateRackFragment() { }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_create_rack, container, false);

        // Binding de vistas
        tilRackCode = v.findViewById(R.id.tilRackCode);
        etRackCode = v.findViewById(R.id.etRackCode);
        ivQr = v.findViewById(R.id.ivQr);
        btnGenerate = v.findViewById(R.id.btnGenerate);
        btnShare = v.findViewById(R.id.btnShare);
        layoutResult = v.findViewById(R.id.layoutResult);
        actSection = v.findViewById(R.id.actSection);
        actAisle = v.findViewById(R.id.actAisle);
        topAppBar = v.findViewById(R.id.topAppBar);
        setupToolbar();

        MaterialButton btnSave = v.findViewById(R.id.btnSave);
        if (btnSave != null) btnSave.setVisibility(View.GONE);

        // Estado inicial UI
        layoutResult.setVisibility(View.GONE);     // no se enseña qr hasta guardar OK
        btnShare.setEnabled(false);
        btnShare.setText(R.string.guardar_imprimir);

        btnGenerate.setOnClickListener(view -> onGenerateClicked());
        btnShare.setOnClickListener(view -> onExportClicked());

        loadSections();

        return v;
    }

    private void setupToolbar() {
        topAppBar.setNavigationIcon(R.drawable.ic_arrow_back);
        topAppBar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp()
        );
    }

    // Ocultar la Toolbar de MainActivity al entrar, mostrarla al salir
    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null && getActivity() instanceof AppCompatActivity) {
            if (((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
                ((AppCompatActivity) getActivity()).getSupportActionBar().hide();
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (getActivity() != null && getActivity() instanceof AppCompatActivity) {
            if (((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
                ((AppCompatActivity) getActivity()).getSupportActionBar().show();
            }
        }
    }
    // ---------------------------------------

    private void loadSections() {
        FirebaseFirestore.getInstance().collection("sections").get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded() || getContext() == null) return;
                    sectionNames.clear();
                    sectionNameToId.clear();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String name = doc.getString("name");
                        if (name == null) continue;
                        sectionNames.add(name);
                        sectionNameToId.put(name, doc.getId());
                    }
                    Context ctx = getContext();
                    if (!isAdded() || ctx == null) return;
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, sectionNames);
                    actSection.setAdapter(adapter);
                    actSection.setOnItemClickListener((parent, view, position, id) -> {
                        String name = sectionNames.get(position);
                        selectedSectionId = sectionNameToId.get(name);
                        selectedAisleId = null;
                        actAisle.setText("", false);
                        loadAislesForSection(selectedSectionId);
                    });
                });
    }

    private void loadAislesForSection(String sectionId) {
        if (sectionId == null) return;
        FirebaseFirestore.getInstance().collection("aisles")
                .whereEqualTo("sectionId", sectionId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded() || getContext() == null) return;
                    aisleNames.clear();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String name = doc.getId();
                        aisleNames.add(name);
                    }
                    Context ctx = getContext();
                    if (!isAdded() || ctx == null) return;
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, aisleNames);
                    actAisle.setAdapter(adapter);
                    actAisle.setOnItemClickListener((parent, view, position, id) -> selectedAisleId = aisleNames.get(position));
                });
    }

    private void onGenerateClicked() {
        tilRackCode.setError(null);

        String code = etRackCode.getText() != null
                ? etRackCode.getText().toString().trim().toUpperCase(Locale.ROOT)
                : "";

        if (!code.matches("^E\\d{3}$")) {
            tilRackCode.setError("Formato inválido. Debe ser E + 3 dígitos (Ej: E105)");
            return;
        }
        if (selectedSectionId == null || selectedAisleId == null) {
            Toast.makeText(getContext(), "Selecciona Sección y Pasillo", Toast.LENGTH_SHORT).show();
            return;
        }

        // Deshabilitamos mientras comprobamos
        btnGenerate.setEnabled(false);

        // 1) COMPROBAR SI YA EXISTE EL RACK EN BBDD
        FirebaseFirestore.getInstance()
                .collection("racks")
                .document(code)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;

                    if (doc.exists()) {
                        // Ya existe: avisar y NO generar
                        btnGenerate.setEnabled(true);
                        Toast.makeText(getContext(),
                                "Ese rack ya existe. Primero debes eliminarlo si quieres regenerar el QR.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    // 2) Si NO existe, generamos
                    generateQrAndContinueSave(code);

                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    btnGenerate.setEnabled(true);
                    Toast.makeText(getContext(), "Error consultando BBDD: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void generateQrAndContinueSave(String code) {
        String qrContent = "gestionaltos://rack/" + code;

        try {
            // Generar QR
            Bitmap qr = QrUtils.generateQrBitmap(qrContent, 1000);
            lastQrBitmap = buildQrBitmapWithRackCode(qr, code);
            lastRackCode = code;

            // Guardar automáticamente en Firestore
            Rack rack = new Rack(lastRackCode, selectedSectionId, selectedAisleId);

            FirebaseFirestore.getInstance()
                    .collection("racks")
                    .document(rack.id)
                    .set(rack)
                    .addOnSuccessListener(v -> {
                        if (!isAdded()) return;

                        Toast.makeText(getContext(), "✅ Rack guardado correctamente", Toast.LENGTH_SHORT).show();

                        // Mostrar resultado solo si guardado OK
                        ivQr.setImageBitmap(lastQrBitmap);
                        layoutResult.setVisibility(View.VISIBLE);

                        btnShare.setEnabled(true);
                        btnShare.setText(R.string.guardar_imprimir);
                        tilRackCode.setHelperText("✅ Registrado en sistema");

                        btnGenerate.setEnabled(true);
                    })
                    .addOnFailureListener(e -> {
                        if (!isAdded()) return;

                        btnGenerate.setEnabled(true);
                        layoutResult.setVisibility(View.GONE);

                        tilRackCode.setError("Error guardando en BBDD: " + e.getMessage());
                        Toast.makeText(getContext(), "❌ No se pudo guardar el rack", Toast.LENGTH_SHORT).show();
                    });

        } catch (WriterException e) {
            btnGenerate.setEnabled(true);
            tilRackCode.setError("Error generando QR");
        } finally {
            btnGenerate.setEnabled(true);
        }
    }

    // Menú de opciones
    private void onExportClicked() {
        if (lastQrBitmap == null) return;

        String[] options = {"Guardar en Galería", "Imprimir"};

        new AlertDialog.Builder(requireContext())
                .setTitle("QR " + lastRackCode)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        saveToGallery();
                    } else {
                        printQr();
                    }
                })
                .show();
    }

    // Opción 1: Guardar en Galería (Fotos)
    private void saveToGallery() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "QR_" + lastRackCode);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GestionAltos");

        Uri uri = requireContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        try {
            if (uri != null) {
                OutputStream out = requireContext().getContentResolver().openOutputStream(uri);
                assert out != null;
                lastQrBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.close();
                Toast.makeText(getContext(), "Guardado en Galería", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(getContext(), "Error guardando", Toast.LENGTH_SHORT).show();
        }
    }

    // Opción 2: Imprimir (Usa el servicio de impresión de Android)
    private void printQr() {
        PrintHelper photoPrinter = new PrintHelper(requireContext());
        photoPrinter.setScaleMode(PrintHelper.SCALE_MODE_FIT);
        photoPrinter.printBitmap("QR_" + lastRackCode, lastQrBitmap);
    }

    private Bitmap buildQrBitmapWithRackCode(Bitmap qrBitmap, String rackCode) {

        int extraHeight = 220;

        Bitmap out = Bitmap.createBitmap(
                qrBitmap.getWidth(),
                qrBitmap.getHeight() + extraHeight,
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.WHITE);

        // Dibuja el QR arriba
        canvas.drawBitmap(qrBitmap, 0, 0, null);

        // Texto grande centrado abajo
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(120f);

        float x = out.getWidth() / 2f;
        float y = qrBitmap.getHeight() + (extraHeight / 2f) + 40;

        canvas.drawText(rackCode, x, y, paint);
        return out;
    }

}