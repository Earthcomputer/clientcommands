package net.earthcomputer.clientcommands;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TntFinderManager {
    private static final Logger LOGGER = LogManager.getLogger("clientcommands");

    private static Vec3d x1 = null;
    private static Vec3d v1 = null;
    private static Vec3d x2 = null;
    private static Vec3d v2 = null;
    private static Vec3d x3 = null;
    private static Vec3d v3 = null;

    public static boolean set(Vec3d x, Vec3d v) {
        if (x1 == null || v1 == null) {
            x1 = x;
            v1 = v;
            return false;
        } else if (x2 == null || v2 == null) {
            x2 = x;
            v2 = v;
            return false;
        } else {
            x3 = x;
            v3 = v;
            return true;
        }
    }

    public static Vec3d triangulate() {
        Vec3d est1 = intersect(x1, v1, x2, v2);
        Vec3d est2 = intersect(x1, v1, x3, v3);
        Vec3d est3 = intersect(x2, v2, x3, v3);
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            LOGGER.debug('\n' + generateGraph());
        }
        reset();
        final Vec3d sum = est1.add(est2).add(est3);
        if (Double.isFinite(sum.x) && Double.isFinite(sum.z)) {
            return sum.multiply(1.0 / 3.0);
        }
        return new Vec3d(sum.x / 3.0, -1184951860, sum.z / 3.0);
    }

    private static Vec3d intersect(Vec3d x1, Vec3d v1, Vec3d x2, Vec3d v2) {
        final double slopeDiff = v1.x * v2.z - v1.z * v2.x;
        double x = (x1.z * v1.x * v2.x - x1.x * v1.z * v2.x - x2.z * v1.x * v2.x + x2.x * v1.x * v2.z) / slopeDiff;
        double z = (v1.z / v1.x) * (x - x1.x) + x1.z;
        return new Vec3d(x, 0, z);
    }

    public static void reset() {
        x1 = x2 = v1 = v2 = x3 = v3 = null;
    }

    private static String generateGraph() {
        return """
                <script src="https://www.desmos.com/api/v1.6/calculator.js?apiKey=dcb31709b452b1cf9dc26972add0fda6"></script>
                <div id="calculator" style="width: 100%%; height: 100%%;"></div>
                <script>
                  var elt = document.getElementById('calculator');
                  var calculator = Desmos.GraphingCalculator(elt);
                  calculator.setExpression({ id: 'A', latex: 'A=(%s,%s)' });
                  calculator.setExpression({ id: 'B', latex: 'B=(A.x+%s,A.y+%s)' });
                  calculator.setExpression({ id: 'graph1', latex: 'y=\\\\frac{B.y-A.y}{B.x-A.x}\\\\left(x-A.x\\\\right)+A.y' });
                  calculator.setExpression({ id: 'C', latex: 'C=(%s,%s)' });
                  calculator.setExpression({ id: 'D', latex: 'D=(C.x+%s,C.y+%s)' });
                  calculator.setExpression({ id: 'graph2', latex: 'y=\\\\frac{D.y-C.y}{D.x-C.x}\\\\left(x-C.x\\\\right)+C.y' });
                  calculator.setExpression({ id: 'E', latex: 'E=(%s,%s)' });
                  calculator.setExpression({ id: 'F', latex: 'F=(E.x+%s,E.y+%s)' });
                  calculator.setExpression({ id: 'graph3', latex: 'y=\\\\frac{F.y-E.y}{F.x-E.x}\\\\left(x-E.x\\\\right)+E.y' });
                </script>
                """.formatted(x1.x, x1.z, v1.x, v1.z, x2.x, x2.z, v2.x, v2.z, x3.x, x3.z, v3.x, v3.z);
    }
}
