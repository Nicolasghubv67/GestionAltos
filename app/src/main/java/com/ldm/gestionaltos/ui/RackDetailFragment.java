package com.ldm.gestionaltos.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.ldm.gestionaltos.R;
import com.ldm.gestionaltos.model.Product;
import com.ldm.gestionaltos.model.StockItem;

import java.util.ArrayList;
import java.util.List;

public class RackDetailFragment extends Fragment {

    private String rackId;
    private FirebaseFirestore db;
    private MaterialToolbar topAppBar;
    private TextView tvRackCode, tvLocationInfo, tvEmpty;
    private StockAdapter adapter;
    private final List<StockItem> stockList = new ArrayList<>();

    public RackDetailFragment() { }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_rack_detail, container, false);

        db = FirebaseFirestore.getInstance();
        rackId = getArguments() != null ? getArguments().getString("rackId") : "";

        tvRackCode = v.findViewById(R.id.tvRackCode);
        tvLocationInfo = v.findViewById(R.id.tvLocationInfo);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        RecyclerView rvStock = v.findViewById(R.id.rvStock);
        FloatingActionButton fabAdd = v.findViewById(R.id.fabAdd);
        topAppBar = v.findViewById(R.id.topAppBar);
        setupToolbar();

        // Configurar RecyclerView
        rvStock.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new StockAdapter(stockList, this::showEditDialog, this::confirmRemoveFromRack);

        rvStock.setAdapter(adapter);

        setupHeader();
        listenToStockUpdates();

        fabAdd.setOnClickListener(view -> showAddProductDialog());

        getParentFragmentManager().setFragmentResultListener("scan_product_for_add", this,
                (key, bundle) -> {
                    String barcode = bundle.getString("barcode", "");
                    if (barcode == null || barcode.trim().isEmpty()) return;
                    onBarcodeForAdd(barcode.trim());
                });


        MaterialButton btnDeleteRack = v.findViewById(R.id.btnDeleteRack);
        btnDeleteRack.setOnClickListener(view -> confirmDeleteRack());

        return v;
    }

    private void setupToolbar() {
        topAppBar.setNavigationIcon(R.drawable.ic_arrow_back);
        topAppBar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp()
        );

        topAppBar.setTitle("Rack " + rackId);
    }

    private void onBarcodeForAdd(String barcode) {
        // 1) Consultar catálogo
        db.collection("products").document(barcode).get().addOnSuccessListener(doc -> {
            if (!isAdded()) return;

            if (doc.exists()) {
                Product p = doc.toObject(Product.class);
                if (p == null) p = new Product();
                p.barcode = barcode;

                // Existe -> diálogo con datos precargados
                showAddOrEditStockDialog(p, /*isEdit*/ false, /*existingStock*/ null);
            } else {
                // No existe -> diálogo expandido para crear producto + añadir al rack
                Product p = new Product(barcode, "", "");
                showAddOrEditStockDialog(p, /*isEdit*/ false, /*existingStock*/ null);
            }
        });
    }

    private void showAddOrEditStockDialog(Product p, boolean isEdit, @Nullable StockItem editingItem) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(isEdit ? "Editar producto" : "Añadir producto");

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText etBarcode = new EditText(requireContext());
        etBarcode.setHint("Código de barras");
        etBarcode.setText(p.barcode != null ? p.barcode : "");

        boolean lockBarcode = isEdit || (p.barcode != null && !p.barcode.trim().isEmpty());

        etBarcode.setEnabled(!lockBarcode);
        etBarcode.setFocusable(!lockBarcode);
        etBarcode.setFocusableInTouchMode(!lockBarcode);
        etBarcode.setClickable(!lockBarcode);
        etBarcode.setLongClickable(!lockBarcode);

        if (lockBarcode) etBarcode.setAlpha(0.85f);

        layout.addView(etBarcode);

        final EditText etName = new EditText(requireContext());
        etName.setHint("Nombre");
        etName.setText(p.name != null ? p.name : "");
        layout.addView(etName);

        final EditText etInternal = new EditText(requireContext());
        etInternal.setHint("Referencia interna");
        String internalPrefill = "";
        if (p.reference != null && !p.reference.isEmpty()) {
            internalPrefill = p.reference;
        }
        etInternal.setText(internalPrefill);
        layout.addView(etInternal);

        final EditText etQty = new EditText(requireContext());
        etQty.setHint("Cantidad");
        etQty.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (editingItem != null) etQty.setText(String.valueOf(editingItem.qty));
        layout.addView(etQty);

        builder.setView(layout);

        builder.setPositiveButton(isEdit ? "Guardar" : "Añadir", (dialog, which) -> {
            String barcode = etBarcode.getText().toString().trim();
            String name = etName.getText().toString().trim();
            String internal = etInternal.getText().toString().trim();
            String qtyStr = etQty.getText().toString().trim();

            if (barcode.isEmpty() || qtyStr.isEmpty()) {
                Toast.makeText(getContext(), "Barcode y cantidad son obligatorios", Toast.LENGTH_SHORT).show();
                return;
            }

            int qty = Integer.parseInt(qtyStr);

            Product prod = new Product(barcode, internal, name);

            if (!isEdit) {
                // ADD: catálogo + sumar o crear stock
                upsertCatalogAndAddOrSumStock(prod, qty);
            } else {
                // EDIT: catálogo + update/merge de stock
                assert editingItem != null;
                upsertCatalogAndEditStock(prod, qty, editingItem);
            }
        });

        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void upsertCatalogAndAddOrSumStock(Product prod, int qtyToAdd) {
        // 1) Guardar/actualizar catálogo (products/{barcode})
        db.collection("products").document(prod.barcode).set(prod)
                .addOnSuccessListener(v -> {
                    // 2) Ver si ya existe stock en este rack con ese barcode
                    db.collection("stock")
                            .whereEqualTo("rackId", rackId)
                            .whereEqualTo("productId", prod.barcode)
                            .limit(1)
                            .get()
                            .addOnSuccessListener(snap -> {
                                if (!isAdded()) return;

                                if (snap != null && !snap.isEmpty()) {
                                    // YA EXISTE -> sumar
                                    DocumentSnapshot ds = snap.getDocuments().get(0);
                                    StockItem existing = ds.toObject(StockItem.class);
                                    int current = (existing != null) ? existing.qty : 0;

                                    db.collection("stock").document(ds.getId()).update(
                                            "qty", current + qtyToAdd,
                                            "productName", prod.name,
                                            "productReference", prod.reference,
                                            "searchName", (prod.name != null ? prod.name.toLowerCase() : "")
                                    );
                                } else {
                                    // NO EXISTE -> crear nuevo stock
                                    StockItem newItem = new StockItem(rackId, prod.barcode, qtyToAdd, prod.name, prod.reference);
                                    db.collection("stock").add(newItem);
                                }
                            });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Error guardando producto: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void upsertCatalogAndEditStock(Product prod, int newQty, @NonNull StockItem editingItem) {
        // 1) upsert catálogo
        db.collection("products").document(prod.barcode).set(prod)
                .addOnSuccessListener(v -> {

                    final String oldBarcode = editingItem.productId;
                    final String newBarcode = prod.barcode;

                    // Si no cambia barcode -> update simple
                    if (oldBarcode.equals(newBarcode)) {
                        db.collection("stock").document(editingItem.id).update(
                                "qty", newQty,
                                "productName", prod.name,
                                "productReference", prod.reference,
                                "searchName", (prod.name != null ? prod.name.toLowerCase() : ""),
                                "productId", newBarcode
                        );
                        return;
                    }

                    // Si cambia barcode -> comprobar si ya hay otro stock con newBarcode para fusionar
                    db.collection("stock")
                            .whereEqualTo("rackId", rackId)
                            .whereEqualTo("productId", newBarcode)
                            .limit(1)
                            .get()
                            .addOnSuccessListener(snap -> {
                                if (!isAdded()) return;

                                if (snap != null && !snap.isEmpty()) {
                                    // Existe -> fusionar qty y borrar el antiguo doc
                                    DocumentSnapshot ds = snap.getDocuments().get(0);
                                    StockItem existing = ds.toObject(StockItem.class);
                                    int current = (existing != null) ? existing.qty : 0;

                                    db.collection("stock").document(ds.getId()).update(
                                            "qty", current + newQty,
                                            "productName", prod.name,
                                            "productReference", prod.reference,
                                            "searchName", (prod.name != null ? prod.name.toLowerCase() : "")
                                    ).addOnSuccessListener(x -> db.collection("stock").document(editingItem.id).delete());

                                } else {
                                    // No existe -> update del doc actual al nuevo barcode
                                    db.collection("stock").document(editingItem.id).update(
                                            "productId", newBarcode,
                                            "qty", newQty,
                                            "productName", prod.name,
                                            "productReference", prod.reference,
                                            "searchName", (prod.name != null ? prod.name.toLowerCase() : "")
                                    );
                                }
                            });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Error guardando producto: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }



    // 1. CARGAR INFO DEL RACK (Cabecera)
    private void setupHeader() {
        tvRackCode.setText(String.format("%s%s", getString(R.string.rack), rackId));

        db.collection("racks").document(rackId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {

                String secId = doc.getString("sectionId");
                String aisId = doc.getString("aisleId");
                tvLocationInfo.setText(String.format("Sección: %s · %s", secId, aisId));
            }
        });
    }

    // 2. ESCUCHAR CAMBIOS EN TIEMPO REAL (Stock)
    @SuppressLint("NotifyDataSetChanged")
    private void listenToStockUpdates() {
        db.collection("stock")
                .whereEqualTo("rackId", rackId)
                //.orderBy("productName", Query.Direction.ASCENDING) // Requiere índice compuesto en Firestore
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;

                    stockList.clear();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots) {
                            StockItem item = doc.toObject(StockItem.class);
                            assert item != null;
                            item.id = doc.getId(); // Capturamos el ID del documento stock
                            stockList.add(item);
                        }
                    }

                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(stockList.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    // 3. DIÁLOGO: AÑADIR PRODUCTO
    private void showAddProductDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Añadir al Rack " + rackId)
                .setMessage("Escanea el código de barras del producto.")
                .setPositiveButton("Escanear", (d, w) -> Navigation.findNavController(requireView()).navigate(R.id.scanProductFragment))
                .setNegativeButton("Cancelar", null)
                .show();
    }


    private void showEditDialog(StockItem item) {
        Product p = new Product(item.productId, item.productReference, item.productName);
        showAddOrEditStockDialog(p, true, item);
    }

    private void confirmDeleteRack() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Eliminar rack")
                .setMessage("Se eliminará el rack y el stock asociado a este rack.\n\n¿Continuar?")
                .setPositiveButton("Eliminar", (d, w) -> deleteRackAndStock())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void deleteRackAndStock() {
        // 1) Borrar stock asociado (colección "stock" where rackId == rackId)
        db.collection("stock")
                .whereEqualTo("rackId", rackId)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;

                    // Borrado en batch para que sea rápido/limpio
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    for (DocumentSnapshot ds : snap.getDocuments()) {
                        batch.delete(ds.getReference());
                    }

                    // 2) También borrar el documento del rack
                    batch.delete(db.collection("racks").document(rackId));

                    batch.commit()
                            .addOnSuccessListener(v -> {
                                if (!isAdded()) return;
                                Toast.makeText(getContext(), "Rack eliminado", Toast.LENGTH_SHORT).show();
                                requireActivity()
                                        .getOnBackPressedDispatcher()
                                        .onBackPressed();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(getContext(), "Error eliminando: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                            );
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Error consultando stock: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void confirmRemoveFromRack(StockItem item) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Quitar del rack")
                .setMessage("Se eliminará este producto del rack, pero seguirá existiendo en el catálogo.")
                .setPositiveButton("Quitar", (d,w) -> db.collection("stock").document(item.id).delete())
                .setNegativeButton("Cancelar", null)
                .show();
    }



    // --- ADAPTER ---
    static class StockAdapter extends RecyclerView.Adapter<StockAdapter.VH> {

        interface OnItemClick { void onClick(StockItem item); }
        interface OnRemoveClick { void onRemove(StockItem item); }

        private final List<StockItem> list;
        private final OnItemClick listener;
        private final OnRemoveClick removeListener;

        StockAdapter(List<StockItem> list, OnItemClick listener, OnRemoveClick removeListener) {
            this.list = list;
            this.listener = listener;
            this.removeListener = removeListener;
        }

        @NonNull
        @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_stock, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull VH holder, int position) {
            StockItem item = list.get(position);

            holder.tvName.setText(item.productName);
            holder.tvBarcode.setText(item.productId);
            holder.tvQty.setText(String.valueOf(item.qty));

            holder.itemView.setOnClickListener(v -> listener.onClick(item));

            holder.btnRemove.setOnClickListener(v -> removeListener.onRemove(item));
        }

        @Override public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvBarcode, tvQty;
            View btnRemove;

            VH(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvName);
                tvBarcode = v.findViewById(R.id.tvBarcode);
                tvQty = v.findViewById(R.id.tvQty);
                btnRemove = v.findViewById(R.id.btnRemove);
            }
        }
    }

}