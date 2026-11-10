package com.aquariverr.fluxod.miniature.model;

import java.math.BigInteger;
import java.util.Arrays;

public final class QuantityCodec {

    public static byte[] encode(BigInteger quantity) {
        byte[] signed = FactoryLimits.requireQuantity(quantity).toByteArray();
        return signed.length > 1 && signed[0] == 0
                ? Arrays.copyOfRange(signed, 1, signed.length)
                : signed;
    }

    public static BigInteger decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_QUANTITY,
                    "Encoded quantity must contain at least one byte");
        }
        if (encoded.length > FactoryLimits.MAX_QUANTITY_BYTES) {
            throw new ModelValidationException(FactoryDiagnostic.Code.QUANTITY_LIMIT_EXCEEDED,
                    "Encoded quantity exceeds " + FactoryLimits.MAX_QUANTITY_BYTES + " bytes");
        }
        return new BigInteger(1, encoded);
    }

    private QuantityCodec() {}
}
