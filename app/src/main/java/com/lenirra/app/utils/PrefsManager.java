package com.lenirra.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsManager {
    private static final String PREFS_NAME = "kernelforge_prefs";
    private static SharedPreferences prefs;

    public static void init(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Boot & General ──────────────────────────────────────────────────────
    public static boolean isApplyOnBoot() { return prefs.getBoolean("apply_on_boot", false); }
    public static void setApplyOnBoot(boolean v) { prefs.edit().putBoolean("apply_on_boot", v).apply(); }

    public static boolean isDropCachesOnBoot() { return prefs.getBoolean("drop_caches_boot", false); }
    public static void setDropCachesOnBoot(boolean v) { prefs.edit().putBoolean("drop_caches_boot", v).apply(); }

    // ─── Profile ─────────────────────────────────────────────────────────────
    public static String getCurrentProfile() { return prefs.getString("current_profile", "balanced"); }
    public static void setCurrentProfile(String v) { prefs.edit().putString("current_profile", v).apply(); }

    public static String getSavedGovernor() { return prefs.getString("saved_governor", "schedutil"); }
    public static void setSavedGovernor(String v) { prefs.edit().putString("saved_governor", v).apply(); }

    public static String getSavedIoScheduler() { return prefs.getString("saved_io_sched", "cfq"); }
    public static void setSavedIoScheduler(String v) { prefs.edit().putString("saved_io_sched", v).apply(); }

    public static int getSavedSwappiness() { return prefs.getInt("saved_swappiness", 60); }
    public static void setSavedSwappiness(int v) { prefs.edit().putInt("saved_swappiness", v).apply(); }

    // ─── Kernel Tweaks ───────────────────────────────────────────────────────
    public static boolean isZramEnabled() { return prefs.getBoolean("zram_enabled", false); }
    public static void setZramEnabled(boolean v) { prefs.edit().putBoolean("zram_enabled", v).apply(); }

    public static boolean isTcpBbrEnabled() { return prefs.getBoolean("tcp_bbr", false); }
    public static void setTcpBbrEnabled(boolean v) { prefs.edit().putBoolean("tcp_bbr", v).apply(); }

    public static boolean isAggressiveDoze() { return prefs.getBoolean("aggressive_doze", false); }
    public static void setAggressiveDoze(boolean v) { prefs.edit().putBoolean("aggressive_doze", v).apply(); }

    public static boolean isSyncDisabled() { return prefs.getBoolean("sync_disabled", false); }
    public static void setSyncDisabled(boolean v) { prefs.edit().putBoolean("sync_disabled", v).apply(); }

    // ─── GPU ──────────────────────────────────────────────────────────────────
    public static boolean isAdrenoBoostEnabled() { return prefs.getBoolean("adreno_boost", false); }
    public static void setAdrenoBoostEnabled(boolean v) { prefs.edit().putBoolean("adreno_boost", v).apply(); }

    public static boolean isGpuThrottleEnabled() { return prefs.getBoolean("gpu_throttle", true); }
    public static void setGpuThrottleEnabled(boolean v) { prefs.edit().putBoolean("gpu_throttle", v).apply(); }

    public static boolean isGpuOcEnabled() { return prefs.getBoolean("gpu_oc", false); }
    public static void setGpuOcEnabled(boolean v) { prefs.edit().putBoolean("gpu_oc", v).apply(); }

    public static int getGpuMaxFreqMhz() { return prefs.getInt("gpu_max_freq_mhz", -1); }
    public static void setGpuMaxFreqMhz(int v) { prefs.edit().putInt("gpu_max_freq_mhz", v).apply(); }

    public static int getGpuMinFreqMhz() { return prefs.getInt("gpu_min_freq_mhz", -1); }
    public static void setGpuMinFreqMhz(int v) { prefs.edit().putInt("gpu_min_freq_mhz", v).apply(); }

    public static String getGpuGovernor() { return prefs.getString("gpu_governor", ""); }
    public static void setGpuGovernor(String v) { prefs.edit().putString("gpu_governor", v).apply(); }

    public static int getCpuMaxFreqMhz() { return prefs.getInt("cpu_max_freq_mhz", -1); }
    public static void setCpuMaxFreqMhz(int v) { prefs.edit().putInt("cpu_max_freq_mhz", v).apply(); }

    public static int getCpuMinFreqMhz() { return prefs.getInt("cpu_min_freq_mhz", -1); }
    public static void setCpuMinFreqMhz(int v) { prefs.edit().putInt("cpu_min_freq_mhz", v).apply(); }
}
