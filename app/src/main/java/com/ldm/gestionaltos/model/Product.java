package com.ldm.gestionaltos.model;

import com.google.firebase.firestore.DocumentId;

public class Product {
    @DocumentId
    public String barcode; // ID del documento EAN13

    public String reference;

    public String name;
    public String searchName;

    public Product() {}

    public Product(String barcode, String reference, String name) {
        this.barcode = barcode;
        this.reference = reference;
        this.name = name;
        this.searchName = name != null ? name.toLowerCase() : "";
    }
}