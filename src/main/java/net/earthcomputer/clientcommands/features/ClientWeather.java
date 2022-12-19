package net.earthcomputer.clientcommands.features;

public class ClientWeather {

    private static float rain = -1;
    private static float thunder = -1;

    public static float getRain() {
        return rain;
    }

    public static void setRain(float rain) {
        ClientWeather.rain = rain;
    }

    public static float getThunder() {
        return thunder;
    }

    public static void setThunder(float thunder) {
        ClientWeather.thunder = thunder;
    }

}
