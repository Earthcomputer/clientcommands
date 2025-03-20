package net.earthcomputer.clientcommands.datafix;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class WaypointAddVisibilityAndColourIfNotPresentFix extends DataFix {

    private static final String VISIBILITY_KEY = "Visible";
    private static final String COLOUR_KEY = "Colour";

    private final boolean visible;
    private final int colour;
    private final String name;

    public WaypointAddVisibilityAndColourIfNotPresentFix(Schema outputSchema, boolean visible, int colour) {
        super(outputSchema, true);
        this.visible = visible;
        this.colour = colour;
        this.name = "WaypointAddVisibilityIfNotPresentFix_" + VISIBILITY_KEY + "=" + this.visible + "_" + COLOUR_KEY + "=" + this.colour + " for " + outputSchema.getVersionKey();
    }

    @Override
    protected TypeRewriteRule makeRule() {
        // TODO
        return null;
    }
}
