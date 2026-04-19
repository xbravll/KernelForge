package com.lenirra.app.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.fragment.app.Fragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.lenirra.app.R;
import com.lenirra.app.utils.PrefsManager;
import com.lenirra.app.utils.RootUtils;

public class GpuFragment extends Fragment {

    private TextView  tvGpuFreqCurrent, tvGpuGovernorCurrent;
    private TextView  tvGpuMaxFreq, tvGpuMinFreq;
    private TextView  tvGpuStatus;
    private ChipGroup chipGroupGpuGov;
    private Spinner   spinnerGpuGov;
    private Spinner   spinnerGpuMaxFreq;
    private Spinner   spinnerGpuMinFreq;
    private Switch    switchAdrenoBoost, switchGpuThrottle, switchGpuOc;

    private String   selectedGov = "";
    private String[] freqListMhz = {};

    // Flag agar listener spinner tidak trigger apply saat pertama populate
    private boolean spinnerReady = false;

    private static final String[] GPU_GOV_PATHS = {
        "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
        "/sys/class/devfreq/gpufreq/governor",
        "/sys/kernel/gpu/gpu_governor"
    };
    private static final String[] GPU_MAX_PATHS = {
        "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
        "/sys/class/devfreq/gpufreq/max_freq",
        "/sys/kernel/gpu/gpu_max_clock"
    };
    private static final String[] GPU_MIN_PATHS = {
        "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq",
        "/sys/class/devfreq/gpufreq/min_freq",
        "/sys/kernel/gpu/gpu_min_clock"
    };
    private static final String[] GPU_AVAIL_FREQ_PATHS = {
        "/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies",
        "/sys/class/devfreq/gpufreq/available_frequencies",
        "/sys/kernel/gpu/gpu_available_frequencies"
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_gpu, container, false);
        initViews(v);
        loadGpuInfo();
        return v;
    }

    private void initViews(View v) {
        tvGpuFreqCurrent     = v.findViewById(R.id.tv_gpu_freq_current);
        tvGpuGovernorCurrent = v.findViewById(R.id.tv_gpu_gov_current);
        tvGpuMaxFreq         = v.findViewById(R.id.tv_gpu_max_freq);
        tvGpuMinFreq         = v.findViewById(R.id.tv_gpu_min_freq);
        chipGroupGpuGov      = v.findViewById(R.id.chipgroup_gpu_governor);
        spinnerGpuGov        = v.findViewById(R.id.spinner_gpu_governor);
        spinnerGpuMaxFreq    = v.findViewById(R.id.spinner_gpu_max_freq);
        spinnerGpuMinFreq    = v.findViewById(R.id.spinner_gpu_min_freq);
        switchAdrenoBoost    = v.findViewById(R.id.switch_adreno_boost);
        switchGpuThrottle    = v.findViewById(R.id.switch_gpu_throttle);
        switchGpuOc          = v.findViewById(R.id.switch_gpu_oc);
        tvGpuStatus          = v.findViewById(R.id.tv_gpu_status);

        // Sembunyikan btn_apply_gpu — tidak dipakai lagi (auto-apply)
        View btnApply = v.findViewById(R.id.btn_apply_gpu);
        if (btnApply != null) btnApply.setVisibility(View.GONE);

        // Restore saved state switches
        switchAdrenoBoost.setChecked(PrefsManager.isAdrenoBoostEnabled());
        switchGpuThrottle.setChecked(PrefsManager.isGpuThrottleEnabled());
        switchGpuOc.setChecked(PrefsManager.isGpuOcEnabled());

        // Auto-apply langsung saat switch diubah
        switchAdrenoBoost.setOnCheckedChangeListener((btn, checked) -> {
            PrefsManager.setAdrenoBoostEnabled(checked);
            applyAdrenoBoost(checked);
        });
        switchGpuThrottle.setOnCheckedChangeListener((btn, checked) -> {
            PrefsManager.setGpuThrottleEnabled(checked);
            applyGpuThrottle(checked);
        });
        switchGpuOc.setOnCheckedChangeListener((btn, checked) -> {
            PrefsManager.setGpuOcEnabled(checked);
            applyGpuOc(checked);
        });
    }

    private void loadGpuInfo() {
        tvGpuStatus.setText("Loading\u2026");
        spinnerReady = false;

        new Thread(() -> {
            int    curFreqMhz = RootUtils.getGpuFreqMhz();
            String curGov     = readFirstValid(GPU_GOV_PATHS);
            String maxRaw     = readFirstValid(GPU_MAX_PATHS);
            String minRaw     = readFirstValid(GPU_MIN_PATHS);
            String availRaw   = readFirstValid(GPU_AVAIL_FREQ_PATHS);

            String[] parsedMhz = parseAvailFreqs(availRaw, maxRaw, minRaw);

            int maxMhz = parsedMhz.length > 0 ? Integer.parseInt(parsedMhz[0]) : 800;
            int minMhz = parsedMhz.length > 0 ? Integer.parseInt(parsedMhz[parsedMhz.length - 1]) : 100;

            String govListRaw = RootUtils.runCommand(
                "cat /sys/class/kgsl/kgsl-3d0/devfreq/available_governors 2>/dev/null || " +
                "cat /sys/class/devfreq/gpufreq/available_governors 2>/dev/null || " +
                "echo 'simple_ondemand msm-adreno-tz powersave performance'");
            String[] govList = govListRaw.trim().isEmpty()
                ? new String[]{"simple_ondemand", "msm-adreno-tz", "powersave", "performance", "userspace"}
                : govListRaw.trim().split("\\s+");

            // Tentukan governor awal: ambil dari prefs jika ada, fallback ke device
            String savedGov = PrefsManager.getGpuGovernor();
            final String fCurGov = (savedGov != null && !savedGov.isEmpty()) ? savedGov : curGov.trim();

            // Tentukan freq awal: ambil dari prefs jika ada, fallback ke device max/min
            int savedMax = PrefsManager.getGpuMaxFreqMhz();
            int savedMin = PrefsManager.getGpuMinFreqMhz();
            final int fSavedMax = (savedMax > 0) ? savedMax : maxMhz;
            final int fSavedMin = (savedMin > 0) ? savedMin : minMhz;

            final String[] fFreqList = parsedMhz;
            final String[] fGovList  = govList;
            final int fCurFreq = curFreqMhz;

            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;

                freqListMhz = fFreqList;

                tvGpuFreqCurrent.setText(fCurFreq > 0 ? fCurFreq + "" : "\u2014");
                tvGpuGovernorCurrent.setText(fCurGov.isEmpty() ? "\u2014" : fCurGov);
                tvGpuMaxFreq.setText(fSavedMax + "");
                tvGpuMinFreq.setText(fSavedMin + "");
                selectedGov = fCurGov;

                // Governor UI
                if (fGovList.length <= 8) {
                    chipGroupGpuGov.setVisibility(View.VISIBLE);
                    spinnerGpuGov.setVisibility(View.GONE);
                    buildGovChips(fGovList, fCurGov);
                } else {
                    chipGroupGpuGov.setVisibility(View.GONE);
                    spinnerGpuGov.setVisibility(View.VISIBLE);
                    buildGovSpinner(fGovList, fCurGov);
                }

                // Populate freq spinners dan restore nilai tersimpan
                if (fFreqList.length > 0) {
                    String[] labels = new String[fFreqList.length];
                    for (int i = 0; i < fFreqList.length; i++)
                        labels[i] = fFreqList[i] + " MHz";

                    ArrayAdapter<String> adapterMax = makeFreqAdapter(labels);
                    spinnerGpuMaxFreq.setAdapter(adapterMax);
                    spinnerGpuMaxFreq.setSelection(findFreqIndex(fFreqList, fSavedMax, 0));

                    ArrayAdapter<String> adapterMin = makeFreqAdapter(labels);
                    spinnerGpuMinFreq.setAdapter(adapterMin);
                    spinnerGpuMinFreq.setSelection(findFreqIndex(fFreqList, fSavedMin, labels.length - 1));
                } else {
                    String[] fallback = {"800 MHz", "700 MHz", "600 MHz", "500 MHz",
                                         "400 MHz", "300 MHz", "200 MHz", "100 MHz"};
                    spinnerGpuMaxFreq.setAdapter(makeFreqAdapter(fallback));
                    spinnerGpuMinFreq.setAdapter(makeFreqAdapter(fallback));
                    spinnerGpuMinFreq.setSelection(fallback.length - 1);
                }

                // Pasang listener SETELAH adapter & selection selesai
                spinnerReady = true;
                attachFreqListeners();

                tvGpuStatus.setText("Loaded \u2713");
            });
        }).start();
    }

    /** Cari index freq di freqList yang cocok targetMhz, fallback ke defaultIdx */
    private int findFreqIndex(String[] freqList, int targetMhz, int defaultIdx) {
        for (int i = 0; i < freqList.length; i++) {
            try {
                if (Integer.parseInt(freqList[i]) == targetMhz) return i;
            } catch (NumberFormatException ignored) {}
        }
        return defaultIdx;
    }

    private void attachFreqListeners() {
        spinnerGpuMaxFreq.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (!spinnerReady) return;
                int mhz = getSelectedFreqMhz(spinnerGpuMaxFreq);
                if (mhz > 0) {
                    PrefsManager.setGpuMaxFreqMhz(mhz);
                    tvGpuMaxFreq.setText(mhz + "");
                    applyGpuMaxFreq(mhz);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerGpuMinFreq.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (!spinnerReady) return;
                int mhz = getSelectedFreqMhz(spinnerGpuMinFreq);
                if (mhz > 0) {
                    PrefsManager.setGpuMinFreqMhz(mhz);
                    tvGpuMinFreq.setText(mhz + "");
                    applyGpuMinFreq(mhz);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // ─── Apply individual settings ───────────────────────────────────────────

    private void applyGpuMaxFreq(int mhz) {
        tvGpuStatus.setText("Applying\u2026");
        new Thread(() -> {
            long hz = (long) mhz * 1_000_000L;
            for (String p : GPU_MAX_PATHS)
                RootUtils.runCommand("echo " + hz + " > " + p + " 2>/dev/null");
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> tvGpuStatus.setText("Max freq applied \u2713"));
        }).start();
    }

    private void applyGpuMinFreq(int mhz) {
        tvGpuStatus.setText("Applying\u2026");
        new Thread(() -> {
            long hz = (long) mhz * 1_000_000L;
            for (String p : GPU_MIN_PATHS)
                RootUtils.runCommand("echo " + hz + " > " + p + " 2>/dev/null");
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> tvGpuStatus.setText("Min freq applied \u2713"));
        }).start();
    }

    private void applyGovNow(String gov) {
        if (gov.isEmpty()) return;
        PrefsManager.setGpuGovernor(gov);
        tvGpuGovernorCurrent.setText(gov);
        tvGpuStatus.setText("Applying\u2026");
        new Thread(() -> {
            for (String p : GPU_GOV_PATHS)
                RootUtils.runCommand("echo " + gov + " > " + p + " 2>/dev/null");
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> tvGpuStatus.setText("Governor applied \u2713"));
        }).start();
    }

    private void applyAdrenoBoost(boolean enabled) {
        new Thread(() ->
            RootUtils.runCommand("echo " + (enabled ? "1" : "0") +
                " > /sys/class/kgsl/kgsl-3d0/force_clk_on 2>/dev/null")
        ).start();
    }

    private void applyGpuThrottle(boolean enabled) {
        new Thread(() ->
            RootUtils.runCommand("echo " + (enabled ? "1" : "0") +
                " > /sys/class/kgsl/kgsl-3d0/throttling 2>/dev/null")
        ).start();
    }

    private void applyGpuOc(boolean enabled) {
        if (enabled) {
            new Thread(() ->
                RootUtils.runCommand("echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null")
            ).start();
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ArrayAdapter<String> makeFreqAdapter(String[] items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(),
                R.layout.spinner_item_white, items);
        a.setDropDownViewResource(R.layout.spinner_dropdown_item_white);
        return a;
    }

    private String[] parseAvailFreqs(String raw, String maxRaw, String minRaw) {
        if (raw == null || raw.trim().isEmpty()) {
            int max = parseToMhz(maxRaw);
            int min = parseToMhz(minRaw);
            if (max <= 0) return new String[0];
            if (min <= 0) min = 100;
            java.util.List<String> gen = new java.util.ArrayList<>();
            for (int f = max; f >= min; f -= 50)
                gen.add(String.valueOf(f));
            return gen.toArray(new String[0]);
        }

        String[] tokens = raw.trim().split("\\s+");
        java.util.TreeSet<Integer> freqs = new java.util.TreeSet<>(java.util.Collections.reverseOrder());
        for (String t : tokens) {
            try {
                long hz = Long.parseLong(t.trim());
                int mhz = hz > 1_000_000 ? (int)(hz / 1_000_000) : hz > 1_000 ? (int)(hz / 1_000) : (int)hz;
                if (mhz > 0) freqs.add(mhz);
            } catch (NumberFormatException ignored) {}
        }

        String[] result = new String[freqs.size()];
        int i = 0;
        for (int mhz : freqs) result[i++] = String.valueOf(mhz);
        return result;
    }

    private int parseToMhz(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0;
        try {
            long hz = Long.parseLong(raw.trim().split("\\s+")[0]);
            return hz > 1_000_000 ? (int)(hz / 1_000_000) : hz > 1_000 ? (int)(hz / 1_000) : (int)hz;
        } catch (Exception e) { return 0; }
    }

    private void buildGovChips(String[] govList, String current) {
        chipGroupGpuGov.removeAllViews();
        for (String gov : govList) {
            Chip chip = new Chip(requireContext());
            chip.setText(gov);
            chip.setCheckable(true);
            chip.setChecked(gov.equals(current));
            chip.setTag(gov);
            chip.setChipBackgroundColorResource(R.color.chip_bg_color);
            chip.setChipStrokeColorResource(R.color.chip_stroke_color);
            chip.setChipStrokeWidth(1.5f);
            chip.setTextColor(getResources().getColorStateList(R.color.chip_text_color));
            chip.setTextSize(12f);
            chip.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) {
                    selectedGov = gov;
                    applyGovNow(gov);
                }
            });
            chipGroupGpuGov.addView(chip);
        }
    }

    private void buildGovSpinner(String[] govList, String current) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                R.layout.spinner_item_white, govList);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white);
        spinnerGpuGov.setAdapter(adapter);
        for (int i = 0; i < govList.length; i++) {
            if (govList[i].equals(current)) { spinnerGpuGov.setSelection(i); break; }
        }
        spinnerGpuGov.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                String gov = govList[pos];
                if (!gov.equals(selectedGov)) {
                    selectedGov = gov;
                    applyGovNow(gov);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private int getSelectedFreqMhz(Spinner spinner) {
        if (spinner.getSelectedItem() == null) return 0;
        try {
            String s = spinner.getSelectedItem().toString().replace(" MHz", "").trim();
            return Integer.parseInt(s);
        } catch (Exception e) { return 0; }
    }

    private String readFirstValid(String[] paths) {
        for (String p : paths) {
            String val = RootUtils.runCommand("cat " + p + " 2>/dev/null");
            if (!val.isEmpty()) return val.trim();
        }
        return "";
    }
}
