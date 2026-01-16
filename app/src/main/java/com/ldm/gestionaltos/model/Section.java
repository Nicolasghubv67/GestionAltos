package com.ldm.gestionaltos.model;

import com.google.firebase.firestore.DocumentId;

public class Section {
    @DocumentId public String id;
    public String name;
    public Section() {}
    public Section(String id, String name) { this.id = id; this.name = name; }
}
