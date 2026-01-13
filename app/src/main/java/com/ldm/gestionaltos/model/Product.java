package com.ldm.gestionaltos.model;

public class Product {
    public String id;            // se puede usar barcode como id (valorar)
    public String barcode;       // EAN/UPC
    public String reference;     // referencia interna
    public String name;

    public Product() {
    }

    public Product(String id, String barcode, String reference, String name) {
        this.id = id;
        this.barcode = barcode;
        this.reference = reference;
        this.name = name;
    }
}
