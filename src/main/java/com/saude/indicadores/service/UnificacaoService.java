package com.saude.indicadores.service;

import com.saude.indicadores.model.Cidadao;
import com.saude.indicadores.model.Indicador;
import com.saude.indicadores.model.Indicador.TipoIndicador;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Serviço principal de unificação de dados.
 *
 * Realiza o "join" entre a base cadastral do e-SUS (CSV) e as listas
 * nominais de indicadores SIAPS (XLSX), usando CPF como chave primária
 * e CNS como fallback quando o CPF não está disponível.
 *
 * Estratégia de merge:
 *   1. Cidadão existe no CSV e no XLSX → enriquece o objeto Cidadao com o Indicador
 *   2. Cidadão existe apenas no XLSX → cria um Cidadao mínimo (dados apenas do SIAPS)
 *   3. Cidadão existe apenas no CSV → fica sem indicadores (normal)
 */
public class UnificacaoService {

    private static final Logger LOG = Logger.getLogger(UnificacaoService.class.getName());

    private final CsvAcompanhamentoService csvService = new CsvAcompanhamentoService();
    private final XlsxIndicadorService xlsxService  = new XlsxIndicadorService();

    // Base cadastral principal
    private Map<String, Cidadao> baseCadastral = new LinkedHashMap<>();

    // Listeners para progresso
    private final List<ProgressListener> listeners = new ArrayList<>();

    public interface ProgressListener {
        void onProgress(String mensagem, int percentual);
    }

    public void addProgressListener(ProgressListener l) { listeners.add(l); }

    private void notificar(String msg, int pct) {
        LOG.info(msg);
        listeners.forEach(l -> l.onProgress(msg, pct));
    }

    // =========================================================================
    // API principal
    // =========================================================================

    /**
     * Carrega a base cadastral do CSV e armazena internamente.
     */
    public void carregarBaseCadastral(File csvFile) throws IOException {
        notificar("Carregando base cadastral: " + csvFile.getName(), 5);
        baseCadastral = csvService.lerCidadaos(csvFile);
        notificar(String.format("Base cadastral carregada: %d cidadãos.", baseCadastral.size()), 20);
    }

    /**
     * Adiciona um arquivo XLSX de indicadores à base cadastral já carregada.
     * Pode ser chamado múltiplas vezes para carregar indicadores diferentes.
     *
     * @param xlsxFile arquivo XLSX da lista nominal
     * @param tipo     tipo do indicador (null para auto-detecção)
     */
    public void mergeIndicador(File xlsxFile, TipoIndicador tipo) throws IOException {
        if (baseCadastral.isEmpty()) {
            throw new IllegalStateException("Base cadastral não carregada. Chame carregarBaseCadastral() primeiro.");
        }

        TipoIndicador tipoFinal = tipo != null ? tipo : XlsxIndicadorService.detectarTipo(xlsxFile.getName());
        String nomeTipo = tipoFinal != null ? tipoFinal.name() : xlsxFile.getName();

        notificar("Processando indicador: " + nomeTipo, -1);

        Map<String, Indicador> indicadores = xlsxService.lerIndicadores(xlsxFile, tipoFinal);

        int encontrados = 0;
        int naoEncontrados = 0;

        for (Map.Entry<String, Indicador> entry : indicadores.entrySet()) {
            String chave = entry.getKey();
            Indicador ind = entry.getValue();

            Cidadao cidadao = baseCadastral.get(chave);

            if (cidadao == null) {
                // Tenta busca alternativa: cidadão pode estar no CSV com CPF
                // e no SIAPS com CNS (ou vice-versa)
                cidadao = buscarPorChaveAlternativa(chave);
            }

            if (cidadao != null) {
                cidadao.adicionarIndicador(nomeTipo, ind);
                encontrados++;
            } else {
                // Cidadão presente no SIAPS mas não no CSV → cria entrada mínima
                Cidadao novo = criarCidadaoMinimo(chave, ind);
                baseCadastral.put(chave, novo);
                novo.adicionarIndicador(nomeTipo, ind);
                naoEncontrados++;
            }
        }

        notificar(String.format("[%s] %d vinculados, %d novos (apenas SIAPS).",
                  nomeTipo, encontrados, naoEncontrados), -1);
    }

    /**
     * Processa múltiplos arquivos XLSX de uma vez.
     */
    public void mergeIndicadores(List<File> xlsxFiles) throws IOException {
        int total = xlsxFiles.size();
        for (int i = 0; i < total; i++) {
            File f = xlsxFiles.get(i);
            int pct = 20 + (int) ((double) i / total * 70);
            notificar("Processando " + (i + 1) + "/" + total + ": " + f.getName(), pct);
            mergeIndicador(f, null);
        }
        notificar("Unificação concluída. Total: " + baseCadastral.size() + " cidadãos.", 100);
    }

    /**
     * Retorna a lista unificada de cidadãos.
     */
    public List<Cidadao> getCidadaos() {
        return new ArrayList<>(baseCadastral.values());
    }

    /**
     * Filtra cidadãos por microárea.
     */
    public List<Cidadao> filtrarPorMicroarea(String microarea) {
        if (microarea == null || microarea.isBlank() || microarea.equals("TODAS")) {
            return getCidadaos();
        }
        return baseCadastral.values().stream()
            .filter(c -> microarea.equalsIgnoreCase(c.getMicroarea()))
            .collect(Collectors.toList());
    }

    /**
     * Filtra cidadãos que possuem um determinado tipo de indicador.
     */
    public List<Cidadao> filtrarPorIndicador(String nomeIndicador) {
        if (nomeIndicador == null || nomeIndicador.isBlank() || nomeIndicador.equals("TODOS")) {
            return getCidadaos();
        }
        return baseCadastral.values().stream()
            .filter(c -> c.temIndicador(nomeIndicador))
            .collect(Collectors.toList());
    }

    /**
     * Filtra cidadãos com indicadores pendentes (NM vazio/zero).
     */
    public List<Cidadao> filtrarPendentes() {
        return baseCadastral.values().stream()
            .filter(Cidadao::possuiIndicadorPendente)
            .collect(Collectors.toList());
    }

    /**
     * Filtra combinando microárea + indicador + pendência.
     */
    public List<Cidadao> filtrar(String microarea, String indicador, boolean apenasPendentes) {
        List<Cidadao> resultado = getCidadaos();

        if (microarea != null && !microarea.isBlank() && !microarea.equalsIgnoreCase("TODAS")) {
            resultado = resultado.stream()
                .filter(c -> microarea.equalsIgnoreCase(c.getMicroarea()))
                .collect(Collectors.toList());
        }

        if (indicador != null && !indicador.isBlank() && !indicador.equalsIgnoreCase("TODOS")) {
            resultado = resultado.stream()
                .filter(c -> c.temIndicador(indicador))
                .collect(Collectors.toList());
        }

        if (apenasPendentes) {
            resultado = resultado.stream()
                .filter(Cidadao::possuiIndicadorPendente)
                .collect(Collectors.toList());
        }

        return resultado;
    }

    /**
     * Retorna lista de microáreas únicas presentes na base.
     */
    public List<String> getMicroareas() {
        return baseCadastral.values().stream()
            .map(Cidadao::getMicroarea)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Retorna lista de tipos de indicadores presentes na base.
     */
    public Set<String> getIndicadoresPresentes() {
        Set<String> set = new LinkedHashSet<>();
        baseCadastral.values().forEach(c -> set.addAll(c.getIndicadores().keySet()));
        return set;
    }

    // =========================================================================
    // Métodos internos
    // =========================================================================

    /**
     * Tenta encontrar um cidadão usando a chave alternativa.
     * Ex: se a chave é CNS:X, tenta encontrar pelo CPF vinculado ao mesmo CNS.
     */
    private Cidadao buscarPorChaveAlternativa(String chave) {
        // Busca linear – adequada para bases de ~500-5000 registros
        if (chave.startsWith("CNS:")) {
            String cns = chave.substring(4);
            return baseCadastral.values().stream()
                .filter(c -> cns.equals(c.getCns()))
                .findFirst().orElse(null);
        } else if (chave.startsWith("CPF:")) {
            String cpf = chave.substring(4);
            return baseCadastral.values().stream()
                .filter(c -> cpf.equals(c.getCpf()))
                .findFirst().orElse(null);
        }
        return null;
    }

    /**
     * Cria um Cidadao mínimo a partir dos dados disponíveis no SIAPS (sem cadastro no CSV).
     */
    private Cidadao criarCidadaoMinimo(String chave, Indicador ind) {
        String cpf = null;
        String cns = null;
        if (chave.startsWith("CPF:")) cpf = chave.substring(4);
        else if (chave.startsWith("CNS:")) cns = chave.substring(4);

        Cidadao c = new Cidadao();
        c.setCpf(cpf);
        c.setCns(cns);
        c.setNome("(Sem cadastro no e-SUS)");
        c.setDataNascimento(ind.getNascimento());
        c.setSexo(ind.getSexo());
        c.setMicroarea("Não informada");
        return c;
    }

    // Permite substituir a base para testes
    void setBaseCadastral(Map<String, Cidadao> base) {
        this.baseCadastral = base;
    }
}
