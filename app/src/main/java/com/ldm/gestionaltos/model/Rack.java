package com.ldm.gestionaltos.model;

public class Rack {
    public String id;
    public String code;
    public String sectionId;
    public String aisleId;

    // Para mapa simple, por implementar aun
    public int row;
    public int col;

    public Rack() {
    }

    public Rack(String id, String code, String sectionId, String aisleId) {
        this.id = id;
        this.code = code;
        this.sectionId = sectionId;
        this.aisleId = aisleId;
    }
}

