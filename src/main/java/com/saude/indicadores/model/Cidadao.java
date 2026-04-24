package com.saude.indicadores.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Modelo principal que representa um cidadão vinculado à equipe de saúde.
 * Agrega dados cadastrais do e-SUS com indicadores de qualidade do SIAPS.
 */
public class Cidadao {

    // === Dados Cadastrais (e-SUS) ===
    private String nome;
    private String cpf;          // pode ser null se o vínculo é apenas por CNS
    private String cns;          // Cartão Nacional de Saúde
    private String microarea;
    private String idade;
    private String sexo;
    private String dataNascimento;
    private String telefone;
    private String endereco;
    private String equipe;
    private String ine;

    /**
     * Mapa de indicadores de saúde.
     * Chave: nome do indicador (ex.: "DIABETES", "HIPERTENSÃO")
     * Valor: objeto Indicador com colunas A-N, NM e DN
     */
    private Map<String, Indicador> indicadores = new HashMap<>();

    // ===== Construtores =====

    public Cidadao() {}

    public Cidadao(String nome, String cpf, String cns, String microarea, String idade,
                   String sexo, String dataNascimento, String telefone,
                   String endereco, String equipe, String ine) {
        this.nome = nome;
        this.cpf = normalizarCpf(cpf);
        this.cns = normalizarCns(cns);
        this.microarea = microarea;
        this.idade = idade;
        this.sexo = sexo;
        this.dataNascimento = dataNascimento;
        this.telefone = telefone;
        this.endereco = endereco;
        this.equipe = equipe;
        this.ine = ine;
    }

    // ===== Métodos utilitários =====

    /**
     * Normaliza CPF removendo pontos e traços.
     */
    public static String normalizarCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) return null;
        String limpo = cpf.replaceAll("[^0-9]", "");
        return limpo.isEmpty() ? null : limpo;
    }

    /**
     * Normaliza CNS removendo espaços.
     */
    public static String normalizarCns(String cns) {
        if (cns == null || cns.isBlank()) return null;
        String limpo = cns.trim();
        return limpo.isEmpty() ? null : limpo;
    }

    /**
     * Retorna a chave de identificação (CPF preferencial, CNS como fallback).
     */
    public String getChaveIdentificacao() {
        if (cpf != null && !cpf.isBlank()) return "CPF:" + cpf;
        if (cns != null && !cns.isBlank()) return "CNS:" + cns;
        return null;
    }

    /**
     * Verifica se o cidadão possui algum indicador pendente (NM vazio ou zero).
     */
    public boolean possuiIndicadorPendente() {
        return indicadores.values().stream().anyMatch(Indicador::isPendente);
    }

    /**
     * Verifica se o cidadão está ativo em determinado indicador.
     */
    public boolean temIndicador(String nomeIndicador) {
        return indicadores.containsKey(nomeIndicador.toUpperCase());
    }

    public void adicionarIndicador(String nome, Indicador indicador) {
        this.indicadores.put(nome.toUpperCase(), indicador);
    }

    // ===== Getters e Setters =====

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = normalizarCpf(cpf); }

    public String getCns() { return cns; }
    public void setCns(String cns) { this.cns = normalizarCns(cns); }

    public String getMicroarea() { return microarea; }
    public void setMicroarea(String microarea) { this.microarea = microarea; }

    public String getIdade() { return idade; }
    public void setIdade(String idade) { this.idade = idade; }

    public String getSexo() { return sexo; }
    public void setSexo(String sexo) { this.sexo = sexo; }

    public String getDataNascimento() { return dataNascimento; }
    public void setDataNascimento(String dataNascimento) { this.dataNascimento = dataNascimento; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }

    public String getEquipe() { return equipe; }
    public void setEquipe(String equipe) { this.equipe = equipe; }

    public String getIne() { return ine; }
    public void setIne(String ine) { this.ine = ine; }

    public Map<String, Indicador> getIndicadores() { return indicadores; }
    public void setIndicadores(Map<String, Indicador> indicadores) { this.indicadores = indicadores; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cidadao)) return false;
        Cidadao c = (Cidadao) o;
        return Objects.equals(cpf, c.cpf) && Objects.equals(cns, c.cns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cpf, cns);
    }

    @Override
    public String toString() {
        return "Cidadao{nome='" + nome + "', cpf='" + cpf + "', microarea='" + microarea +
               "', indicadores=" + indicadores.keySet() + "}";
    }
}
