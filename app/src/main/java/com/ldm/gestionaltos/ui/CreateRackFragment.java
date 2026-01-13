package com.ldm.gestionaltos.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.zxing.WriterException;
import com.ldm.gestionaltos.R;
import com.ldm.gestionaltos.model.Rack;
import com.ldm.gestionaltos.util.QrUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CreateRackFragment extends Fragment {

    private TextInputLayout tilRackCode;
    private TextInputEditText etRackCode;
    private ImageView ivQr;
    private MaterialButton btnGenerate, btnSave, btnShare;

    private AutoCompleteTextView actSection, actAisle;

    private final List<String> sectionNames = new ArrayList<>();
    private final Map<String, Long> sectionNameToId = new HashMap<>();

    private final List<String> aisleNames = new ArrayList<>();
    private final Map<String, String> aisleNameToId = new HashMap<>();

    private Long selectedSectionId = null;
    private String selectedAisleId = null;


    private Bitmap lastQrBitmap;
    private String lastRackCode;

    public CreateRackFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_create_rack, container, false);

        tilRackCode = v.findViewById(R.id.tilRackCode);
        etRackCode = v.findViewById(R.id.etRackCode);
        ivQr = v.findViewById(R.id.ivQr);
        btnGenerate = v.findViewById(R.id.btnGenerate);
        btnSave = v.findViewById(R.id.btnSave);
        btnShare = v.findViewById(R.id.btnShare);
        actSection = v.findViewById(R.id.actSection);
        actAisle = v.findViewById(R.id.actAisle);

        btnGenerate.setOnClickListener(view -> onGenerateClicked());
        btnSave.setOnClickListener(view -> onSaveClicked());
        btnShare.setOnClickListener(view -> onShareClicked());

        loadSections();

        return v;
    }

    private void loadSections() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("sections")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    sectionNames.clear();
                    sectionNameToId.clear();

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String name = doc.getString("name");
                        Long numericId = doc.getLong("numericId");
                        if (name == null || numericId == null) continue;

                        sectionNames.add(name);
                        sectionNameToId.put(name, numericId);
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            requireContext(),
                            android.R.layout.simple_list_item_1,
                            sectionNames
                    );
                    actSection.setAdapter(adapter);

                    actSection.setOnItemClickListener((parent, view, position, id1) -> {
                        String chosenName = sectionNames.get(position);
                        selectedSectionId = sectionNameToId.get(chosenName);

                        // reset pasillo al cambiar sección
                        selectedAisleId = null;
                        actAisle.setText("", false);
                        loadAislesForSection(selectedSectionId);
                    });
                })
                .addOnFailureListener(e -> tilRackCode.setError("Error cargando secciones: " + e.getMessage()));
    }

    private void loadAislesForSection(Long sectionId) {
        if (sectionId == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("aisles")
                .whereEqualTo("sectionId", sectionId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    aisleNames.clear();
                    aisleNameToId.clear();

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String id = doc.getId();
                        String name = doc.getString("name");
                        if (name == null) continue;

                        aisleNames.add(name);
                        aisleNameToId.put(name, id);
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            requireContext(),
                            android.R.layout.simple_list_item_1,
                            aisleNames
                    );
                    actAisle.setAdapter(adapter);

                    actAisle.setOnItemClickListener((parent, view, position, id12) -> {
                        String chosenName = aisleNames.get(position);
                        selectedAisleId = aisleNameToId.get(chosenName);
                    });
                })
                .addOnFailureListener(e -> tilRackCode.setError("Error cargando pasillos: " + e.getMessage()));
    }


    private void onGenerateClicked() {
        tilRackCode.setError(null);

        String code = etRackCode.getText() == null ? "" : etRackCode.getText().toString();
        code = normalizeRackCode(code);

        String error = validateRackCode(code);
        if (error != null) {
            tilRackCode.setError(error);
            return;
        }
        if (selectedSectionId == null) {
            tilRackCode.setError("Selecciona una sección");
            return;
        }
        if (selectedAisleId == null) {
            tilRackCode.setError("Selecciona un pasillo");
            return;
        }


        // El contenido del QR tiene un prefijo para que la app lo reconozca siempre.
        // Ej: gestionaltos://rack/E340
        String qrContent = "gestionaltos://rack/" + code;

        try {
            lastQrBitmap = QrUtils.generateQrBitmap(qrContent, 900);
            ivQr.setImageBitmap(lastQrBitmap);

            lastRackCode = code;
            btnSave.setEnabled(true);
            btnShare.setEnabled(true);

        } catch (WriterException e) {
            tilRackCode.setError("No se pudo generar el QR");
        }
    }

    private void onSaveClicked() {
        if (lastRackCode == null) return;
        if (selectedSectionId == null || selectedAisleId == null) return;

        Rack rack = new Rack();
        rack.id = lastRackCode;
        rack.code = lastRackCode;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("racks")
                .document(rack.id)
                .set(new RackDoc(rack.code, selectedSectionId, selectedAisleId, Timestamp.now()))
                .addOnSuccessListener(unused -> {
                    // mejorar feedback
                    tilRackCode.setError(null);
                    tilRackCode.setHelperText("Guardado en Firestore ✅");
                })
                .addOnFailureListener(e -> tilRackCode.setError("Error guardando: " + e.getMessage()));
    }

    private void onShareClicked() {
        if (lastQrBitmap == null || lastRackCode == null) return;

        try {
            Uri uri = saveBitmapToCacheAndGetUri(lastQrBitmap, "QR_" + lastRackCode + ".png");

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Compartir QR"));

        } catch (IOException e) {
            tilRackCode.setError("Error exportando: " + e.getMessage());
        }
    }

    private String normalizeRackCode(String input) {
        if (input == null) return "";
        return input.trim().toUpperCase(Locale.ROOT);
    }

    // Reglas: "E" + 3 dígitos (E000..E999).
    private String validateRackCode(String code) {
        if (TextUtils.isEmpty(code)) return "Introduce un código (E###)";
        if (!code.matches("^E\\d{3}$")) return "Formato inválido. Ejemplo: E340";
        return null;
    }

    // Guarda png en cache y devuelve content:// uri vía FileProvider
    private Uri saveBitmapToCacheAndGetUri(Bitmap bitmap, String fileName) throws IOException {
        File cacheDir = new File(requireContext().getCacheDir(), "shared_qr");
        if (!cacheDir.exists()) cacheDir.mkdirs();

        File file = new File(cacheDir, fileName);
        FileOutputStream fos = new FileOutputStream(file);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.flush();
        fos.close();

        return FileProvider.getUriForFile(
                requireContext(),
                requireContext().getPackageName() + ".fileprovider",
                file
        );
    }

    // Documento para Firestore
    public static class RackDoc {
        public String code;
        public Long sectionId;
        public String aisleId;
        public Timestamp createdAt;

        public RackDoc() {
        }

        public RackDoc(String code, Long sectionId, String aisleId, Timestamp createdAt) {
            this.code = code;
            this.sectionId = sectionId;
            this.aisleId = aisleId;
            this.createdAt = createdAt;
        }
    }

}
