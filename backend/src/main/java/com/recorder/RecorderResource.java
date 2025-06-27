package com.recorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recorder.model.BrowserAction;
import com.recorder.model.FeatureCleanRequest;
import com.recorder.model.OllamaRequest;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;
import java.util.Map;

@Path("/record")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
public class RecorderResource {

    private static final List<BrowserAction> actions = new ArrayList<>();
    private static String baseUrl = "https://example.com";  // default URL

    public WebDriver createChromeDriverWithCorsDisabled() {
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-web-security");
        options.addArguments("--allow-running-insecure-content");
        options.addArguments("--user-data-dir=/tmp/temp_chrome_profile");

        return new ChromeDriver(options);
    }

    @POST
    @Path("/start")
    public Response start(Map<String, String> payload) {
        baseUrl = payload.get("url");

        if (baseUrl == null || baseUrl.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("URL is missing").build();
        }

        WebDriverManager.chromedriver().setup();
        WebDriver driver = createChromeDriverWithCorsDisabled();
        try {
            driver.get(baseUrl);

            String recorderScript = """
        (function() {
          function send(action, el, value = '') {
            const payload = {
              action: action,
              selector: el.tagName.toLowerCase() + (el.id ? ('#' + el.id) : ''),
              value: value,
              tag: el.tagName,
              text: el.innerText || ''
            };

            fetch('http://localhost:8080/record/log', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(payload)
            });
          }

          document.addEventListener('click', function(e) {
            send('click', e.target);
          });

          document.addEventListener('input', function(e) {
            send('input', e.target, e.target.value);
          });

          console.log('Recorder injected');
        })();
        """;

            ((JavascriptExecutor) driver).executeScript(recorderScript);

            return Response.ok("Recording started at: " + baseUrl).build();

        } catch (Exception e) {
            log.error(String.valueOf(e));
            return Response.serverError().entity("Recording error: " + e.getMessage()).build();
        }
    }


    @POST
    @Path("/stop")
    public Response stopRecording() {
        List<BrowserAction> snapshot = new ArrayList<>(actions);
        return Response.ok(snapshot).build();
    }

    @POST
    @Path("/log")
    public Response log(BrowserAction action) {
        log.info("Received action: {}", action);
        actions.add(action);
        return Response.ok().build();
    }

    @GET
    @Path("/dump")
    public List<BrowserAction> dump() {
        return actions;
    }

    @POST
    @Path("/clear")
    public Response clear() {
        actions.clear();
        return Response.ok().build();
    }
    @POST
    @Path("/feature")
    @Produces(MediaType.TEXT_PLAIN)
    public Response generateFeatureFile() {
        StringBuilder sb = new StringBuilder();
        sb.append("Feature: Recorded User Interaction\n");
        sb.append("  Scenario: User interaction playback\n");

        for (BrowserAction action : actions) {
            if ("click".equalsIgnoreCase(action.getAction())) {
                sb.append("    When I click on \"").append(action.getSelector()).append("\"\n");
            } else if ("input".equalsIgnoreCase(action.getAction())) {
                sb.append("    And I input \"").append(action.getValue())
                        .append("\" into \"").append(action.getSelector()).append("\"\n");
            }
        }

        String featureText = sb.toString();

        try {
            File featureFile = new File("recorded.feature");
            try (FileWriter writer = new FileWriter(featureFile)) {
                writer.write(featureText);
            }
        } catch (IOException e) {
            return Response.serverError().entity("Failed to write feature file").build();
        }

        return Response.ok(featureText).build();
    }

    @POST
    @Path("/playback")
    public Response playback() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        WebDriver driver = new ChromeDriver(options);

        try {
            driver.get(baseUrl);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            for (BrowserAction action : actions) {
                System.out.printf("Executing: %s on %s with value: %s%n",
                        action.action, action.selector, action.value);

                try {
                    WebElement el = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(action.selector)));

                    if ("click".equalsIgnoreCase(action.action)) {
                        el.click();
                    } else if ("input".equalsIgnoreCase(action.action)) {
                        el.clear();
                        el.sendKeys(action.value);
                    }

                    Thread.sleep(500); // Optional delay

                } catch (Exception ex) {
                    System.err.println("Error processing action: " + action + " - " + ex.getMessage());
                }
            }

        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        } finally {
            driver.quit();
        }

        return Response.ok("Playback done").build();
    }

    @POST
    @Path("/clean-feature")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response cleanFeature(FeatureCleanRequest request) {
        String rawFeatureText = request.getRawFeature();
        String cleaned = rawFeatureText
                .replaceAll("(?m)^\\s+", "")
                .replaceAll(" {2,}", " ")
                .trim();

        try {
            HttpClient client = HttpClient.newHttpClient();
            String prompt = "Clean up and improve this Cucumber feature file:\n" + cleaned;
            OllamaRequest ollamaRequest = new OllamaRequest("codellama", prompt, false);

            ObjectMapper mapper = new ObjectMapper();
            String requestBody = mapper.writeValueAsString(ollamaRequest);

            log.info("Sending request to Ollama:\n{}", requestBody);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode jsonNode = mapper.readTree(response.body());
            String cleanedFeature = jsonNode.get("response").asText().trim();
            return Response.ok(cleanedFeature).build();

        } catch (Exception e) {
            return Response.serverError().entity("Failed to connect to Ollama: " + e.getMessage()).build();
        }
    }
}