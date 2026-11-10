package com.aquariverr.fluxod.miniature.compile;

import java.math.BigInteger;
import com.aquariverr.fluxod.miniature.model.FactoryDiagnostic;

/** Exact, bounded rational arithmetic; no rounded quantity can enter a balanced layout. */
record BalanceFraction(BigInteger numerator, BigInteger denominator) implements Comparable<BalanceFraction> {
    static final BalanceFraction ZERO = of(0), ONE = of(1);
    BalanceFraction {
        if (denominator.signum() == 0) throw new ArithmeticException("Zero denominator");
        if (denominator.signum() < 0) { numerator = numerator.negate(); denominator = denominator.negate(); }
        BigInteger gcd = numerator.gcd(denominator);
        numerator = numerator.divide(gcd); denominator = denominator.divide(gcd);
        if (numerator.bitLength() > 4096 || denominator.bitLength() > 4096) throw FactoryCompiler.rejected(
                FactoryDiagnostic.Code.BALANCE_SEARCH_LIMIT, null, null, null, null);
    }
    static BalanceFraction of(long value) { return of(BigInteger.valueOf(value)); }
    static BalanceFraction of(BigInteger value) { return new BalanceFraction(value, BigInteger.ONE); }
    boolean zero() { return numerator.signum() == 0; }
    BalanceFraction add(BalanceFraction other) {
        if (zero()) return other; if (other.zero()) return this;
        return new BalanceFraction(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)), denominator.multiply(other.denominator));
    }
    BalanceFraction negate() { return zero() ? this : new BalanceFraction(numerator.negate(), denominator); }
    BalanceFraction subtract(BalanceFraction other) { return add(other.negate()); }
    BalanceFraction multiply(BalanceFraction other) {
        return zero() || other.zero() ? ZERO : new BalanceFraction(numerator.multiply(other.numerator), denominator.multiply(other.denominator));
    }
    BalanceFraction divide(BalanceFraction other) { return multiply(new BalanceFraction(other.denominator, other.numerator)); }
    @Override public int compareTo(BalanceFraction other) { return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator)); }
}
