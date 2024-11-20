package org.cloudbus.cloudsim.examples;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.text.DecimalFormat;
import java.util.*;

public class ResponseTimeSimulation {
    // Configuration constants
    private static final int HOST_MIPS = 1000;
    private static final int HOST_RAM = 2048;
    private static final long HOST_STORAGE = 1000000;
    private static final int HOST_BW = 10000;
    private static final int VM_COUNT = 5;

    // Métriques de simulation
    private static class SimulationMetrics {
        double averageResponseTime;
        double resourceUtilization;
        double cost;

        public SimulationMetrics(double art) {
            this.averageResponseTime = art;

        }
    }

    public static void main(String[] args) {
        Log.printLine("Démarrage de la simulation CloudSim...");

        try {
            // Scénarios de charge de travail
            int[] workloads = {200, 500, 1000, 2000};
            Map<Integer, SimulationMetrics> results = new HashMap<>();

            for (int workload : workloads) {
                results.put(workload, runSimulation(workload));
            }

            // Afficher les résultats comparatifs
            printComparisonResults(results);

        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Erreur pendant la simulation : " + e.getMessage());
        }
    }

    private static SimulationMetrics runSimulation(int requestCount) throws Exception {
        // Initialisation de CloudSim
        CloudSim.init(1, Calendar.getInstance(), false);

        // Créer le datacenter
        Datacenter datacenter = createDatacenter();
        DatacenterBroker broker = createBroker();

        // Configuration des VMs
        List<Vm> vms = createVMs(broker.getId());
        broker.submitVmList(vms);

        // Création et soumission des cloudlets
        List<Cloudlet> cloudlets = createCloudlets(broker.getId(), requestCount);
        broker.submitCloudletList(cloudlets);

        // Exécuter la simulation
        double startTime = CloudSim.clock();
        CloudSim.startSimulation();

        // Collecter les résultats
        List<Cloudlet> completedCloudlets = broker.getCloudletReceivedList();
        CloudSim.stopSimulation();

        // Calculer et retourner les métriques
        return calculateMetrics(completedCloudlets, vms, startTime);
    }

    private static Datacenter createDatacenter() throws Exception {
        List<Host> hostList = new ArrayList<>();
        List<Pe> peList = new ArrayList<>();

        // Configuration du PE (Processing Element)
        peList.add(new Pe(0, new PeProvisionerSimple(HOST_MIPS)));

        // Configuration de l'hôte
        Host host = new Host(
                0,
                new RamProvisionerSimple(HOST_RAM),
                new BwProvisionerSimple(HOST_BW),
                HOST_STORAGE,
                peList,
                new VmSchedulerTimeShared(peList)
        );
        hostList.add(host);

        // Caractéristiques du datacenter
        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                "x86", "Linux", "Xen", hostList, 10.0,
                3.0, 0.05, 0.1, 0.1
        );

        return new Datacenter(
                "Datacenter_Basic",
                characteristics,
                new VmAllocationPolicySimple(hostList),
                new LinkedList<Storage>(),
                0
        );
    }

    private static List<Vm> createVMs(int brokerId) {
        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < VM_COUNT; i++) {
            Vm vm = new Vm(
                    i, brokerId, HOST_MIPS,
                    1, 512, 1000,
                    10000, "Xen",
                    new CloudletSchedulerTimeShared()
            );
            vms.add(vm);
        }
        return vms;
    }

    private static List<Cloudlet> createCloudlets(int brokerId, int count) {
        List<Cloudlet> cloudlets = new ArrayList<>();
        UtilizationModel utilizationModel = new UtilizationModelFull();

        for (int i = 0; i < count; i++) {
            Cloudlet cloudlet = new Cloudlet(
                    i, 1000, 1,
                    300, 300,
                    utilizationModel,
                    utilizationModel,
                    utilizationModel
            );
            cloudlet.setUserId(brokerId);
            cloudlets.add(cloudlet);
        }
        return cloudlets;
    }

    private static SimulationMetrics calculateMetrics(List<Cloudlet> cloudlets, List<Vm> vms, double startTime) {
        double totalResponseTime = 0;

        for (Cloudlet cloudlet : cloudlets) {
            totalResponseTime += cloudlet.getFinishTime() - cloudlet.getSubmissionTime();

        }

        return new SimulationMetrics(
                totalResponseTime / cloudlets.size()
                                        );
    }

    private static double calculateCloudletCost(Cloudlet cloudlet) {
        // Coût par unité de temps = 3.0 (défini dans les caractéristiques du datacenter)
        return cloudlet.getActualCPUTime() * 3.0;
    }

    private static void printComparisonResults(Map<Integer, SimulationMetrics> results) {
        DecimalFormat df = new DecimalFormat("#.##");

        Log.printLine("\n========== RÉSULTATS DE LA SIMULATION ==========");
        Log.printLine("Workload | Temps Réponse ");
        Log.printLine("--------------------------------------------");

        for (Map.Entry<Integer, SimulationMetrics> entry : results.entrySet()) {
            SimulationMetrics metrics = entry.getValue();
            Log.printLine(String.format("%8d | %12s ",
                    entry.getKey(),
                    df.format(metrics.averageResponseTime)
                   ));
        }
    }

    private static DatacenterBroker createBroker() throws Exception {
        return new DatacenterBroker("Broker");
    }
}