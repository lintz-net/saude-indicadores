package com.saude.indicadores.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Representa os dados de um indicador de saúde SIAPS para um cidadão.
 * Armazena as colunas de ações (A, B, C...) marcadas com "X",
 * além dos campos NM (Numerador) e DN (Denominador).
 */
public class Indicador {

    public enum TipoIndicador {
        DIABETES("Diabetes", "🩸"),
        HIPERTENSAO("Hipertensão", "❤️"),
        GESTACAO("Gestação e Puerpério", "🤱"),
        IDOSO("Pessoa Idosa", "👴"),
        CANCER("Prevenção do Câncer", "🎗️"),
        DESENVOLVIMENTO_INFANTIL("Desenvolvimento Infantil", "👶");

        private final String descricao;
        private final String emoji;

        TipoIndicador(String descricao, String emoji) {
            this.descricao = descricao;
            this.emoji = emoji;
        }

        public String getDescricao() { return descricao; }
        public String getEmoji() { return emoji; }
    }

    private TipoIndicador tipo;
    private String nomeArquivo;       // arquivo de origem

    // Colunas de ações (A, B, C, D, E, F...) – true = marcada com "X"
    private Map<String, Boolean> acoes = new LinkedHashMap<>();

    private String nm;   // Numerador (ex.: "X" ou valor numérico)
    private String dn;   // Denominador

    private String nascimento;
    private String sexo;
    private String cnes;
    private String ine;

    // ===== Construtores =====

    public Indicador() {}

    public Indicador(TipoIndicador tipo) {
        this.tipo = tipo;
    }

    // ===== Métodos utilitários =====

    /**
     * Um indicador é pendente quando NM está vazio, nulo ou igual a zero,
     * mas o cidadão está no denominador (DN preenchido).
     */
    public boolean isPendente() {
        boolean dnPreenchido = dn != null && !dn.isBlank() && !dn.equals("0");
        boolean nmVazio = nm == null || nm.isBlank() || nm.equals("0");
        return dnPreenchido && nmVazio;
    }

    /**
     * Conta quantas ações estão marcadas.
     */
    public long countAcoesMarcadas() {
        return acoes.values().stream().filter(Boolean::booleanValue).count();
    }

    public void setAcao(String letra, boolean marcada) {
        acoes.put(letra.toUpperCase(), marcada);
    }

    public boolean getAcao(String letra) {
        return acoes.getOrDefault(letra.toUpperCase(), false);
    }

    // ===== Getters e Setters =====

    public TipoIndicador getTipo() { return tipo; }
    public void setTipo(TipoIndicador tipo) { this.tipo = tipo; }

    public String getNomeArquivo() { return nomeArquivo; }
    public void setNomeArquivo(String nomeArquivo) { this.nomeArquivo = nomeArquivo; }

    public Map<String, Boolean> getAcoes() { return acoes; }
    public void setAcoes(Map<String, Boolean> acoes) { this.acoes = acoes; }

    public String getNm() { return nm; }
    public void setNm(String nm) { this.nm = nm; }

    public String getDn() { return dn; }
    public void setDn(String dn) { this.dn = dn; }

    public String getNascimento() { return nascimento; }
    public void setNascimento(String nascimento) { this.nascimento = nascimento; }

    public String getSexo() { return sexo; }
    public void setSexo(String sexo) { this.sexo = sexo; }

    public String getCnes() { return cnes; }
    public void setCnes(String cnes) { this.cnes = cnes; }

    public String getIne() { return ine; }
    public void setIne(String ine) { this.ine = ine; }

    @Override
    public String toString() {
        return "Indicador{tipo=" + (tipo != null ? tipo.getDescricao() : "?") +
               ", nm='" + nm + "', dn='" + dn + "', pendente=" + isPendente() + "}";
    }
}
