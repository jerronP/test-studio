package com.recorder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recorder.driver.WebDriverFactory;
import com.recorder.model.AIAgentRequest;
import com.recorder.model.AIRequest;
import com.recorder.util.AIClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
@Slf4j
public class AIAgentService {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final AIClient aiClient;

    public AIAgentService(AIClient aiClient) {
        this.aiClient = aiClient;
    }

    public Response processPrompt(AIAgentRequest request) {
        WebDriver driver = WebDriverFactory.createDriver();

        try {
            String url = request.getUrl();
            String instruction = request.getPrompt();

            driver.get(url);
            Thread.sleep(3000); // initial wait

            // Get list of steps
            String stepPrompt = "Convert this user instruction into a list of clear browser steps:\n" + instruction;
            String rawSteps = aiClient.query(stepPrompt);
            List<String> allSteps = parseSteps(rawSteps);

            log.info("📝 Steps parsed: {}", allSteps.size());

            while (!allSteps.isEmpty()) {
                String dom = (String) ((JavascriptExecutor) driver).executeScript("return document.documentElement.outerHTML;");
                String actionPrompt = buildActionPrompt(dom, allSteps);
                String aiResponse = aiClient.query(actionPrompt);

                List<AIActionStep> applicableSteps = parseAIResponse(aiResponse);

                log.info("✅ AI returned {} actionable steps for current DOM", applicableSteps.size());

                for (AIActionStep step : applicableSteps) {
                    try {
                        log.info("Step : {}", step);
                        WebElement el = new WebDriverWait(driver, Duration.ofSeconds(10))
                                .until(d -> d.findElement(By.cssSelector(step.selector)));

                        if (step.step.toLowerCase().contains("click")) {
                            el.click();
                        } else if (step.step.toLowerCase().contains("enter") || step.step.toLowerCase().contains("type")) {
                            String val = extractValueFromStep(step.step);
                            el.clear();
                            el.sendKeys(val);
                        }

                        log.info("➡️ Performed: {}", step.step);
                    } catch (Exception e) {
                        log.warn("⚠️ Failed to perform: {} using selector: {}", step.step, step.selector);
                    }

                    Thread.sleep(1000);
                }

                allSteps.removeAll(applicableSteps.stream().map(s -> s.step).toList());

                if (allSteps.isEmpty()) {
                    log.info("🎉 All steps executed.");
                    break;
                }

                Thread.sleep(3000); // wait before next DOM evaluation
            }

            return Response.ok("AI-driven execution completed.").build();

        } catch (Exception e) {
            log.error("❌ AI execution failed", e);
            return Response.serverError().entity("AI error: " + e.getMessage()).build();
        } finally {
            WebDriverFactory.quitDriver();
        }
    }

    private String buildActionPrompt(String dom, List<String> steps) {
        StringBuilder sb = new StringBuilder("Given the DOM:\n");
        sb.append(dom).append("\nWhich of the following steps can be done? Return JSON list:\n");
        for (String step : steps) {
            sb.append("- ").append(step).append("\n");
        }
        sb.append("Respond in format: [{\"step\": \"...\", \"selector\": \"...\"}]");
        return sb.toString();
    }

    private List<AIActionStep> parseAIResponse(String json) throws Exception {
        List<AIActionStep> results = new ArrayList<>();
        JsonNode array = mapper.readTree(json);
        if (array.isArray()) {
            for (JsonNode node : array) {
                results.add(new AIActionStep(
                        node.get("step").asText(),
                        node.get("selector").asText()
                ));
            }
        }
        return results;
    }

    private List<String> parseSteps(String raw) {
        return Arrays.stream(raw.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String extractValueFromStep(String step) {
        Matcher matcher = Pattern.compile("(?i)(?:type|enter) ['\"]?(.*?)['\"]?").matcher(step);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static class AIActionStep {
        String step;
        String selector;

        AIActionStep(String step, String selector) {
            this.step = step;
            this.selector = selector;
        }
    }
}
