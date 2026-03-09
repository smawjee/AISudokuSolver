
# AI Sudoku Solver 🧩

A Java-based Sudoku solver that uses **Backtracking with the Minimum Remaining Values (MRV) heuristic** to efficiently solve Sudoku puzzles.
The project includes both a **command-line solver** and a **Swing-based graphical user interface (GUI)** for loading and solving puzzles interactively.

---

## Features

* ✔ Solves standard **9×9 Sudoku puzzles**
* ✔ Uses **Backtracking search with MRV heuristic**
* ✔ Supports puzzle input from **.txt** and **.csv** files
* ✔ Provides **runtime metrics**:

  * Execution time
  * Number of assignments
  * Number of backtracks
  * Maximum recursion depth
* ✔ Includes a **Java Swing GUI**
* ✔ Detects invalid Sudoku grids before solving
* ✔ Highlights conflicts in the GUI

---

## Algorithm

The solver uses **Depth-First Search with Backtracking**.

### Minimum Remaining Values (MRV)

At each step, the algorithm selects the empty cell with the **fewest legal values** remaining.
This reduces branching in the search tree and detects dead-ends earlier.

Steps:

1. Validate the starting grid.
2. Select the most constrained empty cell using MRV.
3. Try digits **1–9** that satisfy Sudoku constraints.
4. Recursively continue solving.
5. Backtrack if a dead-end is reached.

---

## Project Structure

```
sudoku/
│
├── SudokuCW1.java      # Core Sudoku solver
├── SudukoGUI.java      # Swing graphical interface
│
├── easy.txt            # Example puzzle
├── medium.txt
├── hard.txt
└── easy.csv            # CSV puzzle example
```

---

## Running the Solver (Command Line)

Compile:

```
javac sudoku/SudokuCW1.java
```

Run with a puzzle file:

```
java sudoku.SudokuCW1 puzzle.txt
```

or

```
java sudoku.SudokuCW1 puzzle.csv
```

If no argument is provided, the program will prompt for a file path.

---

## Running the GUI

Compile the project and run:

```
java sudoku.SudukoGUI
```

### GUI Controls

| Button   | Description                               |
| -------- | ----------------------------------------- |
| Load     | Load a puzzle file (.txt or .csv)         |
| Validate | Check if the current puzzle has conflicts |
| Solve    | Run the solver                            |
| Clear    | Reset the grid                            |

Conflicting cells will be **highlighted in red**.

---

## Input File Format

### TXT format

```
5 3 . . 7 . . . .
6 . . 1 9 5 . . .
. 9 8 . . . . 6 .
8 . . . 6 . . . 3
4 . . 8 . 3 . . 1
7 . . . 2 . . . 6
. 6 . . . . 2 8 .
. . . 4 1 9 . . 5
. . . . 8 . . 7 9
```

`.` or `0` represents empty cells.

### CSV format

```
5,3,.,.,7,.,.,.,.
6,.,.,1,9,5,.,.,.
.,9,8,.,.,.,.,6,.
```

---

## Performance Metrics

After solving, the solver prints:

* **Execution time (ms)**
* **Assignments** (number of values written)
* **Backtracks**
* **Maximum recursion depth**

Example:

```
Solved? true

Metrics -> Time(ms): 3
Assignments: 52
Backtracks: 11
MaxDepth: 25
```

---

## Technologies Used

* **Java**
* **Java Swing (GUI)**
* **Backtracking search**
* **Constraint satisfaction techniques (MRV)**

---

## Future Improvements

* Least Constraining Value (LCV) heuristic
* Forward Checking
* Arc Consistency (AC-3)
* Puzzle generator
* Export solved puzzles

---

## Author

**Shakir Mawjee**

