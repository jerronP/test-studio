package com.recorder.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recorder.config.AIModelConfig;
import com.recorder.model.AIRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@ApplicationScoped
@Slf4j
public class AIClient {

    @Inject
    AIModelConfig aiModelConfig;

    public String sendToAI(AIRequest request) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String requestBody = mapper.writeValueAsString(request);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(aiModelConfig.getEndpoint()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    public String sendPrompt(String prompt) throws Exception {
        String json = String.format("""
            {
              "model": "%s",
              "prompt": "Convert user request into automation steps:\\n%s",
              "stream": false
            }
        """, aiModelConfig.getModelName(), prompt.replace("\"", "\\\""));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(aiModelConfig.getEndpoint()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return parseResponse(response.body());
    }

    public String query(String prompt) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        String body = mapper.createObjectNode()
                .put("model", aiModelConfig.getModelName())
                .put("prompt", prompt)
                .put("stream", false)
                .toString();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(aiModelConfig.getEndpoint()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        log.info("AI BEFORE SEND");
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = mapper.readTree(response.body());
        String result = json.get("response").asText();
        log.info("AI Response: {}", result);
        return result;
    }

    private String parseResponse(String rawJson) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(rawJson);
        return root.get("response").asText().trim();
    }
}
