package com.aquariverr.fluxod.miniature.compile;

import org.junit.jupiter.api.Test;
import java.util.*;
import com.aquariverr.fluxod.miniature.model.*;
import static com.aquariverr.fluxod.miniature.compile.BalanceFraction.*;
import static org.junit.jupiter.api.Assertions.*;

class PositiveBalanceSolverTest {
    @Test void denseUnderdeterminedSystemsKeepAllSourcesPositiveWithoutRounding() {
        var random = new Random(1703);
        for (int test = 0; test < 100; test++) {
            int n = 3 + random.nextInt(12);
            int[] witness = random.ints(n, 1, 31).toArray();
            var rows = new ArrayList<BalanceFraction[]>();
            for (int r = 0; r < 1 + test % (n - 2); r++) {
                var row = new BalanceFraction[n]; BalanceFraction total = ZERO;
                for (int i = 0; i < n - 1; i++) {
                    row[i] = of(random.nextInt(41) - 20);
                    total = total.add(row[i].multiply(of(witness[i])));
                }
                row[n - 1] = total.negate().divide(of(witness[n - 1])); rows.add(row);
            }
            Collections.shuffle(rows, random);
            var solver = new PositiveBalanceSolver(n);
            for (var row : rows) solver.add(row, new GridPosition(0, 0), FactoryCompilerTest.ORE);
            var result = solver.solve();
            for (var value : result) assertTrue(value.compareTo(ZERO) > 0);
            for (var row : rows) {
                BalanceFraction actual = ZERO;
                for (int i = 0; i < n; i++) actual = actual.add(row[i].multiply(result[i]));
                assertEquals(ZERO, actual);
            }
        }
    }

    @Test void exactPositiveSolutionsSatisfyGeneratedIndependentAndRedundantConstraints() {
        var random = new Random(718);
        for (int test = 0; test < 40; test++) {
            int n = 2 + random.nextInt(10); var solver = new PositiveBalanceSolver(n);
            int[] expected = random.ints(n, 1, 100).toArray(); var rows = new ArrayList<BalanceFraction[]>();
            for (int i = 1; i < n; i++) {
                var row = new BalanceFraction[n]; Arrays.fill(row, ZERO);
                row[0] = of(expected[i]); row[i] = of(-expected[0]); rows.add(row);
            }
            Collections.shuffle(rows, random);
            for (var row : rows) { solver.add(row, new GridPosition(0, 0), FactoryCompilerTest.ORE); solver.add(row, new GridPosition(0, 0), FactoryCompilerTest.ORE); }
            var solution = solver.solve();
            for (var value : solution) assertTrue(value.compareTo(ZERO) > 0);
            for (var row : rows) {
                BalanceFraction sum = ZERO;
                for (int i = 0; i < n; i++) sum = sum.add(row[i].multiply(solution[i]));
                assertEquals(ZERO, sum);
            }
        }
    }

    @Test void underdeterminedPositiveBalanceAndImpossiblePositiveMass() {
        var solver = new PositiveBalanceSolver(3);
        solver.add(new BalanceFraction[]{ONE, ONE, of(-3)}, new GridPosition(0, 0), FactoryCompilerTest.ORE);
        var result = solver.solve();
        assertEquals(result[2].multiply(of(3)), result[0].add(result[1]));
        for (var value : result) assertTrue(value.compareTo(ZERO) > 0);
        var impossible = new PositiveBalanceSolver(2);
        impossible.add(new BalanceFraction[]{ONE, ONE}, new GridPosition(0, 0), FactoryCompilerTest.ORE);
        assertEquals(FactoryDiagnostic.Code.BALANCE_IMPOSSIBLE, assertThrows(ModelValidationException.class, impossible::solve).diagnostic().code());
    }
}
