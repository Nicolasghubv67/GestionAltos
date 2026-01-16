package com.ldm.gestionaltos.model;

import com.google.firebase.firestore.DocumentId;
import com.google.type.Date;

public class Rack {
    @DocumentId
    public String id; // Ej: "E123"

    public String sectionId;
    public String aisleId;

    public Rack() {}

    public Rack(String id, String sectionId, String aisleId) {
        this.id = id;
        this.sectionId = sectionId;
        this.aisleId = aisleId;
    }
}