package com.saude.indicadores.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;

import com.saude.indicadores.model.Cidadao;
import com.saude.indicadores.model.Indicador;
import com.saude.indicadores.model.Indicador.TipoIndicador;
import com.saude.indicadores.selenium.SeleniumDownloadService;
import com.saude.indicadores.service.ExportacaoService;
import com.saude.indicadores.service.UnificacaoService;

/**
 * Tela principal da aplicação Saúde · Indicadores.
 * Layout: painel lateral (esquerda) + tabela de resultados (direita).
 *
 * Melhorias v2:
 *  - Filtro por CPF/CNS com debounce de 300ms
 *  - Header da tabela com renderer customizado (legível em qualquer L&F)
 *  - Badges de indicadores com cores suaves (fundo colorido, não sólido)
 *  - Célula "ausente" exibida com "—" em cinza
 *  - Botão "Limpar filtros"
 */
public class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;
	// ─── Paleta ───────────────────────────────────────────────────────────────
    private static final Color AZUL_PRIMARIO = new Color(0x1565C0);
    private static final Color AZUL_CLARO    = new Color(0x1976D2);
    private static final Color AZUL_HEADER   = new Color(0x0D47A1);
    private static final Color VERDE_OK      = new Color(0x2E7D32);
    private static final Color CINZA_FUNDO   = new Color(0xF5F5F5);
    private static final Color CINZA_PAINEL  = new Color(0xECEFF1);
    private static final Color BRANCO        = Color.WHITE;
    private static final Color TEXTO_ESCURO  = new Color(0x212121);
    private static final Color TEXTO_MEDIO   = new Color(0x546E7A);

    // ─── Serviços ─────────────────────────────────────────────────────────────
    private final UnificacaoService unificacaoService = new UnificacaoService();
    private final ExportacaoService exportacaoService  = new ExportacaoService();

    // ─── Estado ───────────────────────────────────────────────────────────────
    private File         csvCarregado   = null;
    private final List<File> xlsxCarregados = new ArrayList<>();
    private List<Cidadao>    cidadaosExibidos = new ArrayList<>();

    // ─── Componentes ─────────────────────────────────────────────────────────
    private JLabel            lblStatusCsv;
    private JList<String>     listaXlsx;
    private DefaultListModel<String> modeloListaXlsx;
    private JComboBox<String> cmbMicroarea;
    private JComboBox<String> cmbIndicador;
    private JCheckBox         chkPendentes;
    private JTextField        txtCpf;          // ← filtro CPF/CNS
    private JTextField        txtUsuarioEsus;  // ← CPF de acesso ao e-SUS
    private JPasswordField    txtSenhaEsus;    // ← senha de acesso ao e-SUS
    private JTable            tabelaCidadaos;
    private CidadaoTableModel modeloTabela;
    private JLabel            lblContagem;
    private JTabbedPane       tabbedPane;                       // ← painel de abas
    // Mapa: chave do indicador → modelo da aba individual
    private final Map<String, IndicadorTableModel> modelosAbas = new LinkedHashMap<>();
    private JButton           btnProcessar;
    private JButton           btnExportar;
    private JProgressBar      progressBar;
    private JLabel            lblProgresso;
    private Timer             debounceTimer;

    // ─────────────────────────────────────────────────────────────────────────

    public MainFrame() {
        super("Saúde · Indicadores — Processamento de Listas Nominais");
        inicializar();
    }

    private void inicializar() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1360, 760);
        setMinimumSize(new Dimension(960, 600));
        setLocationRelativeTo(null);

        try { setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/icon.png"))); }
        catch (Exception ignored) {}

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(CINZA_FUNDO);
        root.add(criarHeader(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            criarPainelLateral(), criarPainelCentral());
        split.setDividerLocation(338);
        split.setDividerSize(4);
        split.setBorder(null);
        root.add(split, BorderLayout.CENTER);
        root.add(criarStatusBar(), BorderLayout.SOUTH);
        setContentPane(root);
    }

    // =========================================================================
    // HEADER
    // =========================================================================

    private JPanel criarHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(AZUL_PRIMARIO);
        p.setBorder(new EmptyBorder(10, 16, 10, 16));

        JLabel titulo = new JLabel("Saúde · Indicadores");
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titulo.setForeground(BRANCO);

        JLabel sub = new JLabel("Processamento de Listas Nominais — e-SUS / SIAPS");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(new Color(0xBBDEFB));

        JPanel txt = new JPanel(new GridLayout(2, 1, 0, 1));
        txt.setOpaque(false);
        txt.add(titulo);
        txt.add(sub);
        p.add(txt, BorderLayout.WEST);
        return p;
    }

    // =========================================================================
    // PAINEL LATERAL
    // =========================================================================

    private JPanel criarPainelLateral() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(CINZA_PAINEL);
        p.setBorder(new EmptyBorder(8, 8, 8, 8));
        p.add(criarSecaoCsv());       p.add(Box.createVerticalStrut(8));
        p.add(criarSecaoXlsx());      p.add(Box.createVerticalStrut(8));
        p.add(criarSecaoFiltros());   p.add(Box.createVerticalStrut(8));
        p.add(criarSecaoAcoes());     p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel criarSecaoCsv() {
        JPanel s = criarSecao("📋 CSV Principal");

        // Status do arquivo carregado
        lblStatusCsv = new JLabel("Nenhum arquivo carregado");
        lblStatusCsv.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblStatusCsv.setForeground(new Color(0x757575));
        lblStatusCsv.setBorder(new EmptyBorder(0, 0, 6, 0));
        lblStatusCsv.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.add(lblStatusCsv);

        // ── Credenciais e-SUS ─────────────────────────────────────────────
        s.add(criarLabel("Usuário (CPF):"));
        txtUsuarioEsus = new JTextField();
        txtUsuarioEsus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        txtUsuarioEsus.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        txtUsuarioEsus.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtUsuarioEsus.setToolTipText("CPF de acesso ao e-SUS (com ou sem pontuação)");
        txtUsuarioEsus.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(0xB0BEC5), 1),
            new EmptyBorder(2, 6, 2, 6)
        ));
        s.add(txtUsuarioEsus);
        s.add(Box.createVerticalStrut(5));

        s.add(criarLabel("Senha:"));
        txtSenhaEsus = new JPasswordField();
        txtSenhaEsus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        txtSenhaEsus.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        txtSenhaEsus.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtSenhaEsus.setToolTipText("Senha de acesso ao e-SUS");
        txtSenhaEsus.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(0xB0BEC5), 1),
            new EmptyBorder(2, 6, 2, 6)
        ));
        s.add(txtSenhaEsus);
        s.add(Box.createVerticalStrut(6));
        // ─────────────────────────────────────────────────────────────────

        JButton btnSel = criarBotao("⬇ Baixar via Selenium", AZUL_CLARO, BRANCO);
        btnSel.setToolTipText("Abre o Chrome, faz login e baixa o CSV automaticamente");
        btnSel.addActionListener(e -> baixarViaSelenium());
        s.add(btnSel);
        s.add(Box.createVerticalStrut(4));

        JButton btnMan = criarBotaoSecundario("📂 Carregar manualmente");
        btnMan.addActionListener(e -> carregarCsvManual());
        s.add(btnMan);
        return s;
    }

    private JPanel criarSecaoXlsx() {
        JPanel s = criarSecao("📊 Indicadores (Excel)");
        modeloListaXlsx = new DefaultListModel<>();
        listaXlsx = new JList<>(modeloListaXlsx);
        listaXlsx.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        listaXlsx.setVisibleRowCount(4);
        JScrollPane sc = new JScrollPane(listaXlsx);
        sc.setPreferredSize(new Dimension(0, 80));
        sc.setBorder(new LineBorder(new Color(0xB0BEC5), 1));
        sc.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.add(sc);  s.add(Box.createVerticalStrut(4));
        JPanel bt = new JPanel(new GridLayout(1, 2, 4, 0));
        bt.setOpaque(false);
        JButton add = criarBotao("✚ Adicionar", new Color(0x388E3C), BRANCO);
        add.addActionListener(e -> adicionarXlsx());
        JButton rem = criarBotao("✕ Remover", new Color(0xD32F2F), BRANCO);
        rem.addActionListener(e -> removerXlsx());
        bt.add(add); bt.add(rem);
        bt.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.add(bt);
        return s;
    }

    private JPanel criarSecaoFiltros() {
        JPanel s = criarSecao("🔍 Filtros");

        s.add(criarLabel("Microárea:"));
        cmbMicroarea = new JComboBox<>(new String[]{"TODAS"});
        estilizarCombo(cmbMicroarea);
        cmbMicroarea.addActionListener(e -> aplicarFiltros());
        s.add(cmbMicroarea);
        s.add(Box.createVerticalStrut(6));

        s.add(criarLabel("Indicador:"));
        cmbIndicador = new JComboBox<>(new String[]{"TODOS"});
        estilizarCombo(cmbIndicador);
        cmbIndicador.addActionListener(e -> aplicarFiltros());
        s.add(cmbIndicador);
        s.add(Box.createVerticalStrut(6));

        // ── Filtro CPF/CNS (NOVO) ─────────────────────────────────────────
        s.add(criarLabel("CPF / CNS:"));
        txtCpf = new JTextField();
        txtCpf.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        txtCpf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        txtCpf.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtCpf.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(0xB0BEC5), 1),
            new EmptyBorder(2, 6, 2, 6)
        ));
        txtCpf.setToolTipText("Digite CPF (com ou sem pontuação) ou parte do CNS");
        // Debounce: filtra 300ms após última tecla
        txtCpf.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { agendarFiltro(); }
            @Override public void removeUpdate(DocumentEvent e)  { agendarFiltro(); }
            @Override public void changedUpdate(DocumentEvent e) { agendarFiltro(); }
        });
        s.add(txtCpf);
        s.add(Box.createVerticalStrut(8));
        // ─────────────────────────────────────────────────────────────────

        chkPendentes = new JCheckBox("Somente pendentes");
        chkPendentes.setOpaque(false);
        chkPendentes.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chkPendentes.addActionListener(e -> aplicarFiltros());
        chkPendentes.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.add(chkPendentes);
        s.add(Box.createVerticalStrut(6));

        JButton btnLimpar = criarBotaoSecundario("✕ Limpar filtros");
        btnLimpar.addActionListener(e -> limparFiltros());
        s.add(btnLimpar);
        return s;
    }

    /** Agenda aplicarFiltros() com debounce de 300ms. */
    private void agendarFiltro() {
        if (debounceTimer != null && debounceTimer.isRunning()) debounceTimer.stop();
        debounceTimer = new Timer(300, e -> aplicarFiltros());
        debounceTimer.setRepeats(false);
        debounceTimer.start();
    }

    private JPanel criarSecaoAcoes() {
        JPanel s = criarSecao("⚡ Ações");
        btnProcessar = criarBotao("▶ Processar Dados", new Color(0x37474F), BRANCO);
        btnProcessar.setEnabled(false);
        btnProcessar.addActionListener(e -> processarDados());
        s.add(btnProcessar);  s.add(Box.createVerticalStrut(4));

        btnExportar = criarBotao("⬇ Exportar CSV Final", new Color(0x00695C), BRANCO);
        btnExportar.setEnabled(false);
        btnExportar.addActionListener(e -> exportarCsv());
        s.add(btnExportar);

        s.add(Box.createVerticalStrut(8));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.add(progressBar);
        lblProgresso = new JLabel(" ");
        lblProgresso.setFont(new Font("Segoe UI", Font.ITALIC, 10));
        lblProgresso.setForeground(TEXTO_MEDIO);
        lblProgresso.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.add(lblProgresso);
        return s;
    }

    // =========================================================================
    // PAINEL CENTRAL
    // =========================================================================

    private JPanel criarPainelCentral() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BRANCO);

        // ── TabbedPane principal ──────────────────────────────────────────
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabbedPane.setBackground(BRANCO);

        // Aba 0 — Visão Geral (sempre presente)
        tabbedPane.addTab("📋 Visão Geral", criarPainelVisaoGeral());

        p.add(tabbedPane, BorderLayout.CENTER);
        return p;
    }

    /** Cria o painel da aba Visão Geral (tabela unificada existente). */
    private JPanel criarPainelVisaoGeral() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BRANCO);

        JPanel barra = new JPanel(new BorderLayout());
        barra.setBackground(AZUL_HEADER);
        barra.setBorder(new EmptyBorder(7, 14, 7, 14));
        JLabel lblRes = new JLabel("Visão Geral — todos os cidadãos");
        lblRes.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblRes.setForeground(BRANCO);
        lblContagem = new JLabel("0 cidadãos");
        lblContagem.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblContagem.setForeground(new Color(0xBBDEFB));
        barra.add(lblRes, BorderLayout.WEST);
        barra.add(lblContagem, BorderLayout.EAST);
        p.add(barra, BorderLayout.NORTH);

        modeloTabela = new CidadaoTableModel();
        tabelaCidadaos = new JTable(modeloTabela);
        configurarTabela();

        JScrollPane scroll = new JScrollPane(tabelaCidadaos);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BRANCO);
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    /**
     * Cria (ou recria) as abas individuais de cada indicador carregado.
     * Chamado após o processamento dos dados.
     */
    private void criarAbasIndicadores() {
        // Remove abas antigas de indicadores (mantém apenas a aba 0 = Visão Geral)
        while (tabbedPane.getTabCount() > 1) {
            tabbedPane.removeTabAt(1);
        }
        modelosAbas.clear();

        for (String chave : unificacaoService.getIndicadoresPresentes()) {
            // Nome amigável para a aba
            String nomeAba = chave;
            try {
                TipoIndicador tipo = TipoIndicador.valueOf(chave);
                nomeAba = tipo.getEmoji() + " " + tipo.getDescricao();
            } catch (Exception ignored) {}

            IndicadorTableModel modelo = new IndicadorTableModel(chave);
            modelosAbas.put(chave, modelo);

            JPanel painelAba = criarPainelAbaIndicador(chave, modelo);
            tabbedPane.addTab(nomeAba, painelAba);
        }
    }

    /** Cria o painel de uma aba individual de indicador. */
    private JPanel criarPainelAbaIndicador(String chaveIndicador, IndicadorTableModel modelo) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BRANCO);

        // Barra de título
        JPanel barra = new JPanel(new BorderLayout());
        barra.setBackground(new Color(0x37474F));
        barra.setBorder(new EmptyBorder(7, 14, 7, 14));

        String nomeAmigavel = chaveIndicador;
        try {
            TipoIndicador tipo = TipoIndicador.valueOf(chaveIndicador);
            nomeAmigavel = tipo.getEmoji() + "  " + tipo.getDescricao();
        } catch (Exception ignored) {}

        JLabel lblTitulo = new JLabel(nomeAmigavel);
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblTitulo.setForeground(BRANCO);

        JLabel lblQtd = new JLabel("0 registros");
        lblQtd.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblQtd.setForeground(new Color(0xB0BEC5));
        modelo.setLabelContagem(lblQtd);

        barra.add(lblTitulo, BorderLayout.WEST);
        barra.add(lblQtd,    BorderLayout.EAST);
        p.add(barra, BorderLayout.NORTH);

        JTable tabela = new JTable(modelo);
        configurarTabelaIndicador(tabela);
        modelo.setTabela(tabela);

        JScrollPane scroll = new JScrollPane(tabela);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BRANCO);
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    /** Aplica estilos à tabela de uma aba individual. */
    private void configurarTabelaIndicador(JTable tabela) {
        tabela.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tabela.setRowHeight(28);
        tabela.setShowGrid(false);
        tabela.setShowHorizontalLines(true);
        tabela.setGridColor(new Color(0xE8ECEF));
        tabela.setIntercellSpacing(new Dimension(0, 0));
        tabela.setSelectionBackground(new Color(0xBBDEFB));
        tabela.setSelectionForeground(TEXTO_ESCURO);
        tabela.setAutoCreateRowSorter(true);
        tabela.setFillsViewportHeight(true);
        tabela.setDefaultRenderer(Object.class, new CidadaoTableRenderer());
        tabela.setDefaultRenderer(String.class, new CidadaoTableRenderer());

        JTableHeader header = tabela.getTableHeader();
        header.setDefaultRenderer(new HeaderRenderer());
        header.setPreferredSize(new Dimension(0, 38));
        header.setReorderingAllowed(false);
    }

    private void configurarTabela() {
        tabelaCidadaos.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tabelaCidadaos.setRowHeight(30);
        tabelaCidadaos.setShowGrid(false);
        tabelaCidadaos.setShowHorizontalLines(true);
        tabelaCidadaos.setGridColor(new Color(0xE8ECEF));
        tabelaCidadaos.setIntercellSpacing(new Dimension(0, 0));
        tabelaCidadaos.setSelectionBackground(new Color(0xBBDEFB));
        tabelaCidadaos.setSelectionForeground(TEXTO_ESCURO);
        tabelaCidadaos.setAutoCreateRowSorter(true);
        tabelaCidadaos.setFillsViewportHeight(true);

        // Renderer unificado para todos os tipos de célula
        tabelaCidadaos.setDefaultRenderer(Object.class,   new CidadaoTableRenderer());
        tabelaCidadaos.setDefaultRenderer(String.class,   new CidadaoTableRenderer());
        tabelaCidadaos.setDefaultRenderer(Indicador.class, new CidadaoTableRenderer());

        // Header customizado — independente do L&F
        JTableHeader header = tabelaCidadaos.getTableHeader();
        header.setDefaultRenderer(new HeaderRenderer());
        header.setPreferredSize(new Dimension(0, 42));
        header.setReorderingAllowed(false);

        ajustarLarguras();

        // Remove listeners anteriores para evitar empilhamento.
        // configurarTabela() é chamado múltiplas vezes; sem remoção cada chamada
        // adicionaria um novo listener, abrindo N diálogos modais empilhados.
        for (MouseListener ml : tabelaCidadaos.getMouseListeners()) {
            if (ml instanceof TabelaDoubleClickListener) {
                tabelaCidadaos.removeMouseListener(ml);
            }
        }
        tabelaCidadaos.addMouseListener(new TabelaDoubleClickListener());
    }

    private void ajustarLarguras() {
        TableColumnModel cm = tabelaCidadaos.getColumnModel();
        int total = cm.getColumnCount();
        int[] fixas = {220, 130, 90, 110, 100};
        for (int i = 0; i < Math.min(fixas.length, total); i++) {
            cm.getColumn(i).setPreferredWidth(fixas[i]);
            cm.getColumn(i).setMinWidth(60);
        }
        for (int i = fixas.length; i < total; i++) {
            cm.getColumn(i).setPreferredWidth(115);
            cm.getColumn(i).setMinWidth(80);
        }
    }

    // =========================================================================
    // STATUS BAR
    // =========================================================================

    private JPanel criarStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        bar.setBackground(new Color(0xE0E0E0));
        bar.setBorder(new MatteBorder(1, 0, 0, 0, new Color(0xBDBDBD)));
        JLabel lbl = new JLabel("Pronto. Dica: duplo clique em um cidadão para ver detalhes completos.");
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lbl.setForeground(new Color(0x424242));
        bar.add(lbl);
        return bar;
    }

    // =========================================================================
    // AÇÕES
    // =========================================================================

    private void baixarViaSelenium() {
        String usuario = txtUsuarioEsus.getText().trim();
        String senha   = new String(txtSenhaEsus.getPassword()).trim();

        if (usuario.isEmpty() || senha.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Preencha o Usuário (CPF) e a Senha antes de continuar.",
                "Credenciais obrigatórias", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Desabilitar botões durante a execução
        btnProcessar.setEnabled(false);
        btnExportar.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true); // Selenium não tem % preciso

        SeleniumDownloadService seleniumService = new SeleniumDownloadService();

        new SwingWorker<File, String>() {

            @Override
            protected File doInBackground() throws Exception {
                return seleniumService.executar(usuario, senha,
                    msg -> publish(msg)   // repassa mensagens para process()
                );
            }

            @Override
            protected void process(java.util.List<String> msgs) {
                // Chamado na EDT com as mensagens publicadas
                if (!msgs.isEmpty()) {
                    lblProgresso.setText(msgs.get(msgs.size() - 1));
                }
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                progressBar.setVisible(false);
                atualizarBotoesAcao();
                try {
                    File arquivo = get();
                    definirCsv(arquivo);
                    JOptionPane.showMessageDialog(MainFrame.this,
                        "Download concluído!\nArquivo: " + arquivo.getName() +
                        "\n\nCarregue os indicadores e clique em Processar Dados.",
                        "Selenium — Sucesso", JOptionPane.INFORMATION_MESSAGE);
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable causa = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(MainFrame.this,
                        "Erro durante o download:\n" + causa.getMessage(),
                        "Selenium — Erro", JOptionPane.ERROR_MESSAGE);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }


    private void carregarCsvManual() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Selecionar CSV de Acompanhamento");
        fc.setFileFilter(new FileNameExtensionFilter("Arquivos CSV (*.csv)", "csv"));
        fc.setCurrentDirectory(new File(System.getProperty("user.home")));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            definirCsv(fc.getSelectedFile());
    }

    private void definirCsv(File arquivo) {
        csvCarregado = arquivo;
        lblStatusCsv.setText("<html><b>" + arquivo.getName() + "</b></html>");
        lblStatusCsv.setForeground(VERDE_OK);
        lblStatusCsv.setToolTipText(arquivo.getAbsolutePath());
        atualizarBotoesAcao();
    }

    private void adicionarXlsx() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Selecionar Listas Nominais SIAPS");
        fc.setFileFilter(new FileNameExtensionFilter("Planilhas Excel (*.xlsx)", "xlsx"));
        fc.setMultiSelectionEnabled(true);
        fc.setCurrentDirectory(new File(System.getProperty("user.home")));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            for (File f : fc.getSelectedFiles()) {
                if (!xlsxCarregados.contains(f)) {
                    xlsxCarregados.add(f);
                    modeloListaXlsx.addElement(f.getName());
                }
            }
            atualizarBotoesAcao();
        }
    }

    private void removerXlsx() {
        int idx = listaXlsx.getSelectedIndex();
        if (idx >= 0) { xlsxCarregados.remove(idx); modeloListaXlsx.remove(idx); atualizarBotoesAcao(); }
    }

    private void atualizarBotoesAcao() {
        btnProcessar.setEnabled(csvCarregado != null && !xlsxCarregados.isEmpty());
    }

    private void processarDados() {
        btnProcessar.setEnabled(false);
        btnExportar.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setValue(0);

        unificacaoService.addProgressListener((msg, pct) -> SwingUtilities.invokeLater(() -> {
            lblProgresso.setText(msg);
            if (pct >= 0) progressBar.setValue(pct);
        }));

        File csvFinal = csvCarregado;
        List<File> xlsxCopia = new ArrayList<>(xlsxCarregados);

        new SwingWorker<List<Cidadao>, Void>() {
            @Override protected List<Cidadao> doInBackground() throws Exception {
                unificacaoService.carregarBaseCadastral(csvFinal);
                unificacaoService.mergeIndicadores(xlsxCopia);
                return unificacaoService.getCidadaos();
            }
            @Override protected void done() {
                try {
                    List<Cidadao> lista = get();
                    atualizarCombos();
                    cidadaosExibidos = lista;
                    modeloTabela.setCidadaos(lista);
                    lblContagem.setText(lista.size() + " cidadãos");
                    btnExportar.setEnabled(true);
                    progressBar.setValue(100);
                    lblProgresso.setText("Concluído!");
                    limparFiltros();
                    JOptionPane.showMessageDialog(MainFrame.this,
                        "Processamento concluído!\n" + lista.size() + " cidadãos carregados.",
                        "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(MainFrame.this,
                        "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                } finally {
                    btnProcessar.setEnabled(true);
                    new Timer(3000, e -> progressBar.setVisible(false)) {{ setRepeats(false); start(); }};
                }
            }
        }.execute();
    }

    private void atualizarCombos() {
        DefaultComboBoxModel<String> mM = new DefaultComboBoxModel<>();
        mM.addElement("TODAS");
        unificacaoService.getMicroareas().forEach(mM::addElement);
        cmbMicroarea.setModel(mM);

        DefaultComboBoxModel<String> mI = new DefaultComboBoxModel<>();
        mI.addElement("TODOS");
        unificacaoService.getIndicadoresPresentes().forEach(mI::addElement);
        cmbIndicador.setModel(mI);

        // Criar abas individuais para cada indicador carregado
        criarAbasIndicadores();
    }

    private void aplicarFiltros() {
        if (unificacaoService.getCidadaos().isEmpty()) return;

        String microarea  = (String) cmbMicroarea.getSelectedItem();
        String indicador  = (String) cmbIndicador.getSelectedItem();
        boolean pendentes = chkPendentes.isSelected();

        // Remove qualquer caractere que não seja dígito ou letra para normalizar CPF
        String termoCpf = txtCpf.getText().trim().replaceAll("[^0-9]", "");

        List<Cidadao> resultado = unificacaoService.filtrar(microarea, indicador, pendentes);

        // Filtro por CPF/CNS (substring, apenas dígitos)
        if (!termoCpf.isEmpty()) {
            final String t = termoCpf;
            resultado = resultado.stream()
                .filter(c -> {
                    String cpf = c.getCpf() != null ? c.getCpf() : "";
                    String cns = c.getCns() != null ? c.getCns() : "";
                    return cpf.contains(t) || cns.contains(t);
                })
                .collect(Collectors.toList());
        }

        cidadaosExibidos = resultado;
        modeloTabela.setCidadaos(resultado);
        lblContagem.setText(resultado.size() + (resultado.size() == 1 ? " cidadão" : " cidadãos"));

        // Propagar filtros para cada aba de indicador individual
        for (Map.Entry<String, IndicadorTableModel> entry : modelosAbas.entrySet()) {
            // Nas abas individuais, filtra apenas cidadãos vinculados a este indicador
            List<Cidadao> filtradosParaAba = resultado.stream()
                .filter(c -> c.getIndicadores().containsKey(entry.getKey()))
                .collect(Collectors.toList());
            entry.getValue().setCidadaos(filtradosParaAba);
        }
    }

    private void limparFiltros() {
        cmbMicroarea.setSelectedIndex(0);
        cmbIndicador.setSelectedIndex(0);
        chkPendentes.setSelected(false);
        txtCpf.setText("");
        // aplicarFiltros() será chamado pelo debounce do txtCpf
    }

    private void exportarCsv() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Salvar CSV consolidado");
        fc.setFileFilter(new FileNameExtensionFilter("CSV (*.csv)", "csv"));
        fc.setSelectedFile(new File(ExportacaoService.gerarNomeArquivo()));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File dest = fc.getSelectedFile();
        if (!dest.getName().endsWith(".csv")) dest = new File(dest.getAbsolutePath() + ".csv");
        final File destFinal = dest;
        final List<Cidadao> lista = new ArrayList<>(cidadaosExibidos);

        new SwingWorker<File, Void>() {
            @Override protected File doInBackground() throws Exception {
                return exportacaoService.exportarCsv(lista, destFinal);
            }
            @Override protected void done() {
                try {
                    JOptionPane.showMessageDialog(MainFrame.this,
                        "Exportação concluída!\n" + get().getAbsolutePath(),
                        "Exportação", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MainFrame.this,
                        "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // =========================================================================
    // UTILITÁRIOS DE UI
    // =========================================================================

    private JPanel criarSecao(String titulo) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0xB0BEC5), 1, true),
            new EmptyBorder(8, 8, 8, 8)
        ));
        JLabel lbl = new JLabel(titulo);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(AZUL_PRIMARIO);
        lbl.setBorder(new EmptyBorder(0, 0, 6, 0));
        p.add(lbl);
        return p;
    }

    private JLabel criarLabel(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        l.setForeground(TEXTO_MEDIO);
        l.setBorder(new EmptyBorder(0, 0, 2, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private void estilizarCombo(JComboBox<String> c) {
        c.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        c.setBackground(BRANCO);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private JButton criarBotao(String texto, Color bg, Color fg) {
        JButton b = new JButton(texto);
        b.setFont(new Font("Segoe UI", Font.BOLD, 11));
        b.setBackground(bg); b.setForeground(fg);
        b.setFocusPainted(false); b.setBorderPainted(false); b.setOpaque(true);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setBackground(bg.darker()); }
            @Override public void mouseExited(MouseEvent e)  { b.setBackground(bg); }
        });
        return b;
    }

    private JButton criarBotaoSecundario(String texto) {
        JButton b = new JButton(texto);
        b.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        b.setBackground(BRANCO); b.setForeground(AZUL_PRIMARIO);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createLineBorder(AZUL_CLARO, 1));
        b.setOpaque(true);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // =========================================================================
    // INNER CLASSES
    // =========================================================================

    /**
     * TableModel dinâmico: 5 colunas fixas + N colunas de indicadores.
     */
    class CidadaoTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
		private final String[] COLS_FIXAS = {"Nome", "CPF / CNS", "Nascimento", "Idade", "Microárea"};
        private List<Cidadao> cidadaos       = new ArrayList<>();
        private List<String>  colsIndicadores = new ArrayList<>();

        public void setCidadaos(List<Cidadao> lista) {
            this.cidadaos = lista;
            Set<String> tipos = new LinkedHashSet<>();
            lista.forEach(c -> tipos.addAll(c.getIndicadores().keySet()));
            this.colsIndicadores = new ArrayList<>(tipos);
            fireTableStructureChanged();
            SwingUtilities.invokeLater(() -> { configurarTabela(); ajustarLarguras(); });
        }

        @Override public int getRowCount()    { return cidadaos.size(); }
        @Override public int getColumnCount() { return COLS_FIXAS.length + colsIndicadores.size(); }

        @Override public String getColumnName(int col) {
            if (col < COLS_FIXAS.length) return COLS_FIXAS[col];
            String tipo = colsIndicadores.get(col - COLS_FIXAS.length);
            try {
                TipoIndicador t = TipoIndicador.valueOf(tipo);
                return t.getEmoji() + " " + t.getDescricao();
            } catch (Exception e) { return tipo; }
        }

        @Override public Object getValueAt(int row, int col) {
            if (row >= cidadaos.size()) return null;
            Cidadao c = cidadaos.get(row);
            switch (col) {
                case 0: return c.getNome() != null ? c.getNome() : "—";
                case 1: return formatarCpfCns(c);
                case 2: return c.getDataNascimento() != null ? c.getDataNascimento() : "—";
                case 3: return c.getIdade() != null ? c.getIdade() : "—";
                case 4: return c.getMicroarea() != null ? c.getMicroarea() : "—";
                default:
                    String tipo = colsIndicadores.get(col - COLS_FIXAS.length);
                    return c.getIndicadores().get(tipo); // null = cidadão não tem este indicador
            }
        }

        @Override public Class<?> getColumnClass(int col) {
            return col >= COLS_FIXAS.length ? Indicador.class : String.class;
        }

        private String formatarCpfCns(Cidadao c) {
            if (c.getCpf() != null && c.getCpf().length() == 11) {
                String v = c.getCpf();
                return v.substring(0,3)+"."+v.substring(3,6)+"."+v.substring(6,9)+"-"+v.substring(9);
            }
            if (c.getCpf() != null) return c.getCpf();
            if (c.getCns() != null) return "CNS " + c.getCns();
            return "—";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Renderer do cabeçalho da tabela.
     *
     * Resolve o problema de ilegibilidade em qualquer Look & Feel:
     * pinta diretamente com fundo azul escuro e texto branco,
     * sem depender do UIManager do sistema operacional.
     *
     * Colunas de indicador recebem o nome em duas linhas (emoji + descrição),
     * evitando o truncamento típico com nomes longos.
     */
    class HeaderRenderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;

		HeaderRenderer() {
            setHorizontalAlignment(JLabel.CENTER);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {

            String raw = value != null ? value.toString() : "";
            boolean isFixa = col < 5;

            if (isFixa) {
                // Colunas fixas: negrito centralizado
                setText(raw);
                setFont(new Font("Segoe UI", Font.BOLD, 12));
            } else {
                // Colunas de indicador: emoji na primeira linha, nome na segunda
                int esp = raw.indexOf(' ');
                if (esp > 0) {
                    String emoji = raw.substring(0, esp);
                    String nome  = raw.substring(esp + 1);
                    // Fonte menor para o nome
                    setText("<html><center><span style='font-size:13px'>" + emoji
                          + "</span><br><span style='font-size:9px;font-weight:normal'>"
                          + nome + "</span></center></html>");
                } else {
                    setText("<html><center>" + raw + "</center></html>");
                }
                setFont(new Font("Segoe UI", Font.BOLD, 11));
            }

            setForeground(BRANCO);
            setBackground(AZUL_HEADER);
            setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 2, 1, new Color(0x283593)),
                new EmptyBorder(3, 6, 3, 6)
            ));
            return this;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Renderer das células de dados.
     *
     * - Zebra (linhas alternadas branco / azul muito claro)
     * - Colunas de texto: alinhamento à esquerda, padding interno
     * - Coluna Nome: negrito
     * - Indicador presente e OK: fundo verde pastel, texto verde escuro, ícone ✓
     * - Indicador presente e PENDENTE: fundo vermelho pastel, texto vermelho, ícone ⚠
     * - Indicador ausente (null): fundo cinza claro, traço "—"
     */
    class CidadaoTableRenderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;

		private final Color PAR      = BRANCO;
        private final Color IMPAR    = new Color(0xF3F7FB);
        private final Color SEL      = new Color(0xBBDEFB);

        // Badge OK
        private final Color OK_BG    = new Color(0xE8F5E9);
        private final Color OK_FG    = new Color(0x1B5E20);
        // Badge PENDENTE
        private final Color PEND_BG  = new Color(0xFFEBEE);
        private final Color PEND_FG  = new Color(0xB71C1C);
        // Badge AUSENTE
        private final Color AUS_BG   = new Color(0xF5F5F5);
        private final Color AUS_FG   = new Color(0xBDBDBD);

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {

            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            setBorder(new EmptyBorder(0, 8, 0, 8));

            Color bgBase = row % 2 == 0 ? PAR : IMPAR;

            if (value instanceof Indicador) {
                Indicador ind = (Indicador) value;
                setHorizontalAlignment(JLabel.CENTER);
                setFont(new Font("Segoe UI", Font.BOLD, 11));
                setBorder(new EmptyBorder(0, 4, 0, 4));

                if (isSelected) {
                    setBackground(SEL); setForeground(TEXTO_ESCURO);
                    setText(ind.isPendente() ? "⚠  PENDENTE" : "✓  OK");
                } else if (ind.isPendente()) {
                    setBackground(PEND_BG); setForeground(PEND_FG);
                    setText("⚠  PENDENTE");
                } else {
                    setBackground(OK_BG); setForeground(OK_FG);
                    setText("✓  OK");
                }

            } else if (value == null) {
                // Indicador não vinculado a este cidadão
                setHorizontalAlignment(JLabel.CENTER);
                setFont(new Font("Segoe UI", Font.PLAIN, 12));
                setText("—");
                setForeground(isSelected ? TEXTO_ESCURO : AUS_FG);
                setBackground(isSelected ? SEL : AUS_BG);
                setBorder(new EmptyBorder(0, 4, 0, 4));

            } else {
                // Colunas de texto
                setHorizontalAlignment(JLabel.LEFT);
                setFont(new Font("Segoe UI", col == 0 ? Font.BOLD : Font.PLAIN, 12));
                setForeground(isSelected ? TEXTO_ESCURO : TEXTO_ESCURO);
                setBackground(isSelected ? SEL : bgBase);
            }

            return this;
        }
    }
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * MouseListener dedicado para o double-click na tabela.
     *
     * Usa um guard booleano (detalheAberto) para garantir que apenas
     * uma instância do DetalheDialog exista por vez, independente de
     * quantas vezes configurarTabela() for chamado ou quantos cliques
     * o usuário der rapidamente.
     */
    class TabelaDoubleClickListener extends MouseAdapter {

        // Guard: impede abrir um segundo diálogo enquanto um já está visível
        private boolean detalheAberto = false;

        @Override
        public void mouseClicked(MouseEvent e) {
            // Ignora cliques simples e cliques enquanto um diálogo já está aberto
            if (e.getClickCount() < 2 || detalheAberto) return;

            int vRow = tabelaCidadaos.getSelectedRow();
            if (vRow < 0) return;

            int mRow = tabelaCidadaos.convertRowIndexToModel(vRow);
            if (mRow < 0 || mRow >= cidadaosExibidos.size()) return;

            detalheAberto = true;
            try {
                Set<String> todosInd = unificacaoService.getIndicadoresPresentes();
                DetalheDialog dlg = new DetalheDialog(MainFrame.this, cidadaosExibidos.get(mRow), todosInd);
                // Garante que o guard seja resetado ao fechar, mesmo se o usuário
                // fechar pelo X da janela em vez do botão "Fechar"
                dlg.addWindowListener(new WindowAdapter() {
                    @Override public void windowClosed(WindowEvent we) {
                        detalheAberto = false;
                    }
                });
                dlg.setVisible(true); // bloqueia aqui (modal)
            } finally {
                // Segurança extra: garante reset mesmo se setVisible lançar exceção
                detalheAberto = false;
            }
        }
    }

    // =========================================================================
    // INNER CLASS — modelo da tabela de aba individual de indicador
    // =========================================================================

    /**
     * TableModel para as abas individuais de cada indicador SIAPS.
     *
     * Colunas fixas: Nome, CPF/CNS, Microárea, Nascimento, Sexo
     * Colunas dinâmicas: todas as colunas de ação (A, B, C...) presentes
     *                    nos dados deste indicador + NM + DN
     *
     * Espelha fielmente a estrutura da planilha original.
     */
    class IndicadorTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
		private final String         chaveIndicador;
        private List<Cidadao>        cidadaos  = new ArrayList<>();
        private List<String>         colunas   = new ArrayList<>(); // colunas dinâmicas (acoes + NM + DN)
        private JLabel               lblContagem;
        private JTable               tabela;

        // Colunas fixas à esquerda
        private final String[] FIXAS = {"Nome", "CPF / CNS", "Microárea", "Nascimento", "Sexo"};

        IndicadorTableModel(String chaveIndicador) {
            this.chaveIndicador = chaveIndicador;
        }

        void setLabelContagem(JLabel lbl) { this.lblContagem = lbl; }
        void setTabela(JTable t)          { this.tabela = t; }

        public void setCidadaos(List<Cidadao> lista) {
            this.cidadaos = lista;

            // Reconstruir colunas dinâmicas a partir dos dados reais
            LinkedHashSet<String> colSet = new LinkedHashSet<>();
            for (Cidadao c : lista) {
                Indicador ind = c.getIndicadores().get(chaveIndicador);
                if (ind != null) {
                    colSet.addAll(ind.getAcoes().keySet());
                }
            }
            // NM e DN sempre ao final
            colSet.add("NM");
            colSet.add("DN");
            this.colunas = new ArrayList<>(colSet);

            fireTableStructureChanged();

            // Ajustar larguras das colunas após rebuild
            if (tabela != null) {
                SwingUtilities.invokeLater(() -> {
                    configurarTabelaIndicador(tabela);
                    ajustarLargurasIndicador(tabela, colunas.size());
                });
            }

            // Atualizar label de contagem da barra do painel
            if (lblContagem != null) {
                int total    = lista.size();
                long pend    = lista.stream()
                    .filter(c -> { Indicador i = c.getIndicadores().get(chaveIndicador);
                                   return i != null && i.isPendente(); })
                    .count();
                String txt   = total + " registro" + (total != 1 ? "s" : "");
                if (pend > 0) txt += "  |  ⚠ " + pend + " pendente" + (pend != 1 ? "s" : "");
                lblContagem.setText(txt);
            }
        }

        @Override public int getRowCount()    { return cidadaos.size(); }
        @Override public int getColumnCount() { return FIXAS.length + colunas.size(); }

        @Override public String getColumnName(int col) {
            if (col < FIXAS.length) return FIXAS[col];
            String c = colunas.get(col - FIXAS.length);
            if ("NM".equals(c)) return "NM (Numerador)";
            if ("DN".equals(c)) return "DN (Denominador)";
            return "Ação " + c;
        }

        @Override public Object getValueAt(int row, int col) {
            if (row >= cidadaos.size()) return null;
            Cidadao c = cidadaos.get(row);
            Indicador ind = c.getIndicadores().get(chaveIndicador);

            switch (col) {
                case 0: return c.getNome()          != null ? c.getNome()          : "—";
                case 1: return formatarCpfCns(c);
                case 2: return c.getMicroarea()     != null ? c.getMicroarea()     : "—";
                case 3: return c.getDataNascimento()!= null ? c.getDataNascimento(): "—";
                case 4: return c.getSexo()          != null ? c.getSexo()          : "—";
                default:
                    if (ind == null) return "—";
                    String colNome = colunas.get(col - FIXAS.length);
                    if ("NM".equals(colNome)) return ind.isPendente() ? ind : (ind.getNm() != null ? ind.getNm() : "—");
                    if ("DN".equals(colNome)) return ind.getDn() != null ? ind.getDn() : "—";
                    // Coluna de ação: X se marcada, vazio se não
                    Boolean marcada = ind.getAcoes().get(colNome);
                    return Boolean.TRUE.equals(marcada) ? "X" : "";
            }
        }

        @Override public Class<?> getColumnClass(int col) {
            // Coluna NM retorna Indicador para acionar o renderer de badge
            if (col >= FIXAS.length) {
                String colNome = colunas.isEmpty() ? "" : colunas.get(col - FIXAS.length);
                if ("NM".equals(colNome)) return Indicador.class;
            }
            return String.class;
        }

        private String formatarCpfCns(Cidadao c) {
            if (c.getCpf() != null && c.getCpf().length() == 11) {
                String v = c.getCpf();
                return v.substring(0,3)+"."+v.substring(3,6)+"."+v.substring(6,9)+"-"+v.substring(9);
            }
            if (c.getCpf() != null) return c.getCpf();
            if (c.getCns() != null) return "CNS " + c.getCns();
            return "—";
        }
    }

    /** Ajusta larguras das colunas das abas individuais. */
    private void ajustarLargurasIndicador(JTable tabela, int nColsDinamicas) {
        TableColumnModel cm = tabela.getColumnModel();
        int total = cm.getColumnCount();
        // Fixas: Nome, CPF, Microárea, Nascimento, Sexo
        int[] fixas = {200, 120, 90, 90, 60};
        for (int i = 0; i < Math.min(fixas.length, total); i++) {
            cm.getColumn(i).setPreferredWidth(fixas[i]);
            cm.getColumn(i).setMinWidth(50);
        }
        // Colunas de ação: compactas
        for (int i = fixas.length; i < total - 2 && i < total; i++) {
            cm.getColumn(i).setPreferredWidth(65);
            cm.getColumn(i).setMinWidth(40);
        }
        // NM e DN: um pouco mais largas
        if (total >= 2) {
            cm.getColumn(total - 2).setPreferredWidth(110); // NM
            cm.getColumn(total - 1).setPreferredWidth(110); // DN
        }
    }


}
