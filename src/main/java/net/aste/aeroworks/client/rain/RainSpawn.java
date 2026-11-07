package net.aste.aeroworks.client.rain;

/** Exact first nextInt(100) of Minecraft's seeded LegacyRandomSource; no object allocation. */
public final class RainSpawn {
    private static final long MULTIPLIER=0x5deece66dL,MASK=(1L<<48)-1;
    private RainSpawn(){}
    public static boolean accepted(long seed){
        long state=(seed^MULTIPLIER)&MASK;int bits,value;
        do {state=(state*MULTIPLIER+11)&MASK;bits=(int)(state>>>17);value=bits%100;}while(bits-value+99<0);
        return value<=2;
    }
}
