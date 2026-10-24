package net.aste.aeroworks.failure;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Numeric per-type factors applied to the live common settings at each config reload. */
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
        return parse(text,defaults,graceTicks,.02);
    }
    public static Named parse(String text,FailureRisk defaults,int graceTicks,double startChance) {
        String id=idOf(text);
        if(id==null)throw new IllegalArgumentException("Expected namespace:block before the first semicolon");
        var values=new HashMap<String,String>();
        String[] parts=text.split(";",-1);
        for(int i=1;i<parts.length;i++) {
            if(parts[i].isBlank())continue;
            String[] pair=parts[i].trim().split("=",-1);
            if(pair.length!=2 || !(pair[0].trim().equals("count_reversals_as_start") || (pair[0].trim().endsWith("_factor") && KEYS.subList(0,12).contains(pair[0].trim().substring(0,pair[0].trim().length()-7)))))throw new IllegalArgumentException("Unknown/malformed setting: "+parts[i]);
            if(values.putIfAbsent(pair[0].trim(),pair[1].trim())!=null)throw new IllegalArgumentException("Duplicate setting: "+pair[0]);
        }
        var risk=new FailureRisk(Math.min(1,scaled(values,KEYS.get(0),defaults.baseChancePerMinute())),scaled(values,KEYS.get(1),defaults.referenceRPM()),
                scaled(values,KEYS.get(2),defaults.rpmWeight()),scaled(values,KEYS.get(3),defaults.referenceSpeed()),
                scaled(values,KEYS.get(4),defaults.speedWeight()),scaled(values,KEYS.get(5),defaults.altitudeStart()),
                scaled(values,KEYS.get(6),defaults.altitudeScale()),scaled(values,KEYS.get(7),defaults.altitudeWeight()),
                Math.max(1,scaled(values,KEYS.get(8),defaults.turbulenceMultiplier())),Math.max(1,scaled(values,KEYS.get(9),defaults.maximumMultiplier())));
        String reverse=values.getOrDefault("count_reversals_as_start","true");
        if(!reverse.equals("true")&&!reverse.equals("false"))throw new IllegalArgumentException("count_reversals_as_start must be true/false");
        int grace=(int)Math.round(Math.min(72000,scaled(values,"repair_grace_seconds",graceTicks)));
        return new Named(id,new FailureBlockProfile(risk,Math.min(1,scaled(values,"chance_per_start",startChance)),grace,Boolean.parseBoolean(reverse)));
    }
    private static double scaled(Map<String,String> values,String key,double common) {
        double factor=values.containsKey(key+"_factor")?Double.parseDouble(values.get(key+"_factor")):1;
        if(!Double.isFinite(factor)||factor<0)throw new IllegalArgumentException("Factors must be finite and non-negative: "+key);
        double result=common*factor;
        if(!Double.isFinite(result))throw new IllegalArgumentException("Factor product overflow: "+key);
        return result;
    }
    /** Convert old absolute entries once. Current values are retained where a nonzero common baseline permits it. */
    public static String migrate(String text,FailureRisk defaults,int graceTicks,double startChance) {
        String id=idOf(text);
        if(id==null)return text;
        double[] common={defaults.baseChancePerMinute(),defaults.referenceRPM(),defaults.rpmWeight(),defaults.referenceSpeed(),
                defaults.speedWeight(),defaults.altitudeStart(),defaults.altitudeScale(),defaults.altitudeWeight(),
                defaults.turbulenceMultiplier(),defaults.maximumMultiplier(),startChance,graceTicks/20.0};
        String[] parts=text.split(";",-1);
        boolean changed=false;
        for(int i=1;i<parts.length;i++) {
            String[] pair=parts[i].trim().split("=",-1);
            if(pair.length!=2)continue;
            int index=KEYS.indexOf(pair[0].trim());
            if(index<0||index>=common.length)continue;
            double old=Double.parseDouble(pair[1].trim());
            if(!Double.isFinite(old))throw new IllegalArgumentException("Non-finite legacy value");
            // A zero global value must stay zero; it cannot be bypassed by an individual profile.
            double factor=common[index]==0?(old==0?0:1):old/common[index];
            if(factor<0||!Double.isFinite(factor))throw new IllegalArgumentException("Legacy value cannot become a non-negative factor");
            parts[i]=" "+KEYS.get(index)+"_factor="+factor;
            changed=true;
        }
        if(!changed)return text;
        String migrated=String.join(";",parts);
        // The old format had no common start chance: omission explicitly meant zero.
        boolean hasStart=false;
        for(int i=1;i<parts.length;i++)if(parts[i].trim().startsWith("chance_per_start_factor="))hasStart=true;
        return hasStart?migrated:migrated+"; chance_per_start_factor=0.0";
    }
    public static List<String> completeEntries(List<String> configured,List<String> blocks,FailureRisk defaults) {
        var entries=new ArrayList<>(configured);
        var present=new HashSet<String>();
        for(var text:entries) {String id=idOf(text);if(id!=null)present.add(id);}
        for(var id:blocks)if(present.add(id))entries.add(defaultEntry(id,defaults));
        return List.copyOf(entries);
    }
    public static String defaultEntry(String id,FailureRisk defaults) {
        String result=id+"; base_chance_per_minute_factor=1.0";
        return id.equals("create:mechanical_bearing")
                ?result+"; chance_per_start_factor=1.0; rpm_risk_weight_factor=0.0"
                :result+"; chance_per_start_factor=0.0";
    }
    public double probability(double averageRPM,double eventRPM,double speed,double height,boolean turbulent,double seconds,boolean started) {
        double time=risk.probability(averageRPM,speed,height,turbulent,seconds);
        double start=started?risk.eventProbability(chancePerStart,eventRPM,speed,height,turbulent):0;
        return -Math.expm1(Math.log1p(-time)+Math.log1p(-start));
    }
}
