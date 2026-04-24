package com.saude.indicadores.service;

import com.saude.indicadores.model.Cidadao;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Serviço responsável por ler o arquivo CSV de Acompanhamento de Cidadãos Vinculados
 * exportado pelo e-SUS Atenção Primária.
 *
 * Formato do arquivo:
 *   - Linhas 0-16: metadados do sistema (e-SUS, ministério, filtros, etc.)
 *   - Linha 17 (índice 0-based): cabeçalho dos dados reais
 *   - Linha 18+: dados dos cidadãos
 *
 * Separador: ponto-e-vírgula (;)
 * Encoding: ISO-8859-1 (latin1) — padrão e-SUS
 */
public class CsvAcompanhamentoService {

    private static final Logger LOG = Logger.getLogger(CsvAcompanhamentoService.class.getName());

    // Número de linhas de metadados a pular antes do cabeçalho
    private static final int LINHAS_METADADOS = 17;

    // Charset padrão do e-SUS
    private static final Charset CHARSET = Charset.forName("ISO-8859-1");

    // Índices das colunas no arquivo (baseado na estrutura real observada)
    private static final int COL_EQUIPE    = 0;
    private static final int COL_INE       = 1;
    private static final int COL_MICROAREA = 2;
    private static final int COL_ENDERECO  = 3;
    private static final int COL_CPF_CNS   = 4;
    private static final int COL_NOME      = 5;
    private static final int COL_IDADE     = 6;
    private static final int COL_SEXO      = 7;
    // col 8 = identidade de gênero
    private static final int COL_NASC      = 9;
    private static final int COL_TELEFONE  = 10;

    /**
     * Tenta localizar automaticamente o arquivo mais recente na pasta de downloads
     * do Selenium (configurável via propriedade do sistema ou variável de ambiente).
     */
    public static File localizarArquivoAutomatico() {
        // Verifica variável de ambiente primeiro
        String pastaEnv = System.getenv("ESUS_DOWNLOAD_DIR");
        if (pastaEnv != null) {
            File pasta = new File(pastaEnv);
            return buscarMaisRecente(pasta, "acompanhamento-cidadaos-vinculados");
        }

        // Caminhos padrão por OS
        String userHome = System.getProperty("user.home");
        List<Path> candidatos = Arrays.asList(
            Paths.get(userHome, "Downloads"),
            Paths.get(userHome, "Desktop"),
            Paths.get(userHome, "esus-downloads"),
            Paths.get("C:\\esus-downloads"),
            Paths.get("/tmp/esus-downloads")
        );

        for (Path pasta : candidatos) {
            File f = buscarMaisRecente(pasta.toFile(), "acompanhamento-cidadaos-vinculados");
            if (f != null) return f;
        }

        return null;
    }

    /**
     * Busca o arquivo CSV mais recente com o prefixo especificado em uma pasta.
     */
    private static File buscarMaisRecente(File pasta, String prefixo) {
        if (!pasta.exists() || !pasta.isDirectory()) return null;
        File[] arquivos = pasta.listFiles((dir, name) ->
            name.toLowerCase().startsWith(prefixo.toLowerCase()) && name.endsWith(".csv")
        );
        if (arquivos == null || arquivos.length == 0) return null;
        Arrays.sort(arquivos, Comparator.comparingLong(File::lastModified).reversed());
        return arquivos[0];
    }

    /**
     * Lê o arquivo CSV e retorna um mapa de cidadãos indexados pela chave de identificação.
     * A chave é "CPF:XXXXXXXXXXX" ou "CNS:XXXXXXXXXXXXXXX" quando não há CPF.
     *
     * @param arquivo arquivo CSV exportado pelo e-SUS
     * @return mapa cidadão indexado por chave
     * @throws IOException se o arquivo não puder ser lido
     */
    public Map<String, Cidadao> lerCidadaos(File arquivo) throws IOException {
        Map<String, Cidadao> mapa = new LinkedHashMap<>();

        if (arquivo == null || !arquivo.exists()) {
            throw new FileNotFoundException("Arquivo CSV não encontrado: " + arquivo);
        }

        LOG.info("Lendo CSV: " + arquivo.getAbsolutePath());

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(arquivo), CHARSET))) {

            // Pular linhas de metadados
            for (int i = 0; i < LINHAS_METADADOS; i++) {
                String linha = reader.readLine();
                if (linha == null) {
                    throw new IOException("Arquivo CSV com estrutura inesperada – menos de " +
                                          LINHAS_METADADOS + " linhas de cabeçalho.");
                }
            }

            // A próxima linha é o cabeçalho – lemos mas não usamos (posição fixa)
            String cabecalho = reader.readLine();
            if (cabecalho == null) throw new IOException("Cabeçalho de dados não encontrado.");
            LOG.fine("Cabeçalho lido: " + cabecalho);

            String linha;
            int linhaNum = LINHAS_METADADOS + 1;
            int lidas = 0;
            int ignoradas = 0;

            while ((linha = reader.readLine()) != null) {
                linhaNum++;
                if (linha.isBlank()) continue;

                try {
                    Cidadao c = parseLinha(linha);
                    if (c == null) { ignoradas++; continue; }

                    String chave = c.getChaveIdentificacao();
                    if (chave == null) {
                        LOG.warning("Cidadão sem CPF nem CNS na linha " + linhaNum + ": " + linha.substring(0, Math.min(80, linha.length())));
                        ignoradas++;
                        continue;
                    }

                    mapa.put(chave, c);
                    lidas++;

                } catch (Exception e) {
                    LOG.warning("Erro ao parsear linha " + linhaNum + ": " + e.getMessage());
                    ignoradas++;
                }
            }

            LOG.info(String.format("CSV lido: %d cidadãos carregados, %d ignorados.", lidas, ignoradas));
        }

        return mapa;
    }

    /**
     * Parseia uma linha CSV (separador ;) e retorna um Cidadao.
     * Trata campos entre aspas (RFC-4180 parcial).
     */
    private Cidadao parseLinha(String linha) {
        String[] cols = splitCsv(linha, ';');
        if (cols.length < COL_NOME + 1) return null;

        String cpfCns = get(cols, COL_CPF_CNS);
        String cpf = null;
        String cns = null;

        if (cpfCns != null) {
            // CPF tem pontos/traços; CNS é sequência numérica sem formatação
            if (cpfCns.contains(".") || cpfCns.contains("-")) {
                cpf = Cidadao.normalizarCpf(cpfCns);
            } else {
                // Pode ser CPF sem formatação (11 dígitos) ou CNS (15 dígitos)
                String numerico = cpfCns.replaceAll("[^0-9]", "");
                if (numerico.length() == 11) {
                    cpf = numerico;
                } else if (numerico.length() == 15) {
                    cns = numerico;
                } else {
                    // Trata como CPF de qualquer forma
                    cpf = numerico;
                }
            }
        }

        return new Cidadao(
            get(cols, COL_NOME),
            cpf,
            cns,
            get(cols, COL_MICROAREA),
            get(cols, COL_IDADE),
            get(cols, COL_SEXO),
            get(cols, COL_NASC),
            get(cols, COL_TELEFONE),
            get(cols, COL_ENDERECO),
            get(cols, COL_EQUIPE),
            get(cols, COL_INE)
        );
    }

    /**
     * Retorna o valor de uma coluna ou null se fora do intervalo / vazio.
     */
    private String get(String[] cols, int idx) {
        if (idx >= cols.length) return null;
        String val = cols[idx].trim();
        return (val.isEmpty() || val.equals("-")) ? null : val;
    }

    /**
     * Split CSV com suporte a campos entre aspas.
     */
    public static String[] splitCsv(String linha, char sep) {
        List<String> campos = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean emAspas = false;

        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (c == '"') {
                emAspas = !emAspas;
            } else if (c == sep && !emAspas) {
                campos.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        campos.add(sb.toString());
        return campos.toArray(new String[0]);
    }
}
