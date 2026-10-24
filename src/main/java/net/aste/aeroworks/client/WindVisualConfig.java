package net.aste.aeroworks.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class WindVisualConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue CAMERA_SHAKE, UPDRAFT_CLOUDS;
    public static final ModConfigSpec.DoubleValue SHAKE_STRENGTH;
    public static final ModConfigSpec.IntValue PARTICLES_PER_SECOND;
    static {
        var b=new ModConfigSpec.Builder();
        CAMERA_SHAKE=b.comment("Тряска камеры только на физической конструкции с активной турбулентностью.").define("camera_shake",true);
        SHAKE_STRENGTH=b.comment("Сила визуальной тряски. Не меняет физику аппарата.").defineInRange("camera_shake_strength",1.0,0.0,3.0);
        UPDRAFT_CLOUDS=b.comment("Белые облачные шапки над восходящими горными потоками.").define("updraft_clouds",true);
        PARTICLES_PER_SECOND=b.comment("Общий бюджет новых облачных частиц в секунду, на все видимые потоки.").defineInRange("indicator_particles_per_second",32,0,128);
        SPEC=b.build();
    }
    private WindVisualConfig() {}
}
