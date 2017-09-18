package com.globocom.grou.groot.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.bson.types.ObjectId;

import java.io.Serializable;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class Loader implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonIgnore
    private ObjectId id;

    private String url;

    public Loader() {
        this("UNDEF");
    }

    public Loader(String url) {
        this.url = url;
    }

    public ObjectId getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }
}
