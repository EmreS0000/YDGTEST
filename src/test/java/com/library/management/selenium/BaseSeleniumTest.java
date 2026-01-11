package com.library.management.selenium;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public abstract class BaseSeleniumTest {

    protected WebDriver driver;
    protected WebDriverWait wait;
    protected String baseUrl;
    protected String frontendUrl;

    @BeforeAll
    static void setupClass() {
        WebDriverManager.chromedriver().setup();
    }

    @BeforeEach
    void setup() {
        // Load URLs from System Properties (set by Jenkins/Maven), fallback to
        // localhost
        frontendUrl = System.getProperty("FRONTEND_URL", "http://localhost:5173");
        String backendUrl = System.getProperty("BASE_URL", "http://localhost:8080");
        baseUrl = backendUrl + "/api/v1";

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--start-maximized");

        // Add remote debugging if needed or other args
        String extraArgs = System.getProperty("CHROME_ARGS");
        if (extraArgs != null && !extraArgs.isEmpty()) {
            for (String arg : extraArgs.split(";")) {
                options.addArguments(arg);
            }
        }

        // If running in Docker (Jenkins), use RemoteWebDriver logic or configured
        // driver
        // For simplicity, we assume Selenium Grid usage if SELENIUM_URL is set
        String seleniumUrl = System.getProperty("SELENIUM_URL");
        if (seleniumUrl != null && !seleniumUrl.isEmpty()) {
            try {
                driver = new org.openqa.selenium.remote.RemoteWebDriver(new java.net.URL(seleniumUrl), options);
            } catch (java.net.MalformedURLException e) {
                throw new RuntimeException("Invalid Selenium URL", e);
            }
        } else {
            driver = new ChromeDriver(options);
        }

        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(20));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(10));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));

        wait = new WebDriverWait(driver, Duration.ofSeconds(30));
    }

    @AfterEach
    void teardown() {
        if (driver != null) {
            driver.quit();
        }
    }

    protected void waitForDomReady() {
        wait.until(d -> ((JavascriptExecutor) d)
                .executeScript("return document.readyState")
                .equals("complete"));
    }

    protected void openFrontend(String path) {
        driver.navigate().to(frontendUrl + path);
        waitForDomReady();
    }
}
