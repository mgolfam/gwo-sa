package org.fog.test.cuckoo;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.Helper;
import org.cloudbus.cloudsim.power.PlanetLabHelper;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.RunnerAbstract;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.entities.*;
import org.fog.placement.ModuleMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;
import utils.Constants;

import java.util.*;

public class Cuckoo_Run extends Solution {

    private int varh;
    private int dimension;
    private int Beta = 15;
    private int p = 2;
    static List<MyFogDevice> fogDevices = new ArrayList<MyFogDevice>();
    static Map<Integer, MyFogDevice> deviceById = new HashMap<Integer, MyFogDevice>();
    static List<MySensor> sensors = new ArrayList<MySensor>();
    static List<MyActuator> actuators = new ArrayList<MyActuator>();
    static List<Integer> idOfEndDevices = new ArrayList<Integer>();
    static Map<Integer, Map<String, Double>> deadlineInfo = new HashMap<Integer, Map<String, Double>>();
    static Map<Integer, Map<String, Integer>> additionalMipsInfo = new HashMap<Integer, Map<String, Integer>>();
    static boolean CLOUD = false;
    static int numOfGateways = 2;
    static int numOfEndDevPerGateway = 3;
    static double sensingInterval = 5;
    public double[] workFlowTaskExecution;
    public double[][] workFlowDataTrans;

    public class Nodes extends RunnerAbstract {

        public Nodes(boolean enableOutput, boolean outputToFile, String inputFolder, String outputFolder,
                     String workload, String vmAllocationPolicy,
                     String vmSelectionPolicy,
                     String parameter) {
            super(enableOutput, outputToFile, inputFolder, outputFolder, workload, vmAllocationPolicy,
                    vmSelectionPolicy, parameter);
        }

        @Override
        public void init(String inputFolder) {
            try {

                CloudSim.init(1, Calendar.getInstance(), false);
                broker = Helper.createBroker();
                int brokerId = broker.getId();
                cloudletList = PlanetLabHelper.createCloudletListPlanetLab(brokerId, inputFolder);
                vmList = Helper.createVmList(brokerId, cloudletList.size());
                hostList = Helper.createHostList(Constants.NUMBER_OF_Nodes);

            } catch (Exception e) {
                e.printStackTrace();
                Log.printLine("unexpected error");
                System.exit(0);
            }
        }
    }

    public Cuckoo_Run NumEggs(int n) {
        double rand1 = rand.nextDouble();
        double Eggs = ((Beta - p) * rand1 + p);
        ArrayList<Integer> varIndices = new ArrayList<Integer>(n);
        for (int i = 0; i < n; i++) {
            varIndices.add(i, i);
            dimension = i - 1;
        }
        ArrayList<Double> vars = this.getVars();
        Cuckoo_Run newSol = new Cuckoo_Run(n);
        newSol.initializeWithNull();
        ArrayList<Double> newVars = newSol.getVars();

        for (int i = 0; i < n; i++) {
            varh = n;
            int index = rand.nextInt(varIndices.size());
            int varIndex = varIndices.get(index);
            double curVar = vars.get(varIndex);
            double a = rand.nextDouble();
            double eggs = index / Eggs;
            double varStep = a * eggs * (varh - dimension);
            double newVar = curVar + varStep;
            newVars.set(varIndex, newVar);
            varIndices.remove(index);
        }
        return newSol;
    }

    @SuppressWarnings("static-access")
    public static void main(String[] args) {

        Log.printLine("Starting...");

        try {

            int user = 15;
            boolean trace_flag = false;
            String appId = "test_app";
            Calendar calendar = Calendar.getInstance();
            CloudSim.init(user, calendar, trace_flag);

            Log.printLine("Create Fog Devices...");
            FogBroker broker = new FogBroker("broker");
            createFogDevices(broker.getId(), appId);
            Log.printLine();
            Log.printLine("Create Application...");

            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            moduleMapping.addModuleToDevice("storageModule", "fog");

            for (int i = 0; i < idOfEndDevices.size(); i++) {
                MyFogDevice fogDevice = deviceById.get(idOfEndDevices.get(i));
                moduleMapping.addModuleToDevice("clientModule", fogDevice.getName());
            }

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
            boolean enableOutput = true;
            boolean outputToFile = false;
            String str = "mu";
            String inputFolder = Cuckoo_Run.class.getClassLoader().getResource("workload").getPath();
            String workload = "workload";
            String parameter = "1.2";
            String AllocationPolicy = "Cuckoo";
            String outputFolder = "output";

            Cuckoo_Run newRunners = new Cuckoo_Run(user);
            Cuckoo_Run.Nodes runner = newRunners.new Nodes(enableOutput, outputToFile, inputFolder, outputFolder,
                    workload, AllocationPolicy, str, parameter);

        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }

    private static void createFogDevices(int userId, String appId) {
        MyFogDevice fog = createFogDevice("fog", 44800, 40000, 100, 10000, 0, 0.01, 16 * 103, 16 * 83.25);
        fog.setParentId(-1);
        fogDevices.add(fog);
        deviceById.put(fog.getId(), fog);

        for (int i = 0; i < numOfGateways; i++) {
            addGw(i + "", userId, appId, fog.getId());
        }

    }

    private static void addGw(String gwPartialName, int userId, String appId, int parentId) {
        MyFogDevice gw = createFogDevice("g-" + gwPartialName, 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
        fogDevices.add(gw);
        deviceById.put(gw.getId(), gw);
        gw.setParentId(parentId);
        gw.setUplinkLatency(4);
        for (int i = 0; i < numOfEndDevPerGateway; i++) {
            String endPartialName = gwPartialName + "-" + i;
            MyFogDevice end = create(endPartialName, userId, appId, gw.getId());
            end.setUplinkLatency(2);
            fogDevices.add(end);
            deviceById.put(end.getId(), end);
        }

    }

    private static MyFogDevice create(String endPartialName, int userId, String appId, int parentId) {
        MyFogDevice end = createFogDevice("e-" + endPartialName, 3200, 1000, 10000, 270, 2, 0, 87.53, 82.44);
        end.setParentId(parentId);
        idOfEndDevices.add(end.getId());
        MySensor sensor = new MySensor("s-" + endPartialName, "IoTSensor", userId, appId,
                new DeterministicDistribution(sensingInterval)); // inter-transmission time of EEG sensor follows a
        // deterministic distribution
        sensors.add(sensor);
        MyActuator actuator = new MyActuator("a-" + endPartialName, userId, appId, "IoTActuator");
        actuators.add(actuator);
        sensor.setGatewayDeviceId(end.getId());
        sensor.setLatency(6.0);
        actuator.setGatewayDeviceId(end.getId());
        actuator.setLatency(1.0);
        return end;
    }

    private static MyFogDevice createFogDevice(String nodeName, long mips,
                                               int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
        List<Pe> peList = new ArrayList<Pe>();
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));
        int hostId = FogUtils.generateEntityId();
        long storage = 1000000;
        int bw = 10000;

        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower, idlePower));
        List<Host> hostList = new ArrayList<Host>();
        hostList.add(host);
        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
        LinkedList<Storage> storageList = new LinkedList<Storage>();
        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);

        MyFogDevice fogdevice = null;
        try {
            fogdevice = new MyFogDevice(nodeName, characteristics,
                    new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
        } catch (Exception e) {
            e.printStackTrace();
        }

        fogdevice.setLevel(level);
        fogdevice.setMips((int) mips);
        return fogdevice;
    }

    public Cuckoo_Run(ArrayList<Double> vars) {
        super(vars);
    }

    public Cuckoo_Run(int numVars) {
        super(numVars);
    }
}