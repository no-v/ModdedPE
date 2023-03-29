package com.mojang.minecraftpe.hardwareinfo;

import android.util.Log;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 29.03.2023
 *
 * @author Тимашков Иван
 * @author https://github.com/TimScriptov
 */
public class CPUTopologyInfo {
    private static final Pattern CPU_LIST_VALUE_FORMAT = Pattern.compile("(\\d{1,4})(?:-(\\d{1,4}))?");
    private static final CPUTopologyInfo SINGLETON_INSTANCE = new CPUTopologyInfo();
    private Set<CPUCluster> CLUSTERS;
    private List<SystemCPU> CPUS;
    private BitSet CPUS_BITSET;
    private int CPU_PROCESSOR_COUNT;

    public interface BitCollector {
        void setBit(int bitIndex);
    }

    public static CPUTopologyInfo getInstance() {
        return SINGLETON_INSTANCE;
    }

    private CPUTopologyInfo() {
        try {
            initializeCPUInformation();
            this.CLUSTERS = getClustersFromSiblingCPUs();
        } catch (Exception unused) {
            Log.w("ModdedPE", "Failed to initialize CPU topology information");
            this.CPU_PROCESSOR_COUNT = 0;
            this.CPUS_BITSET = new BitSet();
            this.CPUS = new ArrayList<>();
            this.CLUSTERS = new TreeSet<>();
        }
    }

    private void initializeCPUInformation() throws Exception {
        File file = new File("/sys/devices/system/cpu/present");
        int i = 0;
        if (!file.exists() || !file.canRead()) {
            this.CPU_PROCESSOR_COUNT = 0;
            this.CPUS_BITSET = new BitSet();
            this.CPUS = new ArrayList<>();
        }
        String readLine = new BufferedReader(new FileReader(file)).readLine();
        BitSetCollector bitSetCollector = new BitSetCollector();
        parseCPUListString(readLine, bitSetCollector);
        this.CPU_PROCESSOR_COUNT = bitSetCollector.getBitCount();
        this.CPUS = new ArrayList<>(this.CPU_PROCESSOR_COUNT);
        this.CPUS_BITSET = bitSetCollector.getBitSet();
        while (true) {
            int nextSetBit = this.CPUS_BITSET.nextSetBit(i);
            if (nextSetBit < 0) {
                return;
            }
            this.CPUS.add(new SystemCPU(nextSetBit));
            i = nextSetBit + 1;
        }
    }

    @NonNull
    private Set<CPUCluster> getClustersFromSiblingCPUs() {
        TreeSet<CPUCluster> treeSet = new TreeSet<>();
        List<SystemCPU> cpus = getCPUS();
        ArrayDeque<SystemCPU> arrayDeque = new ArrayDeque<>(cpus.size());
        arrayDeque.addAll(cpus);
        while (!arrayDeque.isEmpty()) {
            SystemCPU systemCPU = (SystemCPU) arrayDeque.poll();
            if (systemCPU != null && systemCPU.exists()) {
                systemCPU.updateCPUFreq();
                String siblingString = systemCPU.getSiblingString();
                Set<SystemCPU> cpuSetFromCPUListString = cpuSetFromCPUListString(siblingString);
                if (!cpuSetFromCPUListString.isEmpty()) {
                    for (SystemCPU systemCPU2 : cpuSetFromCPUListString) {
                        if (systemCPU2 != systemCPU) {
                            systemCPU2.updateCPUFreq();
                        }
                        arrayDeque.remove(systemCPU2);
                    }
                    treeSet.add(new CPUCluster(siblingString, cpuSetFromCPUListString));
                }
            }
        }
        return treeSet;
    }

    public List<SystemCPU> getCPUS() {
        return this.CPUS;
    }

    public int getCPUCount() {
        return this.CPU_PROCESSOR_COUNT;
    }

    public Set<SystemCPU> cpuSetFromCPUListString(@NonNull String siblingInfo) {
        final TreeSet<SystemCPU> treeSet = new TreeSet<>();
        if (siblingInfo.isEmpty()) {
            return treeSet;
        }
        parseCPUListString(siblingInfo, i -> {
            treeSet.add(CPUS.get(i));
        });
        return treeSet;
    }

    public static class BitSetCollector implements BitCollector {
        private int bitsCounted = 0;
        private final BitSet bits = new BitSet();

        int getBitCount() {
            return this.bitsCounted;
        }

        BitSet getBitSet() {
            return this.bits;
        }

        @Override
        public void setBit(int bitIndex) {
            this.bits.set(bitIndex);
            this.bitsCounted++;
        }
    }

    @Contract("_, _ -> param2")
    public static <T extends BitCollector> T parseCPUListString(@NonNull String listString, T collector) {
        String[] split;
        if (listString.isEmpty()) {
            return collector;
        }
        for (String str : listString.split(",")) {
            Matcher matcher = CPU_LIST_VALUE_FORMAT.matcher(str);
            if (matcher.find()) {
                int parseInt = Integer.parseInt(matcher.group(1));
                String group = matcher.group(2);
                if (group != null && !group.isEmpty()) {
                    int parseInt2 = Integer.parseInt(group);
                    while (parseInt <= parseInt2) {
                        collector.setBit(parseInt);
                        parseInt++;
                    }
                } else {
                    collector.setBit(parseInt);
                }
            } else {
                Log.w("ModdedPE", "Unknown CPU List format: " + str);
            }
        }
        return collector;
    }

    public Set<CPUCluster> getClusterSet() {
        return new TreeSet<>(this.CLUSTERS);
    }

    public CPUCluster[] getClusterArray() {
        return (CPUCluster[]) this.CLUSTERS.toArray(new CPUCluster[0]);
    }

    public boolean isMultiClusterSystem() {
        return this.CLUSTERS.size() > 1;
    }

    public int getCPUClusterCount() {
        return this.CLUSTERS.size();
    }
}