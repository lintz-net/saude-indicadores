# Saúde · Indicadores — Processamento de Listas Nominais

Aplicação Java/Swing para unificar a base cadastral do **e-SUS Atenção Primária** com as **Listas Nominais de Qualidade do SIAPS**, permitindo identificar rapidamente cidadãos com indicadores de saúde pendentes.

---

## Pré-requisitos

| Ferramenta | Versão mínima |
|------------|---------------|
| Java JDK   | 11            |
| Maven      | 3.8+          |

---

## Compilar e executar

```bash
# 1. Clone ou descompacte o projeto
cd saude-indicadores

# 2. Compilar e gerar JAR executável (fat jar com todas as dependências)
mvn clean package -DskipTests

# 3. Executar
java -jar target/indicadores-1.0.0-executavel.jar
```

---

## Estrutura do projeto

```
saude-indicadores/
├── pom.xml
└── src/
    ├── main/java/com/saude/indicadores/
    │   ├── Application.java                  ← Ponto de entrada
    │   ├── model/
    │   │   ├── Cidadao.java                  ← POJO principal com Map<String, Indicador>
    │   │   └── Indicador.java                ← Dados de um indicador SIAPS
    │   ├── service/
    │   │   ├── CsvAcompanhamentoService.java  ← Lê o CSV do e-SUS
    │   │   ├── XlsxIndicadorService.java     ← Lê as planilhas SIAPS
    │   │   ├── UnificacaoService.java        ← Faz o merge (join por CPF/CNS)
    │   │   └── ExportacaoService.java        ← Exporta CSV consolidado
    │   └── ui/
    │       ├── MainFrame.java                ← Tela principal
    │       └── DetalheDialog.java            ← Diálogo de detalhe do cidadão
    └── test/java/
        └── UnificacaoServiceTest.java        ← Testes unitários (JUnit 5)
```

---

## Arquivos de entrada

### 1. CSV Principal — e-SUS
Arquivo: `acompanhamento-cidadaos-vinculados_YYYY-MM-DD-HH-mm.csv`

- **Encoding:** ISO-8859-1 (latin1)
- **Separador:** ponto-e-vírgula (`;`)
- **Estrutura:** 17 linhas de metadados → 1 linha de cabeçalho → dados
- **Colunas usadas:** `CPF/CNS`, `Nome`, `Microárea`, `Idade`, `Sexo`, `Data de nascimento`, `Telefone celular`

### 2. Listas Nominais SIAPS — XLSX
Arquivo: `Lista_Nominal_Cuidado_da_pessoa_com_*.xlsx`

- **Estrutura:** 16 linhas de metadados → 1 linha de cabeçalho → dados
- **Colunas:** `CPF`, `CNS`, `Nascimento`, `Sexo`, `CNES`, `INE`, `A`…`N`, `NM`, `DN`
- **Indicadores suportados:**
  - Diabetes
  - Hipertensão
  - Gestação e Puerpério
  - Pessoa Idosa
  - Prevenção do Câncer (transgênero)
  - Desenvolvimento Infantil

---

## Fluxo de uso

### Download automático via Selenium
A aplicação busca automaticamente o CSV mais recente nos diretórios:
- `~/Downloads`
- `~/Desktop`
- `~/esus-downloads`
- `C:\esus-downloads`

Para configurar um diretório customizado, defina a variável de ambiente:
```bash
export ESUS_DOWNLOAD_DIR=/caminho/do/diretorio
```

### Carregamento manual
1. Clique em **📂 Carregar manualmente** → selecione o CSV
2. Clique em **✚ Adicionar** → selecione um ou mais XLSX
3. Clique em **▶ Processar Dados**
4. Use os filtros (Microárea, Indicador, Somente Pendentes)
5. Dê **duplo clique** em um cidadão para ver os detalhes
6. Clique em **⬇ Exportar CSV Final**

---

## Lógica de merge (join)

1. Chave primária: **CPF normalizado** (sem pontos/traços)
2. Fallback: **CNS** (quando o CPF não está disponível)
3. Cidadão no CSV mas não no SIAPS → exibido sem indicadores
4. Cidadão no SIAPS mas não no CSV → entrada mínima criada automaticamente com aviso `(Sem cadastro no e-SUS)`

---

## Indicador pendente

Um indicador é considerado **PENDENTE** quando:
- `DN` está preenchido (cidadão está no denominador) **E**
- `NM` está vazio, nulo ou igual a zero

Isso identifica cidadãos que deveriam ter recebido uma ação de saúde mas ainda não foram atendidos.

---

## CSV exportado

O arquivo de saída contém:

| Coluna | Descrição |
|--------|-----------|
| Nome, CPF, CNS | Identificação |
| Microárea, Idade, Sexo, Nasc., Telefone | Dados cadastrais |
| `[INDICADOR] (NM)` | Numerador do indicador |
| `[INDICADOR] (DN)` | Denominador do indicador |
| `[INDICADOR] Status` | OK ou PENDENTE |
| Total Indicadores | Quantidade de indicadores ativos |
| Pendentes | Quantidade de indicadores pendentes |

---

## Executar testes

```bash
mvn test

