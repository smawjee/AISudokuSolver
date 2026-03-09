package sudoku;

import java.io.*;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Coursework 1 Sudoku solver.
 *
 * - Representation: 9x9 char grid; '.' means empty (also accept '0'/' ' on input).
 * - Algorithm: Backtracking + MRV (choose the empty cell with the fewest legal values).
 * - Heuristics: MRV only (no LCV, no forward-checking/AC-3).
 * - Metrics: wall time, number of assignments, backtracks, and max recursion depth.
 *
 */

public class SudokuCW1 {
	
	// Simple Container for runtime statistics
	public static class Metrics{
		long startNs,endNs;
		int assignments;  // how many values we wrote to cells (including ones we later undo)
		int backtracks; // how many times we hit a dead-end and reverted a choice
		int maxdepth;  // deepest recursion level reached (search tree depth)
		long elapsedMs() { return (endNs - startNs) / 1_000_000; };
	}
	
	private final Metrics metrics = new Metrics();
	
	
	/**
     * Solve the given board in-place.
     * @param board 9x9 grid (digits '1'..'9' or '.' for empty; '0'/' ' also accepted and normalized)
     * @return true if a complete solution was found; false if the initial grid is invalid or unsolvable.
     */
	
	public boolean solveSudoku(char[][] board) {
		
		// Normalise inputs (treat '0' or ' ' as '.')
		normaliseZeroesToDots(board);
		
		// Quick sanity check: the givens must not already violate Sudoku rules.
		if (!isValidSudoku(board)) return false;
		
		// reset per-run metrics
	    metrics.assignments = 0;
	    metrics.backtracks  = 0;
	    metrics.maxdepth    = 0;

		
		long t0 = System.nanoTime();
		boolean solved = backtrack(board,0);
		long t1 = System.nanoTime();
		 getMetrics().startNs = t0; getMetrics().endNs = t1;
		 return solved;
		
		
	}
	
	/**
    * Depth-first backtracking with MRV cell selection.
    * @param board grid (mutated in-place)
    * @param depth current recursion depth
    */

	private boolean backtrack(char[][] board, int depth) {
		
		// Track the deepest level we reach.
	    getMetrics().maxdepth = Math.max(getMetrics().maxdepth, depth);

	    // --- MRV: pick the most constrained empty cell ---
	    int[] cell = findMRVCell(board);
	    if (cell == null) return true; // If there are no empty cells the puzzle is solved.

	    int r = cell[0], c = cell[1];

	    
	    for (char d = '1'; d <= '9'; d++) {
	        if (isValid(board, r, c, d)) {
	            board[r][c] = d;
	            getMetrics().assignments++; // Count Assignments
	            
	            if (backtrack(board, depth + 1)) return true; // Propagate success
	            
	            // Undo the backtrack
	            board[r][c] = '.';
	            getMetrics().backtracks++;
	        }
	    }
	    return false; // dead end
	}
	
	/**
	 * Find the next empty cell using the MRV (Minimum Remaining Values) heuristic.
	 *
	 * Idea:
	 *   Among all '.' cells, choose the one with the *smallest* number of legal digits.
	 *   This pushes the search toward the most constrained spots first, reducing
	 *   branching and early-detecting dead-ends.
	 *
	 * Details:
	 *   - We scan the whole grid (<=81 cells). For each '.' we call countPossible(),
	 *     which tries digits '1'..'9' and checks isValid(). That’s at most 9*9 checks,
	 *     each is O(9) (row/col/box loops), so worst-case work is trivial for 9x9.
	 *   - Early exit: if we find a cell with domain size 1, we return it immediately
	 *     (you can prove this is optimal for MRV).
	 *   - Tie-breaking: we keep the *first* minimum we see. If you want a stronger
	 *     tie-break, plug in a “degree heuristic” (prefer the cell that constrains
	 *     the most neighbors) or later apply LCV ordering on values.
	 */
	
	private int[] findMRVCell(char[][] board) {
		int min = 10;
		int best[] = null;
		for (int r = 0; r < 9; r++) {
	        for (int c = 0; c < 9; c++) {
	            if (board[r][c] == '.') {
	            	int count = countPossible(board, r, c); // domain size for (r,c)
	            	if (count < min) {
	            		min = count; 
	            		best = new int [] {r,c};
	            		
	            		// Perfect MRV: if only one value fits, no need to keep scanning.
	            		if (min == 1) {
	            			return best;
	            		}
	            		}
	            	
	            }      
	}
		}
		return best;
	}
	private int countPossible(char[][] board, int r, int c){
		int count = 0;
		 for (char d = '1'; d <= '9'; d++) if (isValid(board, r, c, d)) count++;
		    return count;
	}
	
	
    private boolean isValid(char[][] board, int row, int col, char d) {
        for (int i = 0; i < 9; i++) {
            if (board[row][i] == d) return false; // row clash
            if (board[i][col] == d) return false; // col clash
            int br = 3 * (row / 3) + i / 3;
            int bc = 3 * (col / 3) + i % 3;
            if (board[br][bc] == d) return false; // box clash
        }
        return true;
    }
    
	/**
	 * Validate the *starting grid* (givens only).
	 *
	 * What it guarantees:
	 *   - No digit repeats in any row, column, or 3x3 box among the pre-filled cells.
	 *   - Empty markers '.', '0', and ' ' are ignored.
	 *
	 * How it works:
	 *   - For each non-empty cell (r,c) with value v, we build three keys:
	 *       "v in row r", "v in col c", "v in box br-bc"
	 *     where br = r/3 and bc = c/3 select the 3x3 block. If any key was seen
	 *     before, then there’s a duplicate and the grid is invalid.
	 *
	 */

	private static boolean isValidSudoku(char[][] board) {
		
	    Set<String> seen = new HashSet<>();
	    for (int i = 0; i < 9; i++) {
	        for (int j = 0; j < 9; j++) {
	            char curr = board[i][j];
	            if (curr == '.' || curr == ' ' || curr == '0') continue; // Ignore Blanks

	            String rowKey  = curr + " in row " + i;
	            String colKey  = curr + " in col " + j;
	            String boxKey  = curr + " in box " + (i / 3) + "-" + (j / 3);

	         // Any duplicate across row/col/box means invalid givens.
	            if (!seen.add(rowKey) || !seen.add(colKey) || !seen.add(boxKey)) {
	                return false;
	            }
	        }
	    }
	    return true;
	}
	
	/** Normalize any '0' or space to '.' so the solver treats them as empty. */
	private void normaliseZeroesToDots(char [][] board){
		for (int r = 0; r < 9; r++) {
			for (int c = 0; c < 9; c++) {
				if (board [r][c] == '0' || board [r][c] == ' ') board [r][c] = '.';
			}
		}
	}
	
	/** Human-friendly printing with 3x3 block separators. */
	private static void printBoard(char[][] board) {
		for (int r = 0; r < 9; r++) {
            if (r == 3 || r == 6) System.out.println("------+-------+------");
            for (int c = 0; c < 9; c++) {
            	if (c == 3 || c == 6) System.out.print("| ");
            	System.out.print(board[r][c] + " ");
            }
            System.out.println();
		}
	}

	/**
     * Dispatch to a reader based on extension (.txt or .csv).
     * TXT supports either 9 tokens per line or free-form with digits/0/./spaces.
     * CSV requires exactly 9 comma-separated entries per row.
     */
	public static char[][] loadFromFile(String path) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".csv")) return readCSV(br);
            if (lower.endsWith(".txt")) return readTXT(br);
            throw new IOException("Unsupported file type (use .txt or .csv): " + path);
        }
    }

	/**
     * TXT reader:
     *  - Case A: line has 9 whitespace-separated tokens.
     *  - Case B: scan characters; keep digits; map 0/./space to '.'.
     * Throws on malformed rows to surface input problems early.
     */
	private static char[][] readTXT(BufferedReader br) throws IOException {
        char[][] board = new char[9][9];
        String line;
        int r = 0;

        while (r < 9 && (line = br.readLine()) != null) {
            if (line.trim().isEmpty()) continue;

            // If line has 9 whitespace-separated tokens
            String[] toks = line.trim().split("\\s+");
            if (toks.length == 9) {
                for (int c = 0; c < 9; c++) {
                    char ch = toks[c].isEmpty() ? '.' : toks[c].charAt(0);
                    board[r][c] = (ch == '0' || ch == ' ') ? '.' : ch;
                }
                r++;
                continue;
            }

            // Otherwise scan characters: digits kept; 0/./SPACE → '.'
            StringBuilder row = new StringBuilder(9);
            for (int i = 0; i < line.length() && row.length() < 9; i++) {
                char ch = line.charAt(i);
                if (ch >= '1' && ch <= '9') row.append(ch);
                else if (ch == '0' || ch == '.' || ch == ' ') row.append('.');
            }

            if (row.length() != 9)
                throw new IOException("Invalid TXT row (need 9 cells): " + line);

            for (int c = 0; c < 9; c++) board[r][c] = row.charAt(c);
            r++;
        }
        if (r != 9) throw new IOException("Expected 9 rows, got " + r);
        return board;
    }

	 /**
     * CSV reader: exactly 9 comma-separated entries per row.
     * Empty cells and 0/space are treated as '.'.
     */
    private static char[][] readCSV(BufferedReader br) throws IOException {
        char[][] board = new char[9][9];
        String line;
        int r = 0;

        while (r < 9 && (line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            String[] cells = line.split("\\s*,\\s*");
            if (cells.length != 9)
                throw new IOException("Invalid CSV row (need 9 cells): " + line);

            for (int c = 0; c < 9; c++) {
                char ch = cells[c].isEmpty() ? '.' : cells[c].charAt(0);
                board[r][c] = (ch == '0' || ch == ' ') ? '.' : ch;
            }
            r++;
        }
        if (r != 9) throw new IOException("Expected 9 rows, got " + r);
        return board;
    }
    
    
    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                // Prompt so you can paste a path; no sample board
                System.out.print("Enter path to .txt or .csv: ");
                BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
                String p = in.readLine();
                if (p == null || p.trim().isEmpty()) {
                    System.err.println("No file path provided.");
                    return;
                }
                runOnce(p.trim());
            } else {
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) System.out.println("\n" + "=".repeat(36) + "\n");
                    runOnce(args[i]);
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("\nUsage: java sudoku.SudokuCW1 [puzzle1.txt|puzzle1.csv ...]");
        }
    }
    
    private static void runOnce(String path) throws IOException {
        char[][] board = loadFromFile(path);

        System.out.println("Input (" + path + "):");
        printBoard(board);

        SudokuCW1 solver = new SudokuCW1();
        boolean solved = solver.solveSudoku(board);

        System.out.println("\nSolved? " + solved);
        printBoard(board);

        Metrics m = solver.getMetrics();
        System.out.println("\nMetrics -> Time(ms): " + m.elapsedMs()
                + "  Assignments: " + m.assignments
                + "  Backtracks: " + m.backtracks
                + "  MaxDepth: " + m.maxdepth);
    }

	public Metrics getMetrics() {
		return metrics;
	}

    
}
