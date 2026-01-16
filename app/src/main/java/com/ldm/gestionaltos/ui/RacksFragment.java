package com.ldm.gestionaltos.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;

import com.ldm.gestionaltos.R;

import com.ldm.gestionaltos.model.StockItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class RacksFragment extends Fragment {

    private TextInputEditText etSearch;
    private RecyclerView rvRacks;
    private ProgressBar progressBar;
    private TextView tvEmpty;

    private SearchAdapter adapter;
    private final List<SearchResult> results = new ArrayList<>();
    private final Map<String, Integer> rackCounts = new HashMap<>();
    private int countsRequestId = 0;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    public RacksFragment() { }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_racks, container, false);

        etSearch = v.findViewById(R.id.etSearch);
        rvRacks = v.findViewById(R.id.rvRacks);
        progressBar = v.findViewById(R.id.progressBar);
        tvEmpty = v.findViewById(R.id.tvEmpty);

        rvRacks.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SearchAdapter(results, rackCounts, this::onRackClicked);

        rvRacks.setAdapter(adapter);

        TextInputLayout tilSearch = v.findViewById(R.id.tilSearch);

        getParentFragmentManager().setFragmentResultListener("scan_product_for_search", this,
                (key, bundle) -> {
                    String barcode = bundle.getString("barcode", "");
                    if (barcode == null) return;

                    String clean = barcode.trim().replaceAll("\\s+", "");

                    etSearch.setText("");
                    etSearch.clearFocus();

                    etSearch.setText(clean);
                    etSearch.setSelection(clean.length());

                    // Lanza búsqueda inmediata (sin esperar 500ms)
                    if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                    performSearch(clean);
                });


        tilSearch.setEndIconOnClickListener(view -> {
            // Limpiar antes de escanear para no arrastrar estado anterior
            if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
            etSearch.setText("");
            etSearch.clearFocus();

            Navigation.findNavController(requireView()).navigate(R.id.scanProductFragment);
        });

        setupSearchListener();

        loadAllRacks();

        loadRackCountsThenUpdate();

        return v;
    }

    private void setupSearchListener() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                // Cancelar la búsqueda anterior si el usuario sigue escribiendo rápido
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);

                // Esperar 500ms antes de lanzar la búsqueda
                searchRunnable = () -> performSearch(s.toString().trim());
                searchHandler.postDelayed(searchRunnable, 500);
            }
        });
    }

    private void performSearch(String rawQuery) {
        String q = rawQuery == null ? "" : rawQuery.trim().replaceAll("\\s+", "");
        if (q.isEmpty()) {
            loadAllRacks();
            return;
        }

        setLoading(true);

        String qUpper = q.toUpperCase(Locale.ROOT);
        String qLower = q.toLowerCase(Locale.ROOT);

        // Rack ID esperado: E + 3 dígitos (ej: E032). Si escriben solo "E" o "E0", tratamos como prefijo.
        if (qUpper.startsWith("E")) {
            searchRackByIdPrefix(qUpper);
            return;
        }

        // Búsquedas numéricas: EAN/UPC suelen ser solo dígitos.
        if (q.matches("^\\d+$")) {
            // EAN/UPC "largo" -> intentamos exacto primero (rápido y preciso)
            if (q.length() >= 8) {
                searchRacksByBarcodeExact(q);
            } else {
                // EAN parcial corto -> filtrado local en stock (Firestore no soporta contains)
                searchRacksByBarcodePartial(q);
            }
            return;
        }

        // Caso general: nombre / referencia / id interno / barcode parcial alfanumérico
        searchRacksByProductContains(qLower);
    }

    private void searchRackByIdPrefix(String rackQueryUpper) {
        db.collection("racks")
                .orderBy(FieldPath.documentId())
                .startAt(rackQueryUpper)
                .endAt(rackQueryUpper + "\uf8ff")
                .limit(200)
                .get()
                .addOnSuccessListener(snap -> {
                    results.clear();
                    for (DocumentSnapshot doc : snap) {
                        String id = doc.getId();
                        results.add(new SearchResult(id, "Coincide rack: " + id));
                    }
                    updateUI();
                })
                .addOnFailureListener(this::handleError);
    }

    private void searchRacksByBarcodeExact(String barcodeDigits) {
        db.collection("stock")
                .whereEqualTo("productId", barcodeDigits)
                .limit(300)
                .get()
                .addOnSuccessListener(snap -> {
                    results.clear();
                    Set<String> added = new HashSet<>();

                    for (DocumentSnapshot doc : snap) {
                        StockItem item = doc.toObject(StockItem.class);
                        if (item == null || item.rackId == null) continue;

                        if (added.add(item.rackId)) {
                            results.add(new SearchResult(item.rackId, "EAN exacto: " + barcodeDigits));
                        }
                    }
                    updateUI();
                })
                .addOnFailureListener(this::handleError);
    }

    private void searchRacksByBarcodePartial(String digits) {
        db.collection("stock")
                .limit(800)
                .get()
                .addOnSuccessListener(snap -> {
                    results.clear();
                    Set<String> added = new HashSet<>();

                    for (DocumentSnapshot doc : snap) {
                        StockItem item = doc.toObject(StockItem.class);
                        if (item == null || item.rackId == null) continue;

                        String barcode = item.productId != null ? item.productId : "";
                        if (!barcode.isEmpty() && barcode.contains(digits) && added.add(item.rackId)) {
                            results.add(new SearchResult(item.rackId, "EAN contiene: " + digits));
                        }
                    }
                    updateUI();
                })
                .addOnFailureListener(this::handleError);
    }

    private void searchRacksByProductContains(String qLower) {
        db.collection("stock")
                .limit(800)
                .get()
                .addOnSuccessListener(snap -> {
                    results.clear();
                    Set<String> added = new HashSet<>();

                    for (DocumentSnapshot doc : snap) {
                        StockItem item = doc.toObject(StockItem.class);
                        if (item == null || item.rackId == null) continue;

                        String name = item.productName != null ? item.productName.toLowerCase(Locale.ROOT) : "";
                        String internal = item.productInternalId != null ? item.productInternalId.toLowerCase(Locale.ROOT) : "";
                        String ref = item.productReference != null ? item.productReference.toLowerCase(Locale.ROOT) : "";
                        String barcode = item.productId != null ? item.productId : "";

                        boolean match =
                                name.contains(qLower) ||
                                        internal.contains(qLower) ||
                                        ref.contains(qLower) ||
                                        barcode.contains(qLower);

                        if (!match || !added.add(item.rackId)) continue;

                        String info = buildMatchInfo(item, qLower);
                        results.add(new SearchResult(item.rackId, info));
                    }

                    updateUI();
                })
                .addOnFailureListener(this::handleError);
    }

    /**
     * Devuelve una descripción corta y útil para el usuario según el campo que haya coincidido.
     */
    private String buildMatchInfo(StockItem item, String qLower) {
        String name = item.productName != null ? item.productName : "";
        String internal = item.productInternalId != null ? item.productInternalId.toLowerCase(Locale.ROOT) : "";
        String ref = item.productReference != null ? item.productReference.toLowerCase(Locale.ROOT) : "";
        String barcode = item.productId != null ? item.productId : "";

        if (!name.isEmpty() && name.toLowerCase(Locale.ROOT).contains(qLower)) {
            return "Producto: " + name;
        }
        if ((!internal.isEmpty() && internal.contains(qLower)) || (!ref.isEmpty() && ref.contains(qLower))) {
            return "Coincide referencia";
        }
        if (!barcode.isEmpty() && barcode.contains(qLower)) {
            return "Coincide EAN/barcode";
        }
        return "Coincidencia";
    }


    // --- LÓGICA DE BÚSQUEDA ---

    private void loadAllRacks() {
        setLoading(true);
        db.collection("racks")
                .orderBy(FieldPath.documentId())
                .limit(50)
                .get()
                .addOnSuccessListener(snapshots -> {
                    results.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        String rackId = doc.getId();

                        Object secObj = doc.get("sectionId");
                        Object aisObj = doc.get("aisleId");

                        String sectionId = (secObj != null) ? String.valueOf(secObj) : "";
                        String aisleId = (aisObj != null) ? String.valueOf(aisObj) : "";

                        String info;
                        if (!sectionId.isEmpty() && !aisleId.isEmpty()) {
                            info = "Sección: " + sectionId + " · " + aisleId;
                        } else if (!sectionId.isEmpty()) {
                            info = "Sección: " + sectionId;
                        } else {
                            info = "Sin ubicación";
                        }

                        results.add(new SearchResult(rackId, info));
                    }
                    updateUI();
                })
                .addOnFailureListener(this::handleError);
    }


    // --- UI HELPERS ---

    @SuppressLint("NotifyDataSetChanged")
    private void updateUI() {
        setLoading(false);
        adapter.notifyDataSetChanged();

        if (results.isEmpty()) {
            tvEmpty.setText(R.string.no_se_encontraron_resultados);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        rvRacks.setVisibility(isLoading ? View.INVISIBLE : View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
    }

    private void handleError(Exception e) {
        setLoading(false);
        Log.e("RacksFragment", "Error en búsqueda", e);
        Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }

    private void onRackClicked(SearchResult result) {
        Bundle args = new Bundle();
        args.putString("rackId", result.rackId);

        View view = getView();
        if (view != null) {
            Navigation.findNavController(view).navigate(R.id.rackDetailFragment, args);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadRackCountsThenUpdate() {
        final int requestId = ++countsRequestId;

        rackCounts.clear();

        db.collection("stock")
                .limit(2000)
                .get()
                .addOnSuccessListener(snap -> {
                    // Si esta respuesta NO es la última, la ignoramos
                    if (requestId != countsRequestId) return;

                    rackCounts.clear();
                    for (DocumentSnapshot doc : snap) {
                        StockItem item = doc.toObject(StockItem.class);
                        if (item == null || item.rackId == null) continue;
                        rackCounts.compute(item.rackId, (k, current) -> (current == null) ? 1 : current + 1);
                    }

                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Log.e("RacksFragment", "Error cargando conteos", e));
    }

    // --- CLASES INTERNAS ---

    static class SearchResult {
        String rackId;
        String info;

        SearchResult(String rackId, String info) {
            this.rackId = rackId;
            this.info = info;
        }
    }

    static class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.VH> {
        private final List<SearchResult> list;
        private final OnItemClick listener;
        private final Map<String, Integer> counts;
        interface OnItemClick { void onClick(SearchResult item); }

        SearchAdapter(List<SearchResult> list, Map<String, Integer> counts, OnItemClick listener) {
            this.list = list;
            this.counts = counts;
            this.listener = listener;
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rack_search, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull VH holder, int position) {
            SearchResult item = list.get(position);

            holder.tvRackId.setText(
                    holder.itemView.getContext().getString(R.string.rack_format, item.rackId)
            );

            holder.tvInfo.setText(item.info);

            Integer count = counts.get(item.rackId);
            if (count == null || count == 0) {
                holder.tvRackStatus.setText(R.string.vac_o);
                holder.tvRackStatus.setBackgroundResource(R.drawable.bg_badge_neutral);
            } else {
                holder.tvRackStatus.setText(count + (count == 1 ? " producto" : " productos"));
                holder.tvRackStatus.setBackgroundResource(R.drawable.bg_badge_active);
            }

            holder.itemView.setOnClickListener(v -> listener.onClick(item));
        }


        @Override public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvRackId, tvInfo, tvRackStatus;

            VH(View v) {
                super(v);
                tvRackId = v.findViewById(R.id.tvRackId);
                tvInfo = v.findViewById(R.id.tvMatchInfo);
                tvRackStatus = v.findViewById(R.id.tvRackStatus);
            }
        }
    }
}