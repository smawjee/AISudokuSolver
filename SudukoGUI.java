package sudoku;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;

/**
 * SudukoGUI — Swing UI for loading, validating, and solving Sudoku.
 *
 * Buttons:
 *  • Load      — open .txt/.csv, parse, populate grid ('.' internally for blanks)
 *  • Validate  — check givens only; highlight all conflicting cells in red
 *  • Solve     — run backtracking+MRV in a worker thread; update UI on the EDT
 *  • Clear     — clear grid, metrics, and highlights
 *
 * Input UX:
 *  • Only digits 1..9 are kept; 0/dot/whitespace/other → blank
 *  • Arrow keys/Enter move focus; DEL/BACKSPACE clears the current cell
 *
 * Threading:
 *  • Long-running solve runs off the EDT; UI changes are done via SwingUtilities.invokeLater
 *
 * TODO (extensions, not required):
 *  - Live validation (color a cell red as soon as it creates a conflict)
 *  - Save button to export the current/solved grid to CSV
 *  - Lock givens (style given cells differently and prevent editing)
 */
public class SudukoGUI extends JFrame {

    private static final long serialVersionUID = 1L;

    // ---- UI widgets ----
    private final JTextField[][] cells = new JTextField[9][9];
    private final JLabel statusLabel  = new JLabel("Ready"); // left: short status
    private final JLabel metricsLabel = new JLabel(" ");     // right: solver metrics

    private final JButton loadBtn     = new JButton("Load");
    private final JButton validateBtn = new JButton("Validate");
    private final JButton solveBtn    = new JButton("Solve");
    private final JButton clearBtn    = new JButton("Clear");

    // ---- Colors ----
    private static final Color BG_A   = new Color(248,248,252);
    private static final Color BG_B   = new Color(235,239,246);
    private static final Color BAD_BG = new Color(255,210,210);
    private static final Color BAD_FG = new Color(140,  0,  0);

    public SudukoGUI() {
        super("Sudoku Solver — F29AI GUI");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10,10));
        setMinimumSize(new Dimension(720,640));

        // Top: controls; Center: grid; Bottom: status/metrics
        add(buildControlPanel(), BorderLayout.NORTH);
        add(buildGridPanel(),    BorderLayout.CENTER);
        add(buildStatusPanel(),  BorderLayout.SOUTH);

        pack();
    }

    /** Build the top control strip (buttons + listeners).
     *  Keep listeners thin: just call onLoad/onValidate/onSolve/clearGrid. */
    private JPanel buildControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 10));

        panel.add(loadBtn);
        panel.add(validateBtn);
        panel.add(solveBtn);
        panel.add(clearBtn);

        // Button handlers (thin delegates)
        loadBtn.addActionListener(e -> onLoad());
        validateBtn.addActionListener(e -> onValidate());
        solveBtn.addActionListener(e -> onSolve());
        clearBtn.addActionListener(e -> clearGrid());

        return panel;
    }

    /** Build the 9×9 grid with thick borders around 3×3 boxes and input sanitization. */
    private JPanel buildGridPanel() {
        JPanel grid = new JPanel(new GridLayout(9,9));
        grid.setBorder(new MatteBorder(2,2,2,2, Color.GRAY));

        Font f = new Font(Font.MONOSPACED, Font.BOLD, 20);

        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                final int rr = r, cc = c;
                JTextField tf = new JTextField();

                // Cell look & feel
                tf.setHorizontalAlignment(JTextField.CENTER);
                tf.setFont(f);
                tf.setPreferredSize(new Dimension(48,48));
                setDefaultCellColors(tf, rr, cc);

                // Compute per-edge thickness so 3×3 boxes are visually separated.
                int top    = (r % 3 == 0) ? 2 : 1;
                int left   = (c % 3 == 0) ? 2 : 1;
                int bottom = (r == 8)     ? 2 : ((r % 3 == 2) ? 2 : 1);
                int right  = (c == 8)     ? 2 : ((c % 3 == 2) ? 2 : 1);
                tf.setBorder(new MatteBorder(top,left,bottom,right, Color.GRAY));

                // Keyboard behavior:
                // - DEL/BACKSPACE clears
                // - Arrow keys/Enter move focus
                // - On release, only keep last char if it's 1..9; else clear
                tf.addKeyListener(new KeyListener() {
                    public void keyTyped(KeyEvent e) { /* let default insert; clean after */ }

                    public void keyPressed(KeyEvent e) {
                        int code = e.getKeyCode();

                        if (code == KeyEvent.VK_DELETE || code == KeyEvent.VK_BACK_SPACE) {
                            tf.setText("");
                            return;
                        }

                        int nr = rr, nc = cc;
                        if      (code == KeyEvent.VK_UP)    nr = Math.max(0, rr-1);
                        else if (code == KeyEvent.VK_DOWN)  nr = Math.min(8, rr+1);
                        else if (code == KeyEvent.VK_LEFT)  nc = Math.max(0, cc-1);
                        else if (code == KeyEvent.VK_RIGHT ||
                                 code == KeyEvent.VK_ENTER) nc = Math.min(8, cc+1);
                        else return; // ignore other keys

                        cells[nr][nc].requestFocusInWindow();
                    }

                    public void keyReleased(KeyEvent e) {
                        // Accept only a single digit 1..9; treat 0/./whitespace/other as blank.
                        String t = tf.getText();
                        if (t == null || t.isEmpty()) return;

                        char ch = t.charAt(t.length() - 1);
                        tf.setText((ch >= '1' && ch <= '9') ? Character.toString(ch) : "");
                    }
                });

                cells[r][c] = tf;
                grid.add(tf);
            }
        }
        return grid;
    }

    /** Bottom strip: left = short status message; right = solver metrics after Solve. */
    private JPanel buildStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(statusLabel,  BorderLayout.WEST);
        panel.add(metricsLabel, BorderLayout.EAST);
        return panel;
    }

    /* ======================= Actions ======================= */

    /** Open a .txt/.csv, parse with SudokuCW1.loadFromFile(), push to UI, clear metrics/highlights. */
    private void onLoad() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Open Sudoku (.txt or .csv)");
        fc.setAcceptAllFileFilterUsed(false);
        fc.addChoosableFileFilter(new FileNameExtensionFilter("TXT and CSV", "txt", "csv"));

        int ans = fc.showOpenDialog(this);
        if (ans == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                char[][] board = SudokuCW1.loadFromFile(f.getAbsolutePath());
                setBoard(board);
                status("Loaded: " + f.getName());
                metricsLabel.setText(" ");
                clearHighlights();
            } catch (Exception ex) {
                error("Failed to load: " + ex.getMessage());
            }
        }
    }

    /** Validate givens only (no solving). Consistent = no duplicates in any row/column/box. */
    private void onValidate() {
        clearHighlights();
        char[][] b = getBoard();
        if (isBoardConsistent(b)) {
            status("Board is consistent.");
        } else {
            status("Conflicts found. Cells in conflict are highlighted.");
            highlightConflicts(b);
        }
    }

    /**
     * Solve in a background thread:
     * 1) Gate on current givens being consistent (avoids wasted work).
     * 2) Disable buttons while solving; re-enable when done.
     * 3) After solving, push board & print metrics (Time, Assignments, Backtracks, MaxDepth).
     */
    private void onSolve() {
        clearHighlights();
        final char[][] b = getBoard();

        // Prevent wasting time on inconsistent givens
        if (!isBoardConsistent(b)) {
            error("Conflicting givens. Fix the red cells and try again.");
            highlightConflicts(b);
            return;
        }

        setBusy(true);                     // prevents accidental re-entry (double clicks)
        status("Solving...");
        metricsLabel.setText(" ");

        // Worker thread: compute off the EDT, then update UI via invokeLater
        Thread t = new Thread(() -> {
            SudokuCW1 solver = new SudokuCW1();
            final boolean solved = solver.solveSudoku(b);
            final SudokuCW1.Metrics m = solver.getMetrics();

            SwingUtilities.invokeLater(() -> {
                setBoard(b);
                status(solved ? "Solved ✔" : "No solution found ✖");
                metricsLabel.setText(
                    "Time(ms): " + m.elapsedMs() +
                    "  Assign: " + m.assignments +
                    "  Backtracks: " + m.backtracks +
                    "  MaxDepth: " + m.maxdepth
                );
                setBusy(false);
            });
        });
        t.start();
    }

    /* ======================= Helpers ======================= */

    private void status(String msg) {
        statusLabel.setForeground(new Color(60,60,60));
        statusLabel.setText(msg);
    }
    private void error(String msg) {
        statusLabel.setForeground(new Color(160,30,30));
        statusLabel.setText(msg);
    }

    /** Enable/disable controls during long-running solve. */
    private void setBusy(boolean busy) {
        loadBtn.setEnabled(!busy);
        validateBtn.setEnabled(!busy);
        solveBtn.setEnabled(!busy);
        clearBtn.setEnabled(!busy);
    }

    /** Clear all cells and reset highlights/metrics. */
    private void clearGrid() {
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++)
                cells[r][c].setText("");
        metricsLabel.setText(" ");
        status("Cleared.");
        clearHighlights();
    }

    /** Restore default coloring for all cells. */
    private void clearHighlights() {
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++)
                setDefaultCellColors(cells[r][c], r, c);
    }

    /** Alternating subtle backgrounds for 3×3 blocks; standard black text. */
    private void setDefaultCellColors(JTextField tf, int r, int c) {
        tf.setBackground(((r/3 + c/3) % 2 == 0) ? BG_A : BG_B);
        tf.setForeground(Color.BLACK);
    }

    /**
     * Highlight all cells that participate in a conflict (row/col/box duplicate).
     * Strategy:
     *  - For each unit (row/col/box), track seen digits.
     *  - On a duplicate, mark *all* cells in the unit that contain that digit.
     * This gives users immediate visual feedback on all offending entries.
     */
    private void highlightConflicts(char[][] b) {
        boolean[][] bad = new boolean[9][9];

        // Rows: mark all occurrences of any duplicated digit
        for (int r = 0; r < 9; r++) {
            int[] seen = new int[10];
            for (int c = 0; c < 9; c++) {
                char ch = b[r][c];
                if (ch >= '1' && ch <= '9') {
                    int d = ch - '0';
                    if (seen[d] == 1) {
                        for (int k = 0; k < 9; k++) if (b[r][k] == ch) bad[r][k] = true;
                    } else seen[d] = 1;
                }
            }
        }
        // Columns
        for (int c = 0; c < 9; c++) {
            int[] seen = new int[10];
            for (int r = 0; r < 9; r++) {
                char ch = b[r][c];
                if (ch >= '1' && ch <= '9') {
                    int d = ch - '0';
                    if (seen[d] == 1) {
                        for (int k = 0; k < 9; k++) if (b[k][c] == ch) bad[k][c] = true;
                    } else seen[d] = 1;
                }
            }
        }
        // 3×3 boxes
        for (int br = 0; br < 3; br++) {
            for (int bc = 0; bc < 3; bc++) {
                int[] seen = new int[10];
                for (int dr = 0; dr < 3; dr++) {
                    for (int dc = 0; dc < 3; dc++) {
                        int r = 3*br + dr, c = 3*bc + dc;
                        char ch = b[r][c];
                        if (ch >= '1' && ch <= '9') {
                            int d = ch - '0';
                            if (seen[d] == 1) {
                                for (int rr = 0; rr < 3; rr++)
                                    for (int cc = 0; cc < 3; cc++) {
                                        int R = 3*br + rr, C = 3*bc + cc;
                                        if (b[R][C] == ch) bad[R][C] = true;
                                    }
                            } else seen[d] = 1;
                        }
                    }
                }
            }
        }

        // Apply highlight
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++)
                if (bad[r][c]) {
                    cells[r][c].setBackground(BAD_BG);
                    cells[r][c].setForeground(BAD_FG);
                }
    }

    /** Extract the current grid from the UI. Non-digits are treated as blanks '.'. */
    private char[][] getBoard() {
        char[][] b = new char[9][9];
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                String t = cells[r][c].getText().trim();
                if (t.isEmpty()) b[r][c] = '.';
                else {
                    char ch = t.charAt(0);
                    b[r][c] = (ch == '0' || ch == '.' || ch == ' ') ? '.' : ch;
                }
            }
        }
        return b;
    }

    /** Push a board to the UI ('.' renders as empty). */
    private void setBoard(char[][] b) {
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++)
                cells[r][c].setText(b[r][c] == '.' ? "" : Character.toString(b[r][c]));
    }

    /**
     * Check constraints on givens only (no inference):
     *  - return false immediately on first duplicate in any row/col/box
     *  - uses tiny int[10] presence tables per unit
     */
    private boolean isBoardConsistent(char[][] b) {
        int r, c;
        // Rows
        for (r = 0; r < 9; r++) {
            int[] seen = new int[10];
            for (c = 0; c < 9; c++) {
                char ch = b[r][c];
                if (ch >= '1' && ch <= '9') {
                    int d = ch - '0';
                    if (seen[d] != 0) return false;
                    seen[d] = 1;
                }
            }
        }
        // Columns
        for (c = 0; c < 9; c++) {
            int[] seen = new int[10];
            for (r = 0; r < 9; r++) {
                char ch = b[r][c];
                if (ch >= '1' && ch <= '9') {
                    int d = ch - '0';
                    if (seen[d] != 0) return false;
                    seen[d] = 1;
                }
            }
        }
        // 3×3 boxes
        for (int br = 0; br < 3; br++)
            for (int bc = 0; bc < 3; bc++) {
                int[] seen = new int[10];
                for (int dr = 0; dr < 3; dr++)
                    for (int dc = 0; dc < 3; dc++) {
                        r = 3*br + dr; c = 3*bc + dc;
                        char ch = b[r][c];
                        if (ch >= '1' && ch <= '9') {
                            int d = ch - '0';
                            if (seen[d] != 0) return false;
                            seen[d] = 1;
                        }
                    }
            }
        return true;
    }

    /** Launch GUI on the Event Dispatch Thread (EDT). */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SudukoGUI().setVisible(true));
    }
}
