package com.saude.indicadores.service;

import com.saude.indicadores.model.Cidadao;
import com.saude.indicadores.model.Indicador;
import com.saude.indicadores.model.Indicador.TipoIndicador;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Serviço responsável por ler as Listas Nominais SIAPS em formato XLSX.
 *
 * Estrutura dos arquivos SIAPS:
 *   - Linhas 0-15: metadados (Ministério da Saúde, SIAPS, data, equipe, etc.)
 *   - Linha 16 (índice 0-based): cabeçalho real com CPF, CNS, Nascimento...
 *   - Linha 17+: dados dos cidadãos
 *
 * Colunas padrão: CPF | CNS | Nascimento | Sexo | Raça cor | CNES | INE | A..N | NM | DN
 * Exceção: arquivo de Prevenção do Câncer usa NM.A, DN.A, NM.B... em vez de A, B, C...
 */
public class XlsxIndicadorService {

    private static final Logger LOG = Logger.getLogger(XlsxIndicadorService.class.getName());

    // Linha do cabeçalho real (0-based)
    private static final int LINHA_CABECALHO = 16;

    /**
     * Detecta o tipo de indicador com base no nome do arquivo.
     */
    public static TipoIndicador detectarTipo(String nomeArquivo) {
        String nome = nomeArquivo.toLowerCase();
        if (nome.contains("diabetes"))                   return TipoIndicador.DIABETES;
        if (nome.contains("hipertens"))                  return TipoIndicador.HIPERTENSAO;
        if (nome.contains("gesta") || nome.contains("puerp")) return TipoIndicador.GESTACAO;
        if (nome.contains("idosa") || nome.contains("idoso")) return TipoIndicador.IDOSO;
        if (nome.contains("cancer") || nome.contains("câncer") || nome.contains("preven")) return TipoIndicador.CANCER;
        if (nome.contains("infantil") || nome.contains("desenvolvimento")) return TipoIndicador.DESENVOLVIMENTO_INFANTIL;
        return null;
    }

    /**
     * Lê um arquivo XLSX de Lista Nominal SIAPS e retorna um mapa de Indicadores
     * indexados pela chave de identificação do cidadão (CPF ou CNS).
     *
     * @param arquivo  arquivo .xlsx da lista nominal
     * @param tipo     tipo do indicador (null para auto-detecção)
     * @return mapa chave→Indicador
     * @throws IOException se o arquivo não puder ser lido
     */
    public Map<String, Indicador> lerIndicadores(File arquivo, TipoIndicador tipo) throws IOException {
        Map<String, Indicador> mapa = new LinkedHashMap<>();

        if (arquivo == null || !arquivo.exists()) {
            throw new FileNotFoundException("Arquivo XLSX não encontrado: " + arquivo);
        }

        if (tipo == null) {
            tipo = detectarTipo(arquivo.getName());
        }

        LOG.info("Lendo XLSX [" + (tipo != null ? tipo.getDescricao() : "?") + "]: " + arquivo.getName());

        try (FileInputStream fis = new FileInputStream(arquivo);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(0);

            // Obter linha do cabeçalho
            Row headerRow = sheet.getRow(LINHA_CABECALHO);
            if (headerRow == null) {
                throw new IOException("Cabeçalho não encontrado na linha " + LINHA_CABECALHO + " do arquivo: " + arquivo.getName());
            }

            // Mapear colunas pelo nome
            Map<String, Integer> colIndex = mapearColunas(headerRow);

            Integer colCpf  = colIndex.get("CPF");
            Integer colCns  = colIndex.get("CNS");
            Integer colNasc = colIndex.get("NASCIMENTO");
            Integer colSexo = colIndex.get("SEXO");
            Integer colCnes = colIndex.get("CNES");
            Integer colIne  = colIndex.get("INE");
            Integer colNm   = colIndex.get("NM");
            Integer colDn   = colIndex.get("DN");

            // Colunas de ações (letras simples: A, B, C... e campos especiais como NM.A, GESTANTE ATIVA, etc.)
            List<String> colsAcao = extrairColunasAcao(colIndex);

            int lidas = 0, ignoradas = 0;

            for (int rowNum = LINHA_CABECALHO + 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;

                String cpf = getCell(row, colCpf);
                String cns = getCell(row, colCns);

                if ((cpf == null || cpf.isBlank()) && (cns == null || cns.isBlank())) {
                    ignoradas++;
                    continue;
                }

                cpf = Cidadao.normalizarCpf(cpf);
                cns = Cidadao.normalizarCns(cns);

                // Determinar chave de identificação
                String chave;
                if (cpf != null) {
                    chave = "CPF:" + cpf;
                } else {
                    chave = "CNS:" + cns;
                }

                Indicador ind = new Indicador(tipo);
                ind.setNomeArquivo(arquivo.getName());
                ind.setNascimento(getCell(row, colNasc));
                ind.setSexo(getCell(row, colSexo));
                ind.setCnes(getCell(row, colCnes));
                ind.setIne(getCell(row, colIne));
                ind.setNm(getCell(row, colNm));
                ind.setDn(getCell(row, colDn));

                // Ler colunas de ações
                for (String colNome : colsAcao) {
                    Integer colIdx = colIndex.get(colNome);
                    if (colIdx == null) continue;
                    String valor = getCell(row, colIdx);
                    boolean marcada = "X".equalsIgnoreCase(valor) || "1".equals(valor);
                    ind.setAcao(colNome, marcada);
                }

                mapa.put(chave, ind);
                lidas++;
            }

            LOG.info(String.format("XLSX lido: %d registros carregados, %d ignorados.", lidas, ignoradas));
        }

        return mapa;
    }

    /**
     * Mapeia nomes de coluna (maiúsculas) para seus índices na linha de cabeçalho.
     */
    private Map<String, Integer> mapearColunas(Row headerRow) {
        Map<String, Integer> mapa = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String nome = getCellString(cell);
            if (nome != null && !nome.isBlank()) {
                mapa.put(nome.trim().toUpperCase(), cell.getColumnIndex());
            }
        }
        return mapa;
    }

    /**
     * Extrai os nomes das colunas de ação (letras A-Z simples e campos especiais do SIAPS).
     * Exclui as colunas fixas de identificação/metadados.
     */
    private List<String> extrairColunasAcao(Map<String, Integer> colIndex) {
        Set<String> excluir = new HashSet<>(Arrays.asList(
            "CPF", "CNS", "NASCIMENTO", "SEXO", "RAÇA COR", "RAÇA_COR", "RACA COR", "CNES", "INE", "NM", "DN"
        ));
        List<String> acoes = new ArrayList<>();
        for (String col : colIndex.keySet()) {
            if (!excluir.contains(col)) {
                acoes.add(col);
            }
        }
        return acoes;
    }

    /**
     * Retorna o valor de uma célula como String, ou null se índice nulo/célula vazia.
     */
    private String getCell(Row row, Integer colIdx) {
        if (colIdx == null) return null;
        Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return getCellString(cell);
    }

    /**
     * Converte qualquer tipo de célula para String.
     */
    private String getCellString(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:  return cell.getStringCellValue().trim();
            case NUMERIC: {
                // Evita notação científica e decimal para CPF/CNS armazenados como número
                // Excel pode armazenar CPF como 73407941404.0 — convertemos para long
                double d = cell.getNumericCellValue();
                long l = (long) d;
                return String.valueOf(l);
            }
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA: {
                try { return String.valueOf((long) cell.getNumericCellValue()); }
                catch (Exception e) { return cell.getStringCellValue().trim(); }
            }
            default: return null;
        }
    }
}
