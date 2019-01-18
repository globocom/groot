package com.globocom.grou.groot.test.properties;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;
import java.util.List;

@JsonInclude(NON_NULL)
public class SslProperty implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<String> ciphers;

    public List<String> getCiphers() {
        return ciphers;
    }

    public SslProperty setCiphers(List<String> ciphers) {
        this.ciphers = ciphers;
        return this;
    }
}
