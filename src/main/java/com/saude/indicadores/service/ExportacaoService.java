package com.saude.indicadores.service;

import com.saude.indicadores.model.Cidadao;
import com.saude.indicadores.model.Indicador;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

/**
 * Serviço de exportação da visão unificada para CSV.
 * Gera um arquivo consolidado com todos os cidadãos e seus indicadores.
 */
public class ExportacaoService {

    private static final Logger LOG = Logger.getLogger(ExportacaoService.class.getName());
    private static final String SEP = ";";

    /**
     * Exporta a lista de cidadãos para um arquivo CSV consolidado.
     *
     * Colunas fixas: Nome, CPF, CNS, Microárea, Idade, Sexo, Nascimento, Telefone
     * Colunas dinâmicas: uma por tipo de indicador, com status (OK/PENDENTE/-)
     * Colunas finais: Total Indicadores Ativos, Total Pendentes
     */
    public File exportarCsv(List<Cidadao> cidadaos, File destino) throws IOException {
        LOG.info("Exportando CSV com " + cidadaos.size() + " cidadãos para: " + destino.getAbsolutePath());

        // Coletar todos os tipos de indicadores presentes
        Set<String> tiposIndicadores = new LinkedHashSet<>();
        cidadaos.forEach(c -> tiposIndicadores.addAll(c.getIndicadores().keySet()));

        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(destino), StandardCharsets.UTF_8))) {

            // BOM UTF-8 para compatibilidade com Excel
            pw.print('\uFEFF');

            // Linha de geração
            pw.println("# Exportado em: " + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

            // Cabeçalho
            List<String> header = new ArrayList<>(Arrays.asList(
                "Nome", "CPF", "CNS", "Microárea", "Idade", "Sexo", "Data Nasc.", "Telefone"
            ));
            tiposIndicadores.forEach(tipo -> {
                header.add(tipo + " (NM)");
                header.add(tipo + " (DN)");
                header.add(tipo + " Status");
            });
            header.add("Total Indicadores");
            header.add("Pendentes");
            pw.println(String.join(SEP, header));

            // Dados
            for (Cidadao c : cidadaos) {
                List<String> linha = new ArrayList<>();
                linha.add(escapeCsv(c.getNome()));
                linha.add(formatCpf(c.getCpf()));
                linha.add(nullSafe(c.getCns()));
                linha.add(nullSafe(c.getMicroarea()));
                linha.add(nullSafe(c.getIdade()));
                linha.add(nullSafe(c.getSexo()));
                linha.add(nullSafe(c.getDataNascimento()));
                linha.add(nullSafe(c.getTelefone()));

                int totalIndicadores = 0;
                int totalPendentes = 0;

                for (String tipo : tiposIndicadores) {
                    Indicador ind = c.getIndicadores().get(tipo);
                    if (ind != null) {
                        totalIndicadores++;
                        if (ind.isPendente()) totalPendentes++;
                        linha.add(nullSafe(ind.getNm()));
                        linha.add(nullSafe(ind.getDn()));
                        linha.add(ind.isPendente() ? "PENDENTE" : "OK");
                    } else {
                        linha.add("-");
                        linha.add("-");
                        linha.add("-");
                    }
                }

                linha.add(String.valueOf(totalIndicadores));
                linha.add(String.valueOf(totalPendentes));
                pw.println(String.join(SEP, linha));
            }
        }

        LOG.info("Exportação concluída: " + destino.getAbsolutePath());
        return destino;
    }

    /**
     * Gera o nome do arquivo de exportação com timestamp.
     */
    public static String gerarNomeArquivo() {
        return "indicadores-saude-" +
               LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm")) +
               ".csv";
    }

    private String escapeCsv(String valor) {
        if (valor == null) return "";
        if (valor.contains(SEP) || valor.contains("\"") || valor.contains("\n")) {
            return "\"" + valor.replace("\"", "\"\"") + "\"";
        }
        return valor;
    }

    private String nullSafe(String valor) {
        return valor == null ? "" : valor;
    }

    private String formatCpf(String cpf) {
        if (cpf == null || cpf.length() != 11) return nullSafe(cpf);
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." +
               cpf.substring(6, 9) + "-" + cpf.substring(9);
    }
}
