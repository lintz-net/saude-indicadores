package com.saude.indicadores.selenium.pages;

import org.openqa.selenium.WebDriver;

/**
 * Page Object para navegação nos menus do e-SUS.
 */
public class MenuPage {

    private final WebDriver driver;

    private static final String URL_CIDADAOS_VINCULADOS =
        "https://esusmaragogi.shifttelecom.com.br/acompanhamentos/cidadaos-vinculados";

    public MenuPage(WebDriver driver) {
        this.driver = driver;
    }

    public void acessarCidadaosVinculados() {
        driver.get(URL_CIDADAOS_VINCULADOS);
    }
}
