package com.saude.indicadores.selenium.driver;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Fábrica do WebDriver Chrome.
 * Configura download automático para a pasta informada.
 */
public class DriverFactory {

    /**
     * Cria um ChromeDriver com download automático configurado.
     *
     * @param pastaDownload caminho absoluto da pasta de destino dos arquivos
     * @return instância do WebDriver pronta para uso
     */
    public static WebDriver createDriver(String pastaDownload) {
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory",           pastaDownload);
        prefs.put("download.prompt_for_download",         false);
        prefs.put("download.directory_upgrade",           true);
        prefs.put("safebrowsing.enabled",                 true);
        prefs.put("profile.default_content_setting_values.notifications", 2);
        options.setExperimentalOption("prefs", prefs);

        return new ChromeDriver(options);
    }
}
