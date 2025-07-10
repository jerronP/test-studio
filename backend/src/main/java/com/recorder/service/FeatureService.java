package com.recorder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recorder.config.AIModelConfig;
import com.recorder.config.AppConfig;
import com.recorder.driver.WebDriverFactory;
import com.recorder.model.BrowserAction;
import com.recorder.model.FeatureCleanRequest;
import com.recorder.model.Locator;
import com.recorder.model.OllamaRequest;
import com.recorder.util.AIClient;
import com.recorder.util.FeatureUtils;
import jakarta.ws.rs.core.Response;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class FeatureService {
    public Response generateFeature() {
        List<BrowserAction> actions = RecordingService.getActions();
        List<Locator> locators = LocatorService.getRawLocators();
        String feature = FeatureUtils.createFeatureFromActions(actions,locators);

        try (FileWriter writer = new FileWriter("recorded.feature")) {
            writer.write(feature);
        } catch (IOException e) {
            return Response.serverError().entity("Failed to write feature file").build();
        }
        return Response.ok(feature).build();
    }

    public Response cleanFeature(FeatureCleanRequest request) {
        String cleaned = FeatureUtils.cleanText(request.getRawFeature());
        OllamaRequest aiRequest = new OllamaRequest(AIModelConfig.MODEL_NAME, "Improve this Cucumber feature:\n" + cleaned, false);

        try {
            String response = AIClient.sendToAI(aiRequest);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(response);
            return Response.ok(node.get("response").asText().trim()).build();
        } catch (Exception e) {
            return Response.serverError().entity("AI call failed: " + e.getMessage()).build();
        }
    }
}
