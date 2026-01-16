package com.ldm.gestionaltos.model;

import com.google.firebase.firestore.DocumentId;

public class Product {
    @DocumentId
    public String barcode; // ID del documento EAN13

    public String internalId;

    public String name;
    public String searchName;

    public Product() {}

    public Product(String barcode, String internalId, String name) {
        this.barcode = barcode;
        this.internalId = internalId;
        this.name = name;
        this.searchName = name != null ? name.toLowerCase() : "";
    }
}