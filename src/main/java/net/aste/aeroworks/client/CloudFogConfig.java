package net.aste.aeroworks.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CloudFogConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue VISIBILITY;
    public static final ModConfigSpec.DoubleValue BOUNDARY_WIDTH;
    public static final ModConfigSpec.DoubleValue TRANSITION_TICKS;
    public static final ModConfigSpec.DoubleValue DENSITY_OFFSET;
    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("cloud_fog");
        ENABLED = builder.comment("Туман внутри объёма Simple Clouds.").define("enabled", true);
        VISIBILITY = builder.comment("Дальность полной непрозрачности тумана в блоках внутри плотного облака.")
                .defineInRange("visibility", 24.0, 2.0, 256.0);
        BOUNDARY_WIDTH = builder.comment("Плавность границы по плотности шума облака. Больше = более мягкая граница.")
                .defineInRange("boundary_width", .15, .01, 2.0);
        DENSITY_OFFSET = builder.comment("Поправка чувствительности: положительное значение включает туман раньше, в разреженной кромке облака.",
                        "0 = штатная граница непрозрачного облака Simple Clouds. Например, 0.1 усиливает чувствительность.")
                .defineInRange("density_offset", 0.0, -1.0, 1.0);
        TRANSITION_TICKS = builder.comment("Время сглаживания входа/выхода в тиках. 0 = мгновенно.")
                .defineInRange("transition_ticks", 3.0, 0.0, 40.0);
        builder.pop();
        SPEC = builder.build();
    }
    private CloudFogConfig() {}
}
