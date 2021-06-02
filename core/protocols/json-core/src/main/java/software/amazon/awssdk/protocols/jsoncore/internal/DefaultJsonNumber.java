package software.amazon.awssdk.protocols.jsoncore.internal;

import java.math.BigDecimal;
import java.math.BigInteger;
import software.amazon.awssdk.protocols.jsoncore.JsonNumber;

public class DefaultJsonNumber implements JsonNumber {
    private final Number number;

    public DefaultJsonNumber(Number number) {
        this.number = number;
    }

    @Override
    public boolean isInteger() {
        return isInt() || isLong() || isBigInteger();
    }

    @Override
    public boolean isFloatingPoint() {
        return isDouble() || isBigDecimal();
    }

    public boolean isBigDecimal() {
        return number instanceof BigDecimal;
    }

    public boolean isBigInteger() {
        return number instanceof BigInteger;
    }

    public boolean isDouble() {
        return number instanceof Double;
    }

    public boolean isInt() {
        return number instanceof Integer;
    }

    public boolean isLong() {
        return number instanceof Long;
    }

    @Override
    public BigDecimal asBigDecimal() {
        if (isBigDecimal()) {
            return (BigDecimal) number;
        }

        if (isInt()) {
            return new BigDecimal(asInt());
        }
        if (isLong()) {
            return BigDecimal.valueOf(asLong());
        }
        if (isBigInteger()) {
            return new BigDecimal(asBigInteger());
        }

        return BigDecimal.valueOf(number.doubleValue());
    }

    @Override
    public BigInteger asBigInteger() {
        if (isBigInteger()) {
            return (BigInteger) number;
        }

        return BigInteger.valueOf(number.longValue());
    }

    @Override
    public double asDouble() {
        return number.doubleValue();
    }

    @Override
    public int asInt() {
        return number.intValue();
    }

    @Override
    public long asLong() {
        return number.longValue();
    }
}
