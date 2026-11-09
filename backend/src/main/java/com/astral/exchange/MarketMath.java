package com.astral.exchange;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** All prices and account arithmetic use decimal values, including AMM execution. */
public final class MarketMath {
    public static final BigDecimal FEE = new BigDecimal("0.0005");
    public static final BigDecimal MAINTENANCE = new BigDecimal("0.005");
    public static final BigDecimal CAPITAL = new BigDecimal("100000");
    public static final BigDecimal ZERO = BigDecimal.ZERO;
    public static BigDecimal d(String value) { return new BigDecimal(value); }
    public static BigDecimal div(BigDecimal a, BigDecimal b) { return a.divide(b, 10, RoundingMode.HALF_UP); }
    public record Fill(BigDecimal base, BigDecimal quote, BigDecimal quantity, BigDecimal notional, BigDecimal price) {}
    public static Fill open(BigDecimal base, BigDecimal quote, BigDecimal notional, boolean buy) {
        if (notional.signum() <= 0 || notional.compareTo(quote.multiply(d("0.01"))) > 0)
            throw new IllegalArgumentException("单笔规模不得超过做市池的 1%");
        BigDecimal nextQuote = buy ? quote.add(notional) : quote.subtract(notional);
        BigDecimal nextBase = div(base.multiply(quote), nextQuote);
        BigDecimal qty = nextBase.subtract(base).abs();
        return new Fill(nextBase, nextQuote, qty, notional, div(notional, qty));
    }
    public static Fill close(BigDecimal base, BigDecimal quote, BigDecimal quantity, boolean buy) {
        BigDecimal nextBase = buy ? base.subtract(quantity) : base.add(quantity);
        if (nextBase.signum() <= 0) throw new IllegalStateException("做市池流动性不足");
        BigDecimal nextQuote = div(base.multiply(quote), nextBase);
        BigDecimal notional = nextQuote.subtract(quote).abs();
        return new Fill(nextBase, nextQuote, quantity, notional, div(notional, quantity));
    }
    public static BigDecimal pnl(String side, BigDecimal qty, BigDecimal entry, BigDecimal price) {
        return price.subtract(entry).multiply(qty).multiply(d(side.equals("LONG") ? "1" : "-1"));
    }
    public static BigDecimal liquidation(String side, BigDecimal qty, BigDecimal entry, BigDecimal margin) {
        BigDecimal perUnit = div(margin, qty);
        return side.equals("LONG") ? div(entry.subtract(perUnit), BigDecimal.ONE.subtract(MAINTENANCE))
                : div(entry.add(perUnit), BigDecimal.ONE.add(MAINTENANCE));
    }
    private MarketMath() {}
}
