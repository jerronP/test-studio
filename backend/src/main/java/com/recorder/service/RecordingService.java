package com.recorder.service;

import com.recorder.config.AppConfig;
import com.recorder.driver.WebDriverFactory;
import com.recorder.model.BrowserAction;
import com.recorder.util.RecorderScriptUtil;
import jakarta.enterprise.context.ApplicationScoped;
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
import java.util.List;

@Slf4j
@ApplicationScoped
public class RecordingService {

    private static final List<BrowserAction> actions = new ArrayList<>();
    private static String recordedUrl = AppConfig.BASE_URL;

    public Response start(String url) {
        clear();
        LocatorService.clear();

        if (url == null || url.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("URL is missing").build();
        }

        WebDriver driver = WebDriverFactory.createDriver();
        recordedUrl = url != null ? url : AppConfig.BASE_URL;
        try {
            driver.get(recordedUrl);
            ((JavascriptExecutor) driver).executeScript(RecorderScriptUtil.getRecorderScript());
            log.info("Recorder script injected.");
            return Response.ok("Recording started at: " + url).build();
        } catch (Exception e) {
            log.error("Error injecting script or navigating", e);
            WebDriverFactory.quitDriver();
            return Response.serverError().entity("Recording error: " + e.getMessage()).build();
        }
    }

    public Response playback() {
        List<BrowserAction> actions = RecordingService.getActions();
        WebDriver driver = WebDriverFactory.createDriver();
        driver.get(recordedUrl);

        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            for (BrowserAction action : actions) {
                By locator;
                switch (action.getFindBy()) {
                    case "id":
                        locator = By.id(action.getSelector());
                        break;
                    case "name":
                        locator = By.name(action.getSelector());
                        break;
                    case "xpath":
                        locator = By.xpath(action.getSelector());
                        break;
                    case "cssSelector":
                    default:
                        locator = By.cssSelector(action.getSelector());
                        break;
                }

                WebElement el = wait.until(ExpectedConditions.elementToBeClickable(locator));

                if ("click".equalsIgnoreCase(action.getAction())) {
                    el.click();
                } else if ("input".equalsIgnoreCase(action.getAction())) {
                    el.clear();
                    el.sendKeys(action.getValue());
                }

                Thread.sleep(500); // add delay for stability
            }
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        } finally {
            WebDriverFactory.quitDriver();
        }

        return Response.ok("Playback complete").build();
    }

    public Response stop() {
        WebDriverFactory.quitDriver();
        return Response.ok(new ArrayList<>(actions)).build();
    }

    public Response log(BrowserAction action) {
        actions.add(action);
        return Response.ok().build();
    }

    public static void clear() {
        actions.clear();
    }

    public static List<BrowserAction> getActions() {
        return actions;
    }
}
