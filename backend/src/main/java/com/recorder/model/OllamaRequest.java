package com.recorder.model;

import lombok.Getter;
import lombok.Setter;


public class OllamaRequest {
    @Getter
    @Setter
    private String model;

    @Getter
    @Setter
    private String prompt;
    private boolean stream;

    public OllamaRequest() {}

    public OllamaRequest(String model, String prompt, boolean stream) {
        this.model = model;
        this.prompt = prompt;
        this.stream = stream;
    }

    public boolean isStream() { return stream; }
    public void setStream(boolean stream) { this.stream = stream; }
}