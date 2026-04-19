package com.lenirra.app.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.fragment.app.Fragment;
import com.google.android.material.slider.Slider;
import com.lenirra.app.R;
import com.lenirra.app.utils.PrefsManager;
import com.lenirra.app.utils.RootUtils;

public class KernelFragment extends Fragment {

    private Spinner  spinnerGovernor;
    private Spinner  spinnerIoSched;
    private Spinner  spinnerTcpCong;
    private Spinner  spinnerCpuMaxFreq;
    private Spinner  spinnerCpuMinFreq;
    private Slider   sliderSwappiness;
    private TextView swappinessValue;
    private TextView tvCpuMaxCurrent, tvCpuMinCurrent;
    private Switch   switchTcpBbr, switchZram, switchDoze, switchSync;
    private TextView tvKernelVer, tvCpuCores;
    private TextView tvCpuGovernorCurrent, tvIoSchedulerCurrent;

    private String[] cpuFreqListMhz = {};

    // Flag agar listener spinner tidak trigger apply saat initial populate
    private boolean spinnerReady = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_kernel, container, false);
        initViews(v);
        loadCurrentValues();
        return v;
    }

    private void initViews(View v) {
        spinnerGovernor      = v.findViewById(R.id.spinner_governor);
        spinnerIoSched       = v.findViewById(R.id.spinner_io_sched);
        spinnerTcpCong       = v.findViewById(R.id.spinner_tcp_cong);
        spinnerCpuMaxFreq    = v.findViewById(R.id.spinner_cpu_max_freq);
        spinnerCpuMinFreq    = v.findViewById(R.id.spinner_cpu_min_freq);
        sliderSwappiness     = v.findViewById(R.id.slider_swappiness);
        swappinessValue      = v.findViewById(R.id.swappiness_value);
        tvCpuMaxCurrent      = v.findViewById(R.id.tv_cpu_max_current);
        tvCpuMinCurrent      = v.findViewById(R.id.tv_cpu_min_current);
        switchTcpBbr         = v.findViewById(R.id.switch_tcp_bbr);
        switchZram           = v.findViewById(R.id.switch_zram);
        switchDoze           = v.findViewById(R.id.switch_doze);
        switchSync           = v.findViewById(R.id.switch_sync);
        tvKernelVer          = v.findViewById(R.id.tv_kernel_version);
        tvCpuCores           = v.findViewById(R.id.tv_cpu_cores);
        tvCpuGovernorCurrent = v.findViewById(R.id.tv_governor_current);
        tvIoSchedulerCurrent = v.findViewById(R.id.tv_io_current);

        // Sembunyikan btn_apply_kernel jika masih ada di layout
        View btnApply = v.findViewById(R.id.btn_apply_kernel);
        if (btnApply != null) btnApply.setVisibility(View.GONE);

        // Slider swappiness — auto-apply saat dilepas (touch up)
        sliderSwappiness.addOnChangeListener((s, val, fromUser) ->
                swappinessValue.setText(String.valueOf((int) val)));
        sliderSwappiness.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(Slider s) {}
            @Override public void onStopTrackingTouch(Slider s) {
                int swappiness = (int) s.getValue();
                PrefsManager.setSavedSwappiness(swappiness);
                applySwappiness(swappiness);
            }
        });

        // Switches — auto-apply langsung
        switchTcpBbr.setOnCheckedChangeListener((btn, checked) -> {
            PrefsManager.setTcpBbrEnabled(checked);
            applyTcpBbr(checked);
        });
        switchZram.setOnCheckedChangeListener((btn, checked) -> {
            PrefsManager.setZramEnabled(checked);
            applyZram(checked);
        });
        switchDoze.setOnCheckedChangeListener((btn, checked) -> {
            PrefsManager.setAggressiveDoze(checked);
            applyDoze(checked);
        });
        switchSync.setOnCheckedChangeListener((btn, checked) -> {
            PrefsManager.setSyncDisabled(checked);
            applySync(checked);
        });
    }

    private ArrayAdapter<String> makeAdapter(String[] items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(),
                R.layout.spinner_item_white, items);
        a.setDropDownViewResource(R.layout.spinner_dropdown_item_white);
        return a;
    }

    private void loadCurrentValues() {
        spinnerReady = false;

        new Thread(() -> {
            String kernelVer  = RootUtils.getKernelVersion();
            int    cores      = RootUtils.getNumCpuCores();
            String gov        = RootUtils.getCpuGovernor();
            String[] availGov = RootUtils.getAvailableGovernors();
            String ioSched    = RootUtils.getIoScheduler();
            int swappiness    = RootUtils.getSwappiness();
            String tcpCong    = RootUtils.getTcpCongestion();

            String availFreqRaw = RootUtils.runCommand(
                "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies 2>/dev/null");
            String curMaxRaw = RootUtils.runCommand(
                "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq 2>/dev/null");
            String curMinRaw = RootUtils.runCommand(
                "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq 2>/dev/null");

            String[] freqList = parseAvailFreqs(availFreqRaw);
            int curMaxMhz = parseKhzToMhz(curMaxRaw);
            int curMinMhz = parseKhzToMhz(curMinRaw);

            // Ambil saved CPU freq dari prefs, fallback ke device current
            int savedMax = PrefsManager.getCpuMaxFreqMhz();
            int savedMin = PrefsManager.getCpuMinFreqMhz();
            final int fSavedMax = (savedMax > 0) ? savedMax : curMaxMhz;
            final int fSavedMin = (savedMin > 0) ? savedMin : curMinMhz;

            // Governor: prefs dulu, fallback ke device
            String savedGov = PrefsManager.getSavedGovernor();
            final String fGov = (savedGov != null && !savedGov.isEmpty()
                    && !savedGov.equals("schedutil")) ? savedGov : gov;

            // I/O Scheduler: prefs dulu
            String savedIo = PrefsManager.getSavedIoScheduler();
            final String fIo = (savedIo != null && !savedIo.isEmpty()
                    && !savedIo.equals("cfq")) ? savedIo : ioSched;

            // Swappiness: prefs dulu
            int savedSwap = PrefsManager.getSavedSwappiness();
            final int fSwappiness = (savedSwap != 60) ? savedSwap : swappiness;

            final String[] fFreqList = freqList;
            final String[] fAvailGov = availGov;
            final String fTcpCong    = tcpCong;
            final String fKernelVer  = kernelVer;
            final int fCores = cores;

            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;

                tvKernelVer.setText(fKernelVer.isEmpty() ? "N/A" : fKernelVer);
                tvCpuCores.setText(fCores + " Cores");
                tvCpuGovernorCurrent.setText("Current: " + fGov);
                tvIoSchedulerCurrent.setText("Current: " + fIo);

                // CPU Governor spinner
                spinnerGovernor.setAdapter(makeAdapter(fAvailGov));
                for (int i = 0; i < fAvailGov.length; i++) {
                    if (fAvailGov[i].equals(fGov)) { spinnerGovernor.setSelection(i); break; }
                }

                // I/O Scheduler
                String[] schedulers = {"cfq","deadline","noop","bfq","kyber","mq-deadline"};
                spinnerIoSched.setAdapter(makeAdapter(schedulers));
                for (int i = 0; i < schedulers.length; i++) {
                    if (schedulers[i].equals(fIo)) { spinnerIoSched.setSelection(i); break; }
                }

                // Swappiness
                sliderSwappiness.setValue(Math.min(fSwappiness, 100));
                swappinessValue.setText(String.valueOf(fSwappiness));

                // TCP Congestion
                String[] tcpAlgos = {"cubic","bbr","reno","westwood","vegas","htcp"};
                spinnerTcpCong.setAdapter(makeAdapter(tcpAlgos));
                for (int i = 0; i < tcpAlgos.length; i++) {
                    if (tcpAlgos[i].equals(fTcpCong)) { spinnerTcpCong.setSelection(i); break; }
                }

                // CPU Freq spinners
                cpuFreqListMhz = fFreqList;
                if (fFreqList.length > 0) {
                    String[] labels = new String[fFreqList.length];
                    for (int i = 0; i < fFreqList.length; i++)
                        labels[i] = fFreqList[i] + " MHz";

                    spinnerCpuMaxFreq.setAdapter(makeAdapter(labels));
                    spinnerCpuMinFreq.setAdapter(makeAdapter(labels));

                    selectFreqInSpinner(spinnerCpuMaxFreq, fFreqList, fSavedMax);
                    selectFreqInSpinner(spinnerCpuMinFreq, fFreqList, fSavedMin);

                    if (tvCpuMaxCurrent != null)
                        tvCpuMaxCurrent.setText(fSavedMax > 0 ? fSavedMax + " MHz" : "\u2014");
                    if (tvCpuMinCurrent != null)
                        tvCpuMinCurrent.setText(fSavedMin > 0 ? fSavedMin + " MHz" : "\u2014");
                } else {
                    String[] fallback = {"3200 MHz","3000 MHz","2800 MHz","2600 MHz","2400 MHz",
                                         "2200 MHz","2000 MHz","1800 MHz","1600 MHz","1200 MHz","800 MHz"};
                    spinnerCpuMaxFreq.setAdapter(makeAdapter(fallback));
                    spinnerCpuMinFreq.setAdapter(makeAdapter(fallback));
                    spinnerCpuMinFreq.setSelection(fallback.length - 1);
                }

                // Restore switch states
                switchTcpBbr.setChecked(PrefsManager.isTcpBbrEnabled());
                switchZram.setChecked(PrefsManager.isZramEnabled());
                switchDoze.setChecked(PrefsManager.isAggressiveDoze());
                switchSync.setChecked(PrefsManager.isSyncDisabled());

                // Pasang spinner listeners SETELAH semua adapter & selection selesai
                spinnerReady = true;
                attachSpinnerListeners(fAvailGov, schedulers, tcpAlgos);
            });
        }).start();
    }

    private void attachSpinnerListeners(String[] govList, String[] schedList, String[] tcpList) {
        spinnerGovernor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!spinnerReady) return;
                String gov = govList[pos];
                PrefsManager.setSavedGovernor(gov);
                tvCpuGovernorCurrent.setText("Current: " + gov);
                applyGovernor(gov);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        spinnerIoSched.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!spinnerReady) return;
                String sched = schedList[pos];
                PrefsManager.setSavedIoScheduler(sched);
                tvIoSchedulerCurrent.setText("Current: " + sched);
                applyIoScheduler(sched);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        spinnerTcpCong.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!spinnerReady) return;
                applyTcpCongestion(tcpList[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        spinnerCpuMaxFreq.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!spinnerReady) return;
                int mhz = getSelectedMhz(spinnerCpuMaxFreq);
                if (mhz > 0) {
                    PrefsManager.setCpuMaxFreqMhz(mhz);
                    if (tvCpuMaxCurrent != null) tvCpuMaxCurrent.setText(mhz + " MHz");
                    applyCpuMaxFreq(mhz);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        spinnerCpuMinFreq.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!spinnerReady) return;
                int mhz = getSelectedMhz(spinnerCpuMinFreq);
                if (mhz > 0) {
                    PrefsManager.setCpuMinFreqMhz(mhz);
                    if (tvCpuMinCurrent != null) tvCpuMinCurrent.setText(mhz + " MHz");
                    applyCpuMinFreq(mhz);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    // ─── Apply individual settings ───────────────────────────────────────────

    private void applyGovernor(String gov) {
        new Thread(() -> {
            RootUtils.setCpuGovernor(gov);
        }).start();
    }

    private void applyIoScheduler(String sched) {
        new Thread(() -> {
            RootUtils.setIoScheduler(sched);
        }).start();
    }

    private void applySwappiness(int value) {
        new Thread(() -> {
            RootUtils.setSwappiness(value);
        }).start();
    }

    private void applyTcpCongestion(String algo) {
        new Thread(() -> {
            RootUtils.setTcpCongestion(algo);
        }).start();
    }

    private void applyCpuMaxFreq(int mhz) {
        new Thread(() -> {
            long khz = (long) mhz * 1_000L;
            int cores = RootUtils.getNumCpuCores();
            for (int c = 0; c < cores; c++)
                RootUtils.runCommand("echo " + khz + " > /sys/devices/system/cpu/cpu" + c
                        + "/cpufreq/scaling_max_freq 2>/dev/null");
        }).start();
    }

    private void applyCpuMinFreq(int mhz) {
        new Thread(() -> {
            long khz = (long) mhz * 1_000L;
            int cores = RootUtils.getNumCpuCores();
            for (int c = 0; c < cores; c++)
                RootUtils.runCommand("echo " + khz + " > /sys/devices/system/cpu/cpu" + c
                        + "/cpufreq/scaling_min_freq 2>/dev/null");
        }).start();
    }

    private void applyTcpBbr(boolean enabled) {
        new Thread(() -> {
            if (enabled)
                RootUtils.runCommand("echo bbr > /proc/sys/net/ipv4/tcp_congestion_control 2>/dev/null");
        }).start();
    }

    private void applyZram(boolean enabled) {
        new Thread(() -> {
            if (enabled)
                RootUtils.runCommand("swapoff /dev/block/zram0 2>/dev/null; " +
                        "echo 536870912 > /sys/block/zram0/disksize; " +
                        "mkswap /dev/block/zram0; swapon /dev/block/zram0");
            else
                RootUtils.runCommand("swapoff /dev/block/zram0 2>/dev/null");
        }).start();
    }

    private void applyDoze(boolean enabled) {
        new Thread(() -> {
            if (enabled)
                RootUtils.runCommand("dumpsys deviceidle force-idle");
        }).start();
    }

    private void applySync(boolean enabled) {
        new Thread(() -> {
            // sync disabled = echo 0 > /proc/sys/vm/dirty_writeback_centisecs (workaround)
            RootUtils.runCommand("echo " + (enabled ? "0" : "500") +
                    " > /proc/sys/vm/dirty_writeback_centisecs 2>/dev/null");
        }).start();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String[] parseAvailFreqs(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new String[0];
        String[] tokens = raw.trim().split("\\s+");
        java.util.TreeSet<Integer> set = new java.util.TreeSet<>(java.util.Collections.reverseOrder());
        for (String t : tokens) {
            try {
                long val = Long.parseLong(t.trim());
                int mhz = val > 100_000 ? (int)(val / 1_000) : (int)val;
                if (mhz > 0) set.add(mhz);
            } catch (NumberFormatException ignored) {}
        }
        String[] result = new String[set.size()];
        int i = 0;
        for (int mhz : set) result[i++] = String.valueOf(mhz);
        return result;
    }

    private int parseKhzToMhz(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0;
        try {
            long kHz = Long.parseLong(raw.trim().split("\\s+")[0]);
            return (int)(kHz / 1_000);
        } catch (Exception e) { return 0; }
    }

    private void selectFreqInSpinner(Spinner spinner, String[] freqList, int targetMhz) {
        if (targetMhz <= 0 || freqList.length == 0) return;
        int best = 0, bestDiff = Integer.MAX_VALUE;
        for (int i = 0; i < freqList.length; i++) {
            try {
                int diff = Math.abs(Integer.parseInt(freqList[i]) - targetMhz);
                if (diff < bestDiff) { bestDiff = diff; best = i; }
            } catch (NumberFormatException ignored) {}
        }
        spinner.setSelection(best);
    }

    private int getSelectedMhz(Spinner spinner) {
        if (spinner.getSelectedItem() == null) return 0;
        try {
            return Integer.parseInt(spinner.getSelectedItem().toString().replace(" MHz", "").trim());
        } catch (Exception e) { return 0; }
    }
}
