package com.ldm.gestionaltos.model;

public class StockItem {
    public String id;
    public String rackId;
    public String productId;
    public int qty;

    public long updatedAt;
    public String updatedBy;

    public StockItem() {
    }

    public StockItem(String id, String rackId, String productId, int qty) {
        this.id = id;
        this.rackId = rackId;
        this.productId = productId;
        this.qty = qty;
    }
}

