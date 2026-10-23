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
        WIND_Y = BUILDER.comment("Вертикальная скорость воздуха: +Y вверх, -Y вниз.")
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
    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
}
