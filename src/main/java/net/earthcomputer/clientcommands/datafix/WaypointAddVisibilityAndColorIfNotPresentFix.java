package net.earthcomputer.clientcommands.datafix;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class WaypointAddVisibilityAndColorIfNotPresentFix extends DataFix {

    private static final String VISIBILITY_KEY = "Visible";
    private static final String COLOR_KEY = "Color";

    private final boolean visible;
    private final int color;
    private final String name;

    public WaypointAddVisibilityAndColorIfNotPresentFix(Schema outputSchema, boolean visible, int color) {
        super(outputSchema, true);
        this.visible = visible;
        this.color = color;
        this.name = "WaypointAddVisibilityIfNotPresentFix_" + VISIBILITY_KEY + "=" + this.visible + "_" + COLOR_KEY + "=" + this.color + " for " + outputSchema.getVersionKey();
    }

    @Override
    protected TypeRewriteRule makeRule() {
        // TODO
        return null;
    }
}
