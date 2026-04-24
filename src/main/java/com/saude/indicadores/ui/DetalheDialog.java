package com.saude.indicadores.ui;

import com.saude.indicadores.model.Cidadao;
import com.saude.indicadores.model.Indicador;
import com.saude.indicadores.model.Indicador.TipoIndicador;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.*;

/**
 * Diálogo de detalhes de um cidadão.
 *
 * Exibe SEMPRE todos os indicadores disponíveis na sessão, com três estados visuais:
 *   ✓ OK        — cidadão vinculado, NM preenchido  (verde)
 *   ⚠ PENDENTE  — cidadão vinculado, NM ausente/zero (vermelho)
 *   — N/A       — cidadão NÃO vinculado a este indicador (cinza)
 *
 * @param todosIndicadores conjunto de chaves de indicadores carregados na sessão
 *                         (ex.: {"DIABETES", "HIPERTENSAO", ...})
 */
public class DetalheDialog extends JDialog {

    private static final long serialVersionUID = 1L;
	// ─── Paleta ───────────────────────────────────────────────────────────────
    private static final Color AZUL         = new Color(0x1565C0);
    private static final Color AZUL_ESCURO  = new Color(0x0D47A1);
    private static final Color VERDE_BG     = new Color(0xE8F5E9);
    private static final Color VERDE_FG     = new Color(0x1B5E20);
    private static final Color VERDE_BORDA  = new Color(0x66BB6A);
    private static final Color VERM_BG      = new Color(0xFFEBEE);
    private static final Color VERM_FG      = new Color(0xB71C1C);
    private static final Color VERM_BORDA   = new Color(0xEF9A9A);
    private static final Color CINZA_BG     = new Color(0xF5F5F5);
    private static final Color CINZA_FG     = new Color(0x9E9E9E);
    private static final Color CINZA_BORDA  = new Color(0xE0E0E0);
    private static final Color FUNDO        = new Color(0xF0F4F8);
    private static final Color BRANCO       = Color.WHITE;
    private static final Color TEXTO_CLARO  = new Color(0xBBDEFB);

    // ─────────────────────────────────────────────────────────────────────────

    public DetalheDialog(Frame parent, Cidadao cidadao, Set<String> todosIndicadores) {
        super(parent, "Detalhes: " + nullSafe(cidadao.getNome()), true);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setSize(660, 580);
        setMinimumSize(new Dimension(500, 400));
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        getContentPane().setBackground(FUNDO);

        add(criarHeader(cidadao),              BorderLayout.NORTH);
        add(criarCorpo(cidadao, todosIndicadores), BorderLayout.CENTER);
        add(criarRodape(),                     BorderLayout.SOUTH);
    }

    // =========================================================================
    // HEADER — dados cadastrais
    // =========================================================================

    private JPanel criarHeader(Cidadao c) {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(AZUL_ESCURO);

        // Título
        JLabel lblTitulo = new JLabel("  " + nullSafe(c.getNome()));
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblTitulo.setForeground(BRANCO);
        lblTitulo.setBorder(new EmptyBorder(10, 14, 6, 14));
        outer.add(lblTitulo, BorderLayout.NORTH);

        // Grid de dados: 3 colunas de pares label→valor
        JPanel grid = new JPanel(new GridLayout(2, 6, 12, 4));
        grid.setBackground(AZUL);
        grid.setBorder(new EmptyBorder(6, 16, 12, 16));

        addInfo(grid, "CPF:",       formatCpf(c.getCpf()));
        addInfo(grid, "CNS:",       c.getCns());
        addInfo(grid, "Microárea:", c.getMicroarea());
        addInfo(grid, "Nasc.:",     c.getDataNascimento());
        addInfo(grid, "Idade:",     c.getIdade());
        addInfo(grid, "Sexo:",      c.getSexo());

        outer.add(grid, BorderLayout.CENTER);
        return outer;
    }

    // =========================================================================
    // CORPO — cards de indicadores (todos, sempre)
    // =========================================================================

    private JScrollPane criarCorpo(Cidadao c, Set<String> todosIndicadores) {
        JPanel corpo = new JPanel();
        corpo.setLayout(new BoxLayout(corpo, BoxLayout.Y_AXIS));
        corpo.setBackground(FUNDO);
        corpo.setBorder(new EmptyBorder(10, 10, 10, 10));

        if (todosIndicadores.isEmpty()) {
            // Sessão sem nenhum XLSX carregado
            JLabel aviso = new JLabel("Nenhum arquivo de indicadores foi carregado nesta sessão.");
            aviso.setFont(new Font("Segoe UI", Font.ITALIC, 13));
            aviso.setForeground(CINZA_FG);
            aviso.setAlignmentX(Component.CENTER_ALIGNMENT);
            corpo.add(Box.createVerticalStrut(20));
            corpo.add(aviso);
        } else {
            // Itera pela ordem de inserção dos indicadores carregados
            for (String chave : todosIndicadores) {
                Indicador ind = c.getIndicadores().get(chave); // null se não vinculado
                corpo.add(criarCard(chave, ind));
                corpo.add(Box.createVerticalStrut(8));
            }
        }

        JScrollPane scroll = new JScrollPane(corpo);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(FUNDO);
        scroll.getVerticalScrollBar().setUnitIncrement(12);
        return scroll;
    }

    /**
     * Cria um card para um indicador.
     *
     * @param chave  nome interno do indicador (ex.: "DIABETES")
     * @param ind    dados do indicador para este cidadão, ou {@code null} se não vinculado
     */
    private JPanel criarCard(String chave, Indicador ind) {

        // ── Determinar estado ────────────────────────────────────────────────
        final boolean vinculado = ind != null;
        final boolean pendente  = vinculado && ind.isPendente();

        Color bg     = vinculado ? (pendente ? VERM_BG    : VERDE_BG)    : CINZA_BG;
        Color borda  = vinculado ? (pendente ? VERM_BORDA : VERDE_BORDA) : CINZA_BORDA;
        Color fgBadge= vinculado ? (pendente ? VERM_FG    : VERDE_FG)    : CINZA_FG;
        String icone = vinculado ? (pendente ? "⚠"        : "✓")         : "—";
        String texto = vinculado ? (pendente ? "PENDENTE"  : "OK")        : "Não vinculado";

        // ── Nome amigável ────────────────────────────────────────────────────
        String nomeAmigavel = chave;
        try {
            TipoIndicador tipo = TipoIndicador.valueOf(chave);
            nomeAmigavel = tipo.getEmoji() + "  " + tipo.getDescricao();
        } catch (Exception ignored) {}

        // ── Card principal ───────────────────────────────────────────────────
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(bg);
        card.setOpaque(true);
        card.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(borda, 1, true),
            new EmptyBorder(10, 12, 10, 12)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, vinculado ? 160 : 48));

        // ── Linha de título ──────────────────────────────────────────────────
        JPanel tituloPanel = new JPanel(new BorderLayout());
        tituloPanel.setOpaque(false);

        JLabel lblNome = new JLabel(nomeAmigavel);
        lblNome.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblNome.setForeground(vinculado ? AZUL : CINZA_FG);

        JLabel lblBadge = new JLabel(icone + "  " + texto);
        lblBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblBadge.setForeground(fgBadge);
        lblBadge.setHorizontalAlignment(JLabel.RIGHT);

        tituloPanel.add(lblNome,  BorderLayout.WEST);
        tituloPanel.add(lblBadge, BorderLayout.EAST);
        card.add(tituloPanel, BorderLayout.NORTH);

        // ── Detalhes (só se vinculado) ───────────────────────────────────────
        if (vinculado) {
            JPanel detalhes = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
            detalhes.setOpaque(false);

            // NM e DN sempre exibidos
            detalhes.add(criarChip("NM", ind.getNm(),
                pendente ? new Color(0xD32F2F) : new Color(0x388E3C)));
            detalhes.add(criarChip("DN", ind.getDn(), new Color(0x1976D2)));

            // Separador visual
            JLabel sep = new JLabel("  |  ");
            sep.setForeground(new Color(0xCCCCCC));
            detalhes.add(sep);

            // Colunas de ação: mostra TODAS com ✓ ou ✗
            boolean temAcao = false;
            for (Map.Entry<String, Boolean> acao : ind.getAcoes().entrySet()) {
                temAcao = true;
                detalhes.add(criarChipAcao(acao.getKey(), acao.getValue()));
            }
            if (!temAcao) {
                JLabel semAcoes = new JLabel("Sem colunas de ação");
                semAcoes.setFont(new Font("Segoe UI", Font.ITALIC, 11));
                semAcoes.setForeground(CINZA_FG);
                detalhes.add(semAcoes);
            }

            card.add(detalhes, BorderLayout.CENTER);
        }

        return card;
    }

    // =========================================================================
    // RODAPÉ
    // =========================================================================

    private JPanel criarRodape() {
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        rodape.setBackground(new Color(0xE0E0E0));
        rodape.setBorder(new MatteBorder(1, 0, 0, 0, new Color(0xBDBDBD)));

        JButton btnFechar = new JButton("Fechar");
        btnFechar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnFechar.setFocusPainted(false);
        btnFechar.addActionListener(e -> dispose());
        rodape.add(btnFechar);
        return rodape;
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /** Chip colorido para NM/DN. */
    private JLabel criarChip(String label, String valor, Color cor) {
        String txt = label + ": " + (valor == null || valor.isBlank() ? "—" : valor);
        JLabel chip = new JLabel(txt);
        chip.setFont(new Font("Segoe UI", Font.BOLD, 11));
        chip.setForeground(BRANCO);
        chip.setBackground(cor);
        chip.setOpaque(true);
        chip.setBorder(new EmptyBorder(2, 7, 2, 7));
        return chip;
    }

    /** Chip de ação: verde se marcada, cinza escuro se não. */
    private JLabel criarChipAcao(String letra, boolean marcada) {
        String txt = letra + ": " + (marcada ? "✓" : "✗");
        JLabel chip = new JLabel(txt);
        chip.setFont(new Font("Segoe UI", Font.BOLD, 10));
        chip.setForeground(BRANCO);
        chip.setBackground(marcada ? new Color(0x43A047) : new Color(0x9E9E9E));
        chip.setOpaque(true);
        chip.setBorder(new EmptyBorder(1, 5, 1, 5));
        chip.setToolTipText("Ação " + letra + ": " + (marcada ? "realizada" : "não realizada"));
        return chip;
    }

    private void addInfo(JPanel panel, String label, String valor) {
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lbl.setForeground(TEXTO_CLARO);

        JLabel val = new JLabel(valor != null && !valor.isBlank() ? valor : "—");
        val.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        val.setForeground(BRANCO);

        panel.add(lbl);
        panel.add(val);
    }

    private String formatCpf(String cpf) {
        if (cpf == null) return null;
        if (cpf.length() == 11)
            return cpf.substring(0,3)+"."+cpf.substring(3,6)+"."+cpf.substring(6,9)+"-"+cpf.substring(9);
        return cpf;
    }

    private static String nullSafe(String s) {
        return s != null ? s : "(Sem nome)";
    }
}
