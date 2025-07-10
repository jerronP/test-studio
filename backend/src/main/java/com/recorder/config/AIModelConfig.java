package com.recorder.config;

public class AIModelConfig {
    public static final String MODEL_NAME = System.getenv().getOrDefault("AI_MODEL", "codellama");
    public static final String ENDPOINT = System.getenv().getOrDefault("AI_ENDPOINT", "http://localhost:11434/api/generate");
}
