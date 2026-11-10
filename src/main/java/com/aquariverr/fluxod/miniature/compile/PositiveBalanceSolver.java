package com.aquariverr.fluxod.miniature.compile;

import com.aquariverr.fluxod.miniature.model.*;
import java.util.*;
import static com.aquariverr.fluxod.miniature.compile.BalanceFraction.*;

/** Solves homogeneous recipe constraints with every source and machine strictly positive. */
final class PositiveBalanceSolver {
    private final int variables;
    private final NavigableMap<Integer, BalanceFraction[]> equations = new TreeMap<>();
    private final Map<Integer, FactoryDiagnostic> origins = new HashMap<>();
    private final Budget budget;
    static final class Budget {
        private int remaining = 4_000_000;
        void use(int work) { remaining -= work; if (remaining < 0) throw limit(); }
    }

    PositiveBalanceSolver(int variables) { this(variables, new Budget()); }
    PositiveBalanceSolver(int variables, Budget budget) { this.variables = variables; this.budget = budget; }

    void add(BalanceFraction[] equation, GridPosition position, ResourceKey resource) {
        var row = equation.clone();
        for (var entry : equations.entrySet()) {
            int pivot = entry.getKey();
            if (!row[pivot].zero()) subtract(row, entry.getValue(), row[pivot]);
        }
        for (int column = 0; column < variables; column++) if (!row[column].zero()) {
            BalanceFraction divisor = row[column];
            for (int j = column; j < variables; j++) row[j] = row[j].divide(divisor);
            equations.put(column, row);
            origins.put(column, new FactoryDiagnostic(FactoryDiagnostic.Code.BALANCE_IMPOSSIBLE, Optional.of(position),
                    Optional.of(resource), Optional.empty(), Optional.empty()));
            return;
        }
    }

    BalanceFraction[] solve() {
        int constraints = equations.size(), width = variables + constraints + 1, rhs = width - 1;
        var tableau = new BalanceFraction[constraints + 1][width];
        for (var row : tableau) Arrays.fill(row, ZERO);
        int[] basis = new int[constraints];
        int index = 0;
        var errors = new ArrayList<FactoryDiagnostic>();
        // x = 1 + y, y >= 0; one artificial variable initially satisfies each equation.
        for (var entry : equations.entrySet()) {
            var row = tableau[index]; BalanceFraction sum = ZERO;
            for (int j = 0; j < variables; j++) { row[j] = entry.getValue()[j]; sum = sum.add(row[j]); }
            row[rhs] = sum.negate();
            if (row[rhs].compareTo(ZERO) < 0) for (int j = 0; j < width; j++) row[j] = row[j].negate();
            basis[index] = variables + index; row[basis[index]] = ONE;
            // Maximize negative artificial mass. Reduced costs use the conventional -c row.
            for (int j = 0; j < variables; j++) tableau[constraints][j] = tableau[constraints][j].subtract(row[j]);
            tableau[constraints][rhs] = tableau[constraints][rhs].subtract(row[rhs]);
            errors.add(origins.get(entry.getKey())); index++;
        }
        for (int iteration = 0; ; iteration++) {
            if (iteration >= 2048) throw limit();
            int entering = -1;
            for (int j = 0; j < rhs; j++) if (tableau[constraints][j].compareTo(ZERO) < 0) { entering = j; break; }
            if (entering < 0) break;
            int leaving = -1; BalanceFraction ratio = null;
            for (int i = 0; i < constraints; i++) if (tableau[i][entering].compareTo(ZERO) > 0) {
                BalanceFraction candidate = tableau[i][rhs].divide(tableau[i][entering]);
                if (ratio == null || candidate.compareTo(ratio) < 0 || candidate.equals(ratio) && basis[i] < basis[leaving]) {
                    leaving = i; ratio = candidate;
                }
            }
            if (leaving < 0) throw limit(); // Phase I is bounded above by zero.
            BalanceFraction divisor = tableau[leaving][entering];
            for (int j = 0; j < width; j++) tableau[leaving][j] = tableau[leaving][j].divide(divisor);
            for (int i = 0; i <= constraints; i++) if (i != leaving && !tableau[i][entering].zero())
                subtract(tableau[i], tableau[leaving], tableau[i][entering]);
            basis[leaving] = entering;
        }
        if (!tableau[constraints][rhs].zero()) {
            for (int i = 0; i < constraints; i++) if (basis[i] >= variables && !tableau[i][rhs].zero())
                throw new ModelValidationException(errors.get(i), "No positive material balance");
            throw new ModelValidationException(FactoryDiagnostic.Code.BALANCE_IMPOSSIBLE, "No positive material balance");
        }
        var result = new BalanceFraction[variables]; Arrays.fill(result, ONE);
        for (int i = 0; i < constraints; i++) if (basis[i] < variables) result[basis[i]] = ONE.add(tableau[i][rhs]);
        return result;
    }

    private void subtract(BalanceFraction[] target, BalanceFraction[] source, BalanceFraction multiplier) {
        budget.use(target.length);
        for (int j = 0; j < target.length; j++) if (!source[j].zero()) target[j] = target[j].subtract(source[j].multiply(multiplier));
    }
    private static ModelValidationException limit() { return new ModelValidationException(FactoryDiagnostic.Code.BALANCE_SEARCH_LIMIT, "Automatic balance work limit exceeded"); }
}
