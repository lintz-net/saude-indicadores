package com.saude.indicadores.selenium;

import com.saude.indicadores.selenium.driver.DriverFactory;
import com.saude.indicadores.selenium.pages.LoginPage;
import com.saude.indicadores.selenium.pages.MenuPage;
import com.saude.indicadores.selenium.pages.RelatorioPage;
import org.openqa.selenium.WebDriver;

import java.io.File;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Serviço que orquestra o fluxo completo de download via Selenium:
 *   1. Abre o Chrome
 *   2. Faz login no e-SUS com as credenciais fornecidas
 *   3. Navega até Cidadãos Vinculados
 *   4. Seleciona a equipe, busca e exporta CSV
 *   5. Aguarda o download e retorna o arquivo
 *   6. Faz logout e fecha o browser
 *
 * Projetado para ser executado em uma thread de background (SwingWorker),
 * reportando progresso via callback {@code Consumer<String>}.
 */
public class SeleniumDownloadService {

    private static final Logger LOG = Logger.getLogger(SeleniumDownloadService.class.getName());

    // Pasta de download padrão — pode ser sobrescrita via setter
    private String pastaDownload = System.getProperty("user.home") + "\\Downloads";

    // Nome da equipe para filtro no relatório
    private String nomeEquipe = "itabaiana";

    // Timeout aguardando o arquivo aparecer na pasta
    private int timeoutDownloadSegundos = 60;

    public void setPastaDownload(String pasta)          { this.pastaDownload = pasta; }
    public void setNomeEquipe(String equipe)            { this.nomeEquipe = equipe; }
    public void setTimeoutDownload(int segundos)        { this.timeoutDownloadSegundos = segundos; }

    /**
     * Executa o fluxo completo e retorna o arquivo CSV baixado.
     *
     * @param usuario  CPF do operador (ex.: "034.465.914-35")
     * @param senha    senha do operador
     * @param progresso callback para reportar mensagens de progresso à UI
     * @return arquivo CSV de acompanhamento de cidadãos vinculados
     */
    public File executar(String usuario, String senha, Consumer<String> progresso)
            throws Exception {

        // Garantir que a pasta de download existe
        File dir = new File(pastaDownload);
        if (!dir.exists()) {
            dir.mkdirs();
            LOG.info("Pasta de download criada: " + pastaDownload);
        }

        WebDriver driver = null;
        try {
            progresso.accept("Iniciando Chrome...");
            driver = DriverFactory.createDriver(pastaDownload);

            LoginPage    login    = new LoginPage(driver);
            MenuPage     menu     = new MenuPage(driver);
            RelatorioPage relatorio = new RelatorioPage(driver);

            progresso.accept("Acessando o e-SUS...");
            login.acessar();

            progresso.accept("Realizando login...");
            login.login(usuario, senha);

            progresso.accept("Navegando até Cidadãos Vinculados...");
            menu.acessarCidadaosVinculados();

            progresso.accept("Selecionando equipe \"" + nomeEquipe + "\"...");
            relatorio.selecionarEquipe(nomeEquipe);

            progresso.accept("Buscando cidadãos...");
            relatorio.buscar();

            progresso.accept("Gerando CSV...");
            relatorio.gerarRelatorioCSV();

            progresso.accept("Aguardando download...");
            File arquivo = relatorio.esperarDownloadAndRetornarUltimoArquivo(
                pastaDownload, timeoutDownloadSegundos);

            progresso.accept("Download concluído: " + arquivo.getName());
            LOG.info("Arquivo baixado: " + arquivo.getAbsolutePath());
            return arquivo;

        } finally {
            if (driver != null) {
                try {
                    new LoginPage(driver).logout();
                } catch (Exception ignored) {}
                driver.quit();
            }
        }
    }
}
