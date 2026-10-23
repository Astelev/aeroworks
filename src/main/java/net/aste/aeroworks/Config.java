package net.aste.aeroworks;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    public static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

    public static final ModConfigSpec.BooleanValue WIND_ENABLED;
    public static final ModConfigSpec.DoubleValue WIND_X;
    public static final ModConfigSpec.DoubleValue WIND_Y;
    public static final ModConfigSpec.DoubleValue WIND_Z;
    public static final ModConfigSpec.DoubleValue SAIL_PRESSURE_COEFFICIENT;
    static {
        BUILDER.push("wind");
        WIND_ENABLED = BUILDER.comment("Серверный ветер для аэродинамических поверхностей Sable: парусов и крыльев.")
                .define("enabled", true);
        WIND_X = BUILDER.comment("Мировая скорость воздуха в блоках/сек. +X = на восток, -X = на запад.")
                .defineInRange("velocity_x", 8.0, -128.0, 128.0);
        WIND_Y = BUILDER.comment("Устаревший параметр: в текущей горизонтальной модели не используется.")
                .defineInRange("velocity_y", 0.0, -128.0, 128.0);
        WIND_Z = BUILDER.comment("Горизонтальная скорость воздуха: +Z на юг, -Z на север.")
                .defineInRange("velocity_z", 0.0, -128.0, 128.0);
        SAIL_PRESSURE_COEFFICIENT = BUILDER
                .comment("Множитель аэродинамического давления на паруса/крылья. 1 = прежняя сила, 0.1 = в десять раз слабее.",
                        "Масштабирует подъёмную силу, сопротивление и их моменты, включая полёт в неподвижном воздухе. Скорость ветра не меняется.")
                .defineInRange("sail_pressure_coefficient", 1.0, 0.0, 10.0);
        BUILDER.pop();
    }
    public static final ModConfigSpec.BooleanValue ATMOSPHERE_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ATMOSPHERE_DIMENSIONS;
    public static final ModConfigSpec.DoubleValue ATMOSPHERE_REFERENCE_HEIGHT;
    public static final ModConfigSpec.DoubleValue ATMOSPHERE_SCALE_HEIGHT;
    public static final ModConfigSpec.DoubleValue ATMOSPHERE_FADE_START;
    public static final ModConfigSpec.DoubleValue ATMOSPHERE_CEILING;
    static {
        BUILDER.push("atmosphere");
        ATMOSPHERE_ENABLED = BUILDER.comment("Заменить высотную кривую давления Sable; совместимо с Lift Patch.")
                .define("enabled", true);
        ATMOSPHERE_DIMENSIONS = BUILDER.comment("Измерения с новой атмосферой. Остальные сохраняют исходную кривую Sable.")
                .defineListAllowEmpty("dimensions", List.of("minecraft:overworld"), () -> "minecraft:overworld",
                        value -> value instanceof String id && ResourceLocation.tryParse(id) != null);
        ATMOSPHERE_REFERENCE_HEIGHT = BUILDER.comment("Мировая высота Y с относительной плотностью 1.")
                .defineInRange("reference_height", 64.0, -1000000.0, 1000000.0);
        ATMOSPHERE_SCALE_HEIGHT = BUILDER.comment("Высота подъёма, за которую плотность уменьшается в e раз. Больше = медленнее разрежение.")
                .defineInRange("scale_height", 1024.0, 1.0, 1000000.0);
        ATMOSPHERE_FADE_START = BUILDER.comment("Начало дополнительного плавного перехода к вакууму. Не ниже reference_height.")
                .defineInRange("fade_start_height", 3072.0, -1000000.0, 1000000.0);
        ATMOSPHERE_CEILING = BUILDER.comment("Высота полного вакуума. Должна быть выше fade_start_height.")
                .defineInRange("ceiling_height", 4096.0, -1000000.0, 1000000.0);
        BUILDER.pop();
    }
    public static final ModConfigSpec.BooleanValue WIND_DYNAMIC;
    public static final ModConfigSpec.DoubleValue WIND_ZONE_SIZE;
    public static final ModConfigSpec.DoubleValue WIND_SURFACE_HEIGHT;
    public static final ModConfigSpec.DoubleValue WIND_MAXIMUM_HEIGHT;
    public static final ModConfigSpec.DoubleValue WIND_SURFACE_CLEAR;
    public static final ModConfigSpec.DoubleValue WIND_SURFACE_RAIN;
    public static final ModConfigSpec.DoubleValue WIND_SURFACE_STORM;
    public static final ModConfigSpec.DoubleValue WIND_ALOFT_CLEAR;
    public static final ModConfigSpec.DoubleValue WIND_ALOFT_RAIN;
    public static final ModConfigSpec.DoubleValue WIND_ALOFT_STORM;
    public static final ModConfigSpec.DoubleValue WIND_FALLBACK_PERIOD;
    public static final ModConfigSpec.DoubleValue WIND_WEATHER_PERIOD_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WIND_TRANSITION_SECONDS;
    public static final ModConfigSpec.DoubleValue WIND_CLOUD_DEVIATION;
    static {
        BUILDER.push("wind").push("field");
        WIND_DYNAMIC = BUILDER.comment("Погодное горизонтальное поле ветра. false = прежние velocity_x/z; velocity_y не используется.")
                .define("dynamic", true);
        WIND_ZONE_SIZE = BUILDER.comment("Размер сетки плавных ветровых зон, в блоках.")
                .defineInRange("zone_size", 512.0, 16.0, 65536.0);
        WIND_SURFACE_HEIGHT = BUILDER.comment("Мировая Y поверхностного слоя. Не требует поиска поверхности/загрузки чанков.")
                .defineInRange("surface_height", 64.0, -1000000.0, 1000000.0);
        WIND_MAXIMUM_HEIGHT = BUILDER.comment("Мировая Y максимального высотного ветра; выше сила от высоты не растёт.")
                .defineInRange("maximum_height", 250.0, -999999.0, 1000001.0);
        WIND_SURFACE_CLEAR = BUILDER.comment("Средняя скорость у поверхности в ясную погоду, блоков/сек.")
                .defineInRange("surface_clear_speed", 2.5, 0.0, 128.0);
        WIND_SURFACE_RAIN = BUILDER.comment("Средняя скорость у поверхности при дожде.")
                .defineInRange("surface_rain_speed", 4.0, 0.0, 128.0);
        WIND_SURFACE_STORM = BUILDER.comment("Средняя скорость у поверхности при сильной грозе.")
                .defineInRange("surface_storm_speed", 6.0, 0.0, 128.0);
        WIND_ALOFT_CLEAR = BUILDER.comment("Средняя высотная скорость в ясную погоду.")
                .defineInRange("aloft_clear_speed", 8.0, 0.0, 128.0);
        WIND_ALOFT_RAIN = BUILDER.comment("Средняя высотная скорость при дожде.")
                .defineInRange("aloft_rain_speed", 12.0, 0.0, 128.0);
        WIND_ALOFT_STORM = BUILDER.comment("Средняя высотная скорость при сильной грозе.")
                .defineInRange("aloft_storm_speed", 18.0, 0.0, 128.0);
        WIND_FALLBACK_PERIOD = BUILDER.comment("Период случайных поворотов без расписания Simple Clouds, в секундах.")
                .defineInRange("fallback_direction_interval_seconds", 360.0, 10.0, 86400.0);
        WIND_WEATHER_PERIOD_MULTIPLIER = BUILDER.comment("Множитель периода появления массивов Simple Clouds для случайных поворотов ветра.")
                .defineInRange("weather_direction_interval_multiplier", 1.0, 0.01, 100.0);
        WIND_TRANSITION_SECONDS = BUILDER.comment("Время плавной реакции на новый облачный массив и погодные данные.")
                .defineInRange("transition_seconds", 15.0, 0.1, 600.0);
        WIND_CLOUD_DEVIATION = BUILDER.comment("Случайное отклонение высотного ветра от движения облаков; погодный поворот добавляет до половины этого угла.")
                .defineInRange("cloud_deviation_degrees", 15.0, 0.0, 45.0);
        BUILDER.pop().pop();
    }
    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
}
