package com.aquariverr.fluxod.multiblock;

final class QuantumBatteryCollisionMath {

    private static final double EPSILON = 1.0E-6;

    static Push ringPush(double x, double y, double z, double normalX, double normalY, double normalZ,
                         double radius, double tubeRadius, double entityRadius) {
        double axial = x * normalX + y * normalY + z * normalZ;
        double radialX = x - normalX * axial;
        double radialY = y - normalY * axial;
        double radialZ = z - normalZ * axial;
        double radialLength = Math.sqrt(radialX * radialX + radialY * radialY + radialZ * radialZ);
        if (radialLength < EPSILON) return Push.ZERO;

        double radialDirectionX = radialX / radialLength;
        double radialDirectionY = radialY / radialLength;
        double radialDirectionZ = radialZ / radialLength;
        double offsetX = x - radialDirectionX * radius;
        double offsetY = y - radialDirectionY * radius;
        double offsetZ = z - radialDirectionZ * radius;
        double distance = Math.sqrt(offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ);
        double penetration = tubeRadius + entityRadius - distance;
        if (penetration <= 0.0) return Push.ZERO;

        double directionX = distance < EPSILON ? radialDirectionX : offsetX / distance;
        double directionY = distance < EPSILON ? radialDirectionY : offsetY / distance;
        double directionZ = distance < EPSILON ? radialDirectionZ : offsetZ / distance;
        return new Push(directionX * penetration, directionY * penetration, directionZ * penetration);
    }

    record Push(double x, double y, double z) {
        private static final Push ZERO = new Push(0.0, 0.0, 0.0);

        double lengthSqr() {
            return x * x + y * y + z * z;
        }
    }

    private QuantumBatteryCollisionMath() {}
}
