package com.saude.indicadores.service;

import com.saude.indicadores.model.Cidadao;
import com.saude.indicadores.model.Indicador;
import com.saude.indicadores.model.Indicador.TipoIndicador;

import org.junit.jupiter.api.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes unitários para o serviço de unificação de dados.
 */
class UnificacaoServiceTest {

    private UnificacaoService service;

    @BeforeEach
    void setUp() {
        service = new UnificacaoService();
    }

    @Test
    @DisplayName("Deve normalizar CPF com formatação")
    void testNormalizarCpf() {
        assertEquals("08748321443", Cidadao.normalizarCpf("087.483.214-43"));
        assertEquals("08748321443", Cidadao.normalizarCpf("08748321443"));
        assertNull(Cidadao.normalizarCpf(null));
        assertNull(Cidadao.normalizarCpf("   "));
    }

    @Test
    @DisplayName("Deve retornar chave CPF quando CPF disponível")
    void testChaveIdentificacaoCpf() {
        Cidadao c = new Cidadao();
        c.setCpf("08748321443");
        c.setCns("123456789012345");
        assertEquals("CPF:08748321443", c.getChaveIdentificacao());
    }

    @Test
    @DisplayName("Deve usar CNS como fallback quando sem CPF")
    void testChaveIdentificacaoCns() {
        Cidadao c = new Cidadao();
        c.setCpf(null);
        c.setCns("898000474593519");
        assertEquals("CNS:898000474593519", c.getChaveIdentificacao());
    }

    @Test
    @DisplayName("Indicador pendente: DN preenchido e NM vazio")
    void testIndicadorPendente() {
        Indicador ind = new Indicador(TipoIndicador.DIABETES);
        ind.setDn("X");
        ind.setNm(null);
        assertTrue(ind.isPendente());

        ind.setNm("X");
        assertFalse(ind.isPendente());
    }

    @Test
    @DisplayName("Indicador não pendente: DN vazio significa não está no denominador")
    void testIndicadorNaoPendenteQuandoDnVazio() {
        Indicador ind = new Indicador(TipoIndicador.HIPERTENSAO);
        ind.setDn(null);
        ind.setNm(null);
        assertFalse(ind.isPendente(), "Sem DN não é considerado pendente");
    }

    @Test
    @DisplayName("Deve marcar cidadão como pendente se algum indicador tiver NM vazio")
    void testCidadaoPendente() {
        Cidadao c = new Cidadao();

        Indicador diabetes = new Indicador(TipoIndicador.DIABETES);
        diabetes.setNm("X");
        diabetes.setDn("X");
        c.adicionarIndicador("DIABETES", diabetes);

        assertFalse(c.possuiIndicadorPendente());

        Indicador hipertensao = new Indicador(TipoIndicador.HIPERTENSAO);
        hipertensao.setNm(null);  // pendente!
        hipertensao.setDn("X");
        c.adicionarIndicador("HIPERTENSAO", hipertensao);

        assertTrue(c.possuiIndicadorPendente());
    }

    @Test
    @DisplayName("Filtragem por microárea deve retornar somente cidadãos da área")
    void testFiltrarPorMicroarea() throws Exception {
        // Montar base manual
        Map<String, Cidadao> base = new LinkedHashMap<>();

        Cidadao c1 = new Cidadao(); c1.setCpf("11111111111"); c1.setMicroarea("01");
        Cidadao c2 = new Cidadao(); c2.setCpf("22222222222"); c2.setMicroarea("02");
        Cidadao c3 = new Cidadao(); c3.setCpf("33333333333"); c3.setMicroarea("01");

        base.put("CPF:11111111111", c1);
        base.put("CPF:22222222222", c2);
        base.put("CPF:33333333333", c3);

        service.setBaseCadastral(base);

        List<Cidadao> resultado = service.filtrarPorMicroarea("01");
        assertEquals(2, resultado.size());

        resultado = service.filtrarPorMicroarea("TODAS");
        assertEquals(3, resultado.size());
    }

    @Test
    @DisplayName("Split CSV deve tratar campos entre aspas corretamente")
    void testSplitCsv() {
        String linha = "USF;\"0000166928\";\"Não informada\";\"Rua A, Nº 10\";\"087.483.214-43\";FULANO";
        String[] campos = CsvAcompanhamentoService.splitCsv(linha, ';');
        assertEquals(6, campos.length);
        assertEquals("Rua A, Nº 10", campos[3]);
        assertEquals("087.483.214-43", campos[4]);
    }

    @Test
    @DisplayName("Detecção de tipo de indicador pelo nome do arquivo")
    void testDetectarTipo() {
        assertEquals(TipoIndicador.DIABETES,
            XlsxIndicadorService.detectarTipo("Lista_Nominal_Cuidado_da_pessoa_com_Diabetes.xlsx"));
        assertEquals(TipoIndicador.HIPERTENSAO,
            XlsxIndicadorService.detectarTipo("Lista_Nominal_Cuidado_da_pessoa_com_Hipertensão.xlsx"));
        assertEquals(TipoIndicador.GESTACAO,
            XlsxIndicadorService.detectarTipo("Lista_Nominal_Cuidado_na_Gestação_e_Puerpério.xlsx"));
        assertEquals(TipoIndicador.IDOSO,
            XlsxIndicadorService.detectarTipo("Lista_Nominal_Cuidado_integral_da_Pessoa_Idosa.xlsx"));
        assertEquals(TipoIndicador.CANCER,
            XlsxIndicadorService.detectarTipo("Lista_Nominal_Cuidado_da_mulher_e_do_homem_transgênero_na_prevenção_do_câncer.xlsx"));
        assertEquals(TipoIndicador.DESENVOLVIMENTO_INFANTIL,
            XlsxIndicadorService.detectarTipo("Lista_Nominal_Desenvolvimento_Infantil.xlsx"));
    }
}
