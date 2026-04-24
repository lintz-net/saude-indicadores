package com.saude.indicadores.selenium.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Page Object para a tela de relatório / acompanhamento de cidadãos vinculados.
 */
public class RelatorioPage {

    private final WebDriver     driver;
    private final WebDriverWait wait;

    public RelatorioPage(WebDriver driver) {
        this.driver = driver;
        this.wait   = new WebDriverWait(driver, Duration.ofSeconds(30));
    }

    /**
     * Seleciona a equipe no campo de autocomplete.
     *
     * @param nome parte do nome da equipe (ex.: "itabaiana")
     */
    public void selecionarEquipe(String nome) {
        WebElement combo = wait.until(ExpectedConditions.elementToBeClickable(
            By.xpath("//label[contains(text(),'Equipe responsável')]/following::input[1]")));

        combo.click();
        combo.clear();
        combo.sendKeys(nome);

        WebElement opcao = wait.until(ExpectedConditions.visibilityOfElementLocated(
            By.xpath("//div[@role='listbox']//*[contains(text(),'" + nome + "')]")));

        opcao.click();
    }

    /**
     * Clica no botão "Buscar cidadãos".
     */
    public void buscar() throws InterruptedException {
        WebElement botao = wait.until(ExpectedConditions.elementToBeClickable(
            By.xpath("//span[contains(text(),'Buscar cidadãos')]/ancestor::button")));

        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", botao);
        Thread.sleep(500); // aguarda renderização React
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", botao);
    }

    /**
     * Clica no botão "Exportar CSV".
     */
    public void gerarRelatorioCSV() {
        WebElement botao = wait.until(ExpectedConditions.presenceOfElementLocated(
            By.xpath("//span[contains(text(),'Exportar CSV')]/ancestor::button")));

        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", botao);
        wait.until(ExpectedConditions.elementToBeClickable(botao));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", botao);
    }

    /**
     * Aguarda o download concluir (arquivo .crdownload desaparecer)
     * e retorna o CSV mais recente na pasta.
     *
     * @param pasta           caminho da pasta de download
     * @param timeoutSegundos tempo máximo de espera
     * @return arquivo CSV baixado
     */
    public File esperarDownloadAndRetornarUltimoArquivo(String pasta, int timeoutSegundos)
            throws InterruptedException {

        File dir = new File(pasta);
        int tempo = 0;

        while (tempo < timeoutSegundos) {
            File[] emAndamento = dir.listFiles((d, nome) -> nome.endsWith(".crdownload"));
            if (emAndamento == null || emAndamento.length == 0) {
                System.out.println("Download finalizado!");
                return getUltimoArquivoCsv(pasta);
            }
            Thread.sleep(1000);
            tempo++;
        }

        throw new RuntimeException("Timeout aguardando download após " + timeoutSegundos + "s");
    }

    private File getUltimoArquivoCsv(String pasta) {
        File dir = new File(pasta);
        File[] arquivos = dir.listFiles(f -> f.getName().endsWith(".csv"));
        if (arquivos == null || arquivos.length == 0)
            throw new RuntimeException("Nenhum arquivo CSV encontrado em: " + pasta);

        return Arrays.stream(arquivos)
            .max(Comparator.comparingLong(File::lastModified))
            .orElseThrow();
    }
}
