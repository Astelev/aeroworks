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
        WIND_Y = BUILDER.comment("Устаревший параметр: не используется. Вертикальные потоки настраиваются в wind.vertical.")
                .defineInRange("velocity_y", 0.0, -128.0, 128.0);
        WIND_Z = BUILDER.comment("Горизонтальная скорость воздуха: +Z на юг, -Z на север.")
                .defineInRange("velocity_z", 0.0, -128.0, 128.0);
        SAIL_PRESSURE_COEFFICIENT = BUILDER
                .comment("Множитель аэродинамического давления на паруса/крылья. 1 = прежняя сила, 0.1 = в десять раз слабее.",
                        "Масштабирует подъёмную силу, сопротивление и их моменты, включая полёт в неподвижном воздухе. Скорость ветра не меняется.")
                .defineInRange("sail_pressure_coefficient", 1.0, 0.0, 10.0);
        BUILDER.pop();
    }
    public static final ModConfigSpec.BooleanValue CONSERVATIVE_AERODYNAMICS;
    public static final ModConfigSpec.BooleanValue STALL_PARTICLES;
    public static final ModConfigSpec.DoubleValue STALL_PARTICLE_MIN_SPEED;
    public static final ModConfigSpec.DoubleValue STALL_LIFT_TRANSITION;
    public static final ModConfigSpec.DoubleValue STALLED_LIFT_FRACTION;
    public static final ModConfigSpec.DoubleValue ATTACHED_FLOW_ANGLE;
    public static final ModConfigSpec.DoubleValue SEPARATED_FLOW_ANGLE;
    public static final ModConfigSpec.DoubleValue ATTACHED_FLOW_DRAG_FRACTION;
    static {
        BUILDER.push("aerodynamics");
        CONSERVATIVE_AERODYNAMICS=BUILDER.comment("Пассивная аэродинамика: подъёмная сила поперёк потока, малые углы уменьшают потери от нормального давления без ослабления рулей.",
                "Не нормирует скорость аппарата: подъёмная сила интегрируется с сохранением энергии, сопротивление — с её потерей.",
                "Сохраняет множители liftTuning/parallelDragTuning/directionlessDragTuning и ОБА soft cap Lift Patch.",
                "false возвращает исходные формулы Sable/Lift Patch.").define("conservative_surfaces",true);
        STALL_PARTICLES=BUILDER.comment("Белые завихрения у парусов выше первого углового порога; только визуальный эффект.").define("stall_particles",true);
        STALL_PARTICLE_MIN_SPEED=BUILDER.comment("Минимальная местная скорость потока для частиц срыва, м/с; не меняет силы.")
                .defineInRange("stall_particle_min_speed",10.0,0.0,256.0);
        STALL_LIFT_TRANSITION=BUILDER.comment("Ширина резкого плавного падения собственной подъёмной силы одностороннего крыла после первого углового порога, градусы.")
                .defineInRange("stall_lift_transition_degrees",5.0,0.1,90.0);
        STALLED_LIFT_FRACTION=BUILDER.comment("Остаточная доля собственной подъёмной силы после срыва (0..1).",
                "Не ослабляет нормальное давление и двухсторонние рули. 1 отключает падение lift.")
                .defineInRange("stalled_lift_fraction",0.1,0.0,1.0);
        ATTACHED_FLOW_ANGLE=BUILDER.comment("Угол потока к ПЛОСКОСТИ паруса в градусах: до него действует минимальное сопротивление нормального давления.")
                .defineInRange("attached_flow_angle_degrees",30.0,0.0,89.0);
        SEPARATED_FLOW_ANGLE=BUILDER.comment("Угол полного возврата прежнего сопротивления. Между порогами — плавный переход.",
                "Если не больше первого порога, используется первый порог + 0.1 градуса.")
                .defineInRange("separated_flow_angle_degrees",45.0,0.1,90.0);
        ATTACHED_FLOW_DRAG_FRACTION=BUILDER.comment("Доля прежнего сопротивления нормального давления на малых углах (0..1).",
                "Не меняет поперечную управляющую силу, общее сопротивление или soft cap. 1 возвращает прежнее сопротивление нормального давления; отдельное падение lift настраивается ниже.")
                .defineInRange("attached_flow_drag_fraction",0.1,0.0,1.0);
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
        WIND_TRANSITION_SECONDS = BUILDER.comment("Время примерно 95% плавной реакции на новый облачный массив и погодные данные.")
                .defineInRange("transition_seconds", 15.0, 0.1, 600.0);
        WIND_CLOUD_DEVIATION = BUILDER.comment("Общее случайное отклонение высотного ветра от движения облаков, в градусах.")
                .defineInRange("cloud_deviation_degrees", 15.0, 0.0, 45.0);
        BUILDER.pop().pop();
    }
    public static final ModConfigSpec.BooleanValue FLOW_ENABLED;
    public static final ModConfigSpec.DoubleValue FLOW_DIAMETER;
    public static final ModConfigSpec.DoubleValue FLOW_SPACING;
    public static final ModConfigSpec.DoubleValue FLOW_DRIFT;
    public static final ModConfigSpec.DoubleValue FLOW_PERIOD;
    public static final ModConfigSpec.DoubleValue FLOW_MIN_HEIGHT;
    public static final ModConfigSpec.DoubleValue FLOW_RELIEF;
    public static final ModConfigSpec.DoubleValue FLOW_HEIGHT;
    public static final ModConfigSpec.DoubleValue FLOW_SPEED;
    static {
        BUILDER.push("wind").push("vertical");
        FLOW_ENABLED = BUILDER.comment("Горные восходящие и нисходящие потоки.")
                .define("enabled", true);
        FLOW_DIAMETER = BUILDER.comment("Диаметр потока, блоков.")
                .defineInRange("diameter", 100.0, 16.0, 512.0);
        FLOW_SPACING = BUILDER.comment("Шаг поиска горных опорных точек.")
                .defineInRange("spacing", 128.0, 64.0, 2048.0);
        FLOW_DRIFT = BUILDER.comment("Радиус медленного смещения центра.")
                .defineInRange("drift_radius", 15.0, 0.0, 32.0);
        FLOW_PERIOD = BUILDER.comment("Период изменения положения и силы.")
                .defineInRange("drift_period_seconds", 120.0, 10.0, 3600.0);
        FLOW_MIN_HEIGHT = BUILDER.comment("Минимальная высота горной поверхности.")
                .defineInRange("minimum_mountain_height", 120.0, -64.0, 2048.0);
        FLOW_RELIEF = BUILDER.comment("Перепад рельефа; горные биомы допускаются и без перепада.")
                .defineInRange("minimum_relief", 20.0, 0.0, 512.0);
        FLOW_HEIGHT = BUILDER.comment("Средняя высота потока над поверхностью, меняется на 20%.")
                .defineInRange("column_height", 500.0, 60.0, 4096.0);
        FLOW_SPEED = BUILDER.comment("Максимальная вертикальная скорость воздуха, блоков/сек.")
                .defineInRange("maximum_vertical_speed", 4.0, 0.0, 128.0);
        BUILDER.pop().pop();
    }
    public static final ModConfigSpec.BooleanValue TURB_ENABLED;
    public static final ModConfigSpec.DoubleValue TURB_CHANCE;
    public static final ModConfigSpec.DoubleValue TURB_MIN_DISTANCE;
    public static final ModConfigSpec.DoubleValue TURB_MAX_DISTANCE;
    public static final ModConfigSpec.DoubleValue TURB_COOLDOWN;
    public static final ModConfigSpec.DoubleValue TURB_GUST;
    public static final ModConfigSpec.DoubleValue TURB_TORQUE;
    public static final ModConfigSpec.DoubleValue TURB_PERIOD;
    public static final ModConfigSpec.DoubleValue TURB_ROCKING_PERIOD;
    public static final ModConfigSpec.DoubleValue TURB_FADE;
    public static final ModConfigSpec.DoubleValue STORM_TURN;
    public static final ModConfigSpec.DoubleValue STORM_TURB;
    static {
        BUILDER.push("wind").push("turbulence");
        TURB_ENABLED = BUILDER.comment("Эпизоды турбулентности отдельных аппаратов в облаках.")
                .define("enabled", true);
        TURB_CHANCE = BUILDER.comment("Вероятность начала за 100 блоков пути внутри плотного облака.")
                .defineInRange("chance_per_100_blocks", 0.3, 0.0, 0.999);
        TURB_MIN_DISTANCE = BUILDER.comment("Минимальная длина эпизода по пройденному пути.")
                .defineInRange("minimum_distance", 100.0, 1.0, 100000.0);
        TURB_MAX_DISTANCE = BUILDER.comment("Максимальная длина эпизода, не ниже minimum_distance.")
                .defineInRange("maximum_distance", 300.0, 1.0, 100000.0);
        TURB_COOLDOWN = BUILDER.comment("Путь без новых эпизодов после завершения.")
                .defineInRange("cooldown_distance", 150.0, 0.0, 100000.0);
        TURB_GUST = BUILDER.comment("Максимальная добавочная скорость порыва.")
                .defineInRange("gust_speed", 3.5, 0.0, 128.0);
        TURB_TORQUE = BUILDER.comment("Вращающий момент на одну аэродинамическую поверхность; масштабируется давлением.")
                .defineInRange("rocking_torque_per_sail", 0.15, 0.0, 10000.0);
        TURB_PERIOD = BUILDER.comment("Интервал резкого плавного изменения направления порыва.")
                .defineInRange("gust_period_seconds", 0.4, 0.05, 30.0);
        TURB_ROCKING_PERIOD = BUILDER.comment("Интервал обязательной смены знака вращающего момента, секунды. Полный цикл вдвое длиннее.")
                .defineInRange("rocking_period_seconds", 0.16, 0.08, 2.0);
        TURB_FADE = BUILDER.comment("Путь плавного входа и выхода из эпизода.")
                .defineInRange("fade_distance", 10.0, 0.1, 10000.0);
        STORM_TURN = BUILDER.comment("Во сколько раз чаще случайные повороты ветра в полной грозе.")
                .defineInRange("storm_direction_multiplier", 3.0, 1.0, 20.0);
        STORM_TURB = BUILDER.comment("Множитель вероятности турбулентности в грозу.")
                .defineInRange("storm_chance_multiplier", 2.0, 1.0, 20.0);
        BUILDER.pop().pop();
    }
    public static final ModConfigSpec.BooleanValue PROPELLER_ALTITUDE_ENABLED;
    public static final ModConfigSpec.DoubleValue PROPELLER_FADE_START;
    public static final ModConfigSpec.DoubleValue PROPELLER_SCALE_HEIGHT;
    static {
        BUILDER.push("propellers");
        PROPELLER_ALTITUDE_ENABLED=BUILDER.comment("Дополнительно снижать тягу винтов с высотой в измерениях включённой atmosphere. Только блоки тега aste_aeroworks:altitude_sensitive_propellers.")
                .define("altitude_efficiency_enabled",true);
        PROPELLER_FADE_START=BUILDER.comment("Мировая Y начала дополнительного ослабления винтов. Ниже винты работают по прежней кривой давления.")
                .defineInRange("fade_start_height",128.0,-1000000.0,1000000.0);
        PROPELLER_SCALE_HEIGHT=BUILDER.comment("Подъём после fade_start_height, при котором дополнительный множитель равен 1/e. Меньше = быстрее ослабевает тяга.",
                "Множитель = exp(-((Y-fade_start_height)/scale_height)^2), только выше начала перехода.")
                .defineInRange("scale_height",256.0,1.0,1000000.0);
        BUILDER.pop();
    }
    public static final ModConfigSpec.BooleanValue FAILURE_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FAILURE_BLOCKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FAILURE_BLOCK_PROFILES;
    public static final ModConfigSpec.IntValue FAILURE_REPAIR_GRACE;
    public static final ModConfigSpec.DoubleValue FAILURE_CHANCE;
    public static final ModConfigSpec.DoubleValue FAILURE_START_CHANCE;
    public static final ModConfigSpec.DoubleValue FAILURE_REFERENCE_RPM;
    public static final ModConfigSpec.DoubleValue FAILURE_RPM_WEIGHT;
    public static final ModConfigSpec.DoubleValue FAILURE_REFERENCE_SPEED;
    public static final ModConfigSpec.DoubleValue FAILURE_SPEED_WEIGHT;
    public static final ModConfigSpec.DoubleValue FAILURE_ALTITUDE_START;
    public static final ModConfigSpec.DoubleValue FAILURE_ALTITUDE_SCALE;
    public static final ModConfigSpec.DoubleValue FAILURE_ALTITUDE_WEIGHT;
    public static final ModConfigSpec.DoubleValue FAILURE_TURBULENCE;
    public static final ModConfigSpec.DoubleValue FAILURE_MAX_MULTIPLIER;
    static {
        BUILDER.push("failures");
        FAILURE_ENABLED=BUILDER.comment("Случайные отказы работающих кинетических блоков. false временно отключает последствия, не стирая сохранённые отказы.").define("enabled",true);
        FAILURE_BLOCKS=BUILDER.comment("Список блоков, способных отказать. Проверяется ненулевое эффективное вращение Create.","Первый поддержанный тип — aeronautics:propeller_bearing. Специальные механизмы других модов могут требовать адаптера.")
                .defineListAllowEmpty("blocks",List.of("aeronautics:propeller_bearing"),()->"aeronautics:propeller_bearing",
                        value->value instanceof String id && ResourceLocation.tryParse(id)!=null);
        FAILURE_BLOCK_PROFILES=BUILDER.comment("Множители общих параметров для каждого блока. Отсутствующие строки добавляются из blocks.",
                        "Формат: namespace:block; base_chance_per_minute_factor=1.0; chance_per_start_factor=1.0; rpm_risk_weight_factor=0.0",
                        "Любой числовой параметр общего риска и repair_grace_seconds получает суффикс _factor: итог = общий параметр * фактор.",
                        "Неуказанный фактор равен 1. Шансы ограничены 0..1, защита — 0..3600 с, turbulence/maximum не ниже 1.",
                        "count_reversals_as_start=true/false остаётся переключателем. Старые абсолютные строки автоматически переводятся в факторы.",
                        "Для create:mechanical_bearing стартовый фактор 1 и фактор влияния RPM 0; для остальных стартовый фактор 0.")
                .defineListAllowEmpty("block_profiles",List.<String>of(),()->"aeronautics:propeller_bearing; base_chance_per_minute_factor=1.0; chance_per_start_factor=0.0",
                        value->value instanceof String text && !text.isBlank());
        FAILURE_REPAIR_GRACE=BUILDER.comment("Защита от повторного отказа после починки ключом, в секундах загруженного времени.")
                .defineInRange("repair_grace_seconds",15,0,3600);
        FAILURE_CHANCE=BUILDER.comment("Общий базовый шанс за минуту фактического вращения; умножается на base_chance_per_minute_factor блока.").defineInRange("base_chance_per_minute",0.3,0.0,1.0);
        FAILURE_START_CHANCE=BUILDER.comment("Общий базовый шанс на начало движения/реверс; умножается на chance_per_start_factor блока. Ноль отключает стартовые отказы у всех блоков.").defineInRange("chance_per_start",0.02,0.0,1.0);
        FAILURE_REFERENCE_RPM=BUILDER.comment("Эталонные обороты для базового риска.").defineInRange("reference_rpm",64.0,1.0,1000000.0);
        FAILURE_RPM_WEIGHT=BUILDER.comment("Влияние оборотов: при 1 риск пропорционален абсолютным RPM, минимум 5% от базового.").defineInRange("rpm_risk_weight",1.0,0.0,100.0);
        FAILURE_REFERENCE_SPEED=BUILDER.comment("Эталонная мировая скорость аппарата в блоках/сек.").defineInRange("reference_flight_speed",40.0,1.0,1000000.0);
        FAILURE_SPEED_WEIGHT=BUILDER.comment("При 1 скорость reference_flight_speed удваивает риск.").defineInRange("flight_speed_risk_weight",1.0,0.0,100.0);
        FAILURE_ALTITUDE_START=BUILDER.comment("Мировая Y, выше которой риск растёт.").defineInRange("altitude_start",128.0,-1000000.0,1000000.0);
        FAILURE_ALTITUDE_SCALE=BUILDER.comment("Подъём выше altitude_start, который при весе 1 удваивает риск.").defineInRange("altitude_scale",256.0,1.0,1000000.0);
        FAILURE_ALTITUDE_WEIGHT=BUILDER.comment("Множитель влияния высоты.").defineInRange("altitude_risk_weight",1.0,0.0,100.0);
        FAILURE_TURBULENCE=BUILDER.comment("Множитель риска во время эпизода турбулентности самого аппарата.").defineInRange("turbulence_multiplier",8.0,1.0,1000.0);
        FAILURE_MAX_MULTIPLIER=BUILDER.comment("Верхняя граница произведения всех множителей риска.").defineInRange("maximum_risk_multiplier",256.0,1.0,1000000.0);
        BUILDER.pop();
    }
    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
}
