package com.saude.indicadores.selenium.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Page Object para login e logout no e-SUS.
 */
public class LoginPage {

    private final WebDriver    driver;
    private final WebDriverWait wait;

    private static final String URL_BASE   = "https://esusmaragogi.shifttelecom.com.br/";
    private static final String URL_LOGOUT = "https://esusmaragogi.shifttelecom.com.br/logout";

    public LoginPage(WebDriver driver) {
        this.driver = driver;
        this.wait   = new WebDriverWait(driver, Duration.ofSeconds(20));
    }

    public void acessar() {
        driver.get(URL_BASE);
    }

    /**
     * Realiza login com CPF e senha informados pelo usuário na UI.
     *
     * @param usuario CPF do operador (com ou sem formatação)
     * @param senha   senha do operador
     */
    public void login(String usuario, String senha) {
        wait.until(ExpectedConditions
            .visibilityOfElementLocated(By.name("username")))
            .sendKeys(usuario);

        driver.findElement(By.name("password")).sendKeys(senha);
        driver.findElement(By.cssSelector("[data-cy='LoginForm.access-button']")).click();

        // Aguarda elemento da tela principal confirmar login bem-sucedido
        wait.until(ExpectedConditions
            .visibilityOfElementLocated(By.xpath("//*[contains(text(),'Lista de atendimentos')]")));
    }

    public void logout() {
        driver.get(URL_LOGOUT);
    }
}
