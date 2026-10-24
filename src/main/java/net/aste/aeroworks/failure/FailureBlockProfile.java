package net.aste.aeroworks.failure;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Per-type overrides. Unspecified keys inherit the common defaults when the config is loaded. */
public record FailureBlockProfile(FailureRisk risk,double chancePerStart,int repairGraceTicks,boolean countReversals) {
    public FailureBlockProfile {
        if(!Double.isFinite(chancePerStart)||chancePerStart<0||chancePerStart>1||repairGraceTicks<0||repairGraceTicks>72000)
            throw new IllegalArgumentException("Invalid start chance or repair protection");
    }
    public record Named(String id,FailureBlockProfile profile) {}
    private static final List<String> KEYS=List.of("base_chance_per_minute","reference_rpm","rpm_risk_weight",
            "reference_flight_speed","flight_speed_risk_weight","altitude_start","altitude_scale","altitude_risk_weight",
            "turbulence_multiplier","maximum_risk_multiplier","chance_per_start","repair_grace_seconds","count_reversals_as_start");
    public static String idOf(String text) {
        String id=text.split(";",2)[0].trim();
        return id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")?id:null;
    }
    public static Named parse(String text,FailureRisk defaults,int graceTicks) {
        String id=idOf(text);
        if(id==null)throw new IllegalArgumentException("Expected namespace:block before the first semicolon");
        var values=new HashMap<String,String>();
        String[] parts=text.split(";",-1);
        for(int i=1;i<parts.length;i++) {
            if(parts[i].isBlank())continue;
            String[] pair=parts[i].trim().split("=",-1);
            if(pair.length!=2 || !KEYS.contains(pair[0].trim()))throw new IllegalArgumentException("Unknown/malformed setting: "+parts[i]);
            if(values.putIfAbsent(pair[0].trim(),pair[1].trim())!=null)throw new IllegalArgumentException("Duplicate setting: "+pair[0]);
        }
        var risk=new FailureRisk(number(values,KEYS.get(0),defaults.baseChancePerMinute()),number(values,KEYS.get(1),defaults.referenceRPM()),
                number(values,KEYS.get(2),defaults.rpmWeight()),number(values,KEYS.get(3),defaults.referenceSpeed()),
                number(values,KEYS.get(4),defaults.speedWeight()),number(values,KEYS.get(5),defaults.altitudeStart()),
                number(values,KEYS.get(6),defaults.altitudeScale()),number(values,KEYS.get(7),defaults.altitudeWeight()),
                number(values,KEYS.get(8),defaults.turbulenceMultiplier()),number(values,KEYS.get(9),defaults.maximumMultiplier()));
        String reverse=values.getOrDefault("count_reversals_as_start","true");
        if(!reverse.equals("true")&&!reverse.equals("false"))throw new IllegalArgumentException("count_reversals_as_start must be true/false");
        int grace=values.containsKey("repair_grace_seconds")?Math.multiplyExact(Integer.parseInt(values.get("repair_grace_seconds")),20):graceTicks;
        return new Named(id,new FailureBlockProfile(risk,number(values,"chance_per_start",0),grace,Boolean.parseBoolean(reverse)));
    }
    private static double number(Map<String,String> values,String key,double fallback) {
        return values.containsKey(key)?Double.parseDouble(values.get(key)):fallback;
    }
    public static List<String> completeEntries(List<String> configured,List<String> blocks,FailureRisk defaults) {
        var entries=new ArrayList<>(configured);
        var present=new HashSet<String>();
        for(var text:entries) {String id=idOf(text);if(id!=null)present.add(id);}
        for(var id:blocks)if(present.add(id))entries.add(defaultEntry(id,defaults));
        return List.copyOf(entries);
    }
    public static String defaultEntry(String id,FailureRisk defaults) {
        String result=id+"; base_chance_per_minute="+defaults.baseChancePerMinute();
        return id.equals("create:mechanical_bearing")
                ?result+"; chance_per_start=0.02; rpm_risk_weight=0.0"
                :result+"; chance_per_start=0.0";
    }
    public double probability(double averageRPM,double eventRPM,double speed,double height,boolean turbulent,double seconds,boolean started) {
        double time=risk.probability(averageRPM,speed,height,turbulent,seconds);
        double start=started?risk.eventProbability(chancePerStart,eventRPM,speed,height,turbulent):0;
        return -Math.expm1(Math.log1p(-time)+Math.log1p(-start));
    }
}
