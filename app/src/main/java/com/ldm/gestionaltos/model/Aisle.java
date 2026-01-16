package com.ldm.gestionaltos.model;

import com.google.firebase.firestore.DocumentId;

public class Aisle {
    @DocumentId
    public String id;
    public String sectionId;
    public Aisle() {}

    public Aisle(String id, String sectionId) {
        this.id = id;
        this.sectionId = sectionId;
    }
}