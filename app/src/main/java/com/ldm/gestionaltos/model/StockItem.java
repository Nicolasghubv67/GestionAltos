package com.ldm.gestionaltos.model;

import com.google.firebase.firestore.DocumentId;

public class StockItem {
    @DocumentId
    public String id; // ID autogenerado por Firestore

    public String rackId;     // Dónde está (Ej: "E123")
    public String productId;  // EAN
    public int qty;

    // --- CAMPOS PARA BÚSQUEDA RÁPIDA (Denormalizados) ---
    // Guardar datos aquí para no tener que leer el producto cada vez
    public String productName;
    public String productReference;
    public String searchName;
    public StockItem() {}

    public StockItem(String rackId, String productId, int qty,
                     String productName, String productReference) {
        this.rackId = rackId;
        this.productId = productId;
        this.qty = qty;
        this.productName = productName;
        this.productReference = productReference;
        this.searchName = productName != null ? productName.toLowerCase() : "";
    }
}