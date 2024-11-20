package org.example;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

public class DeleteVpc {
    private final Ec2Client ec2Client;
    private final String region = "us-east-1";
    private final String vpcCidr = "192.168.0.0/16";
    private final String dmzCidr = "192.168.1.0/24";
    private final String lanCidr = "192.168.2.0/24";

    public DeleteVpc() {
        this.ec2Client = Ec2Client.builder()
                .region(Region.of(region))
                .build();
    }

    public void cleanup() {
        try {
            // Récupérer l'ID du VPC
            String vpcId = getVpcId();
            System.out.println("VPC ID trouvé: " + vpcId);

            if (vpcId == null) {
                System.out.println("Aucun VPC trouvé avec le CIDR " + vpcCidr);
                return;
            }

            // Récupérer les IDs des sous-réseaux
            String dmzSubnetId = getSubnetId(vpcId, dmzCidr);
            String lanSubnetId = getSubnetId(vpcId, lanCidr);
            System.out.println("Sous-réseaux trouvés - DMZ: " + dmzSubnetId + ", LAN: " + lanSubnetId);

            // Récupérer les IDs des instances
            String dmzInstanceId = getInstanceId(dmzSubnetId);
            String lanInstanceId = getInstanceId(lanSubnetId);
            System.out.println("Instances trouvées - DMZ: " + dmzInstanceId + ", LAN: " + lanInstanceId);

            // Récupérer l'ID de la passerelle NAT
            String natGatewayId = getNatGatewayId(vpcId);
            System.out.println("Passerelle NAT trouvée: " + natGatewayId);

            // Récupérer l'ID de l'adresse IP Elastic
            String elasticIpAllocationId = getElasticIpAllocationId(natGatewayId);
            System.out.println("Allocation ID de l'IP Elastic trouvée: " + elasticIpAllocationId);

            // Récupérer l'ID de la passerelle Internet
            String igwId = getInternetGatewayId(vpcId);
            System.out.println("Passerelle Internet trouvée: " + igwId);

            // Récupérer les IDs des tables de routage
            String publicRouteTableId = getRouteTableId(vpcId, dmzSubnetId);
            String privateRouteTableId = getRouteTableId(vpcId, lanSubnetId);
            System.out.println("Tables de routage trouvées - Publique: " + publicRouteTableId + ", Privée: " + privateRouteTableId);

            // Supprimer les instances
            terminateInstances(dmzInstanceId, lanInstanceId);

            // Supprimer la passerelle NAT
            deleteNatGateway(natGatewayId);

            // Libérer l'adresse IP Elastic
            if (elasticIpAllocationId != null) {
                releaseElasticIp(elasticIpAllocationId);
            }

            // Supprimer les sous-réseaux
            deleteSubnets(dmzSubnetId, lanSubnetId);

            // Supprimer les tables de routage
            deleteRouteTables(publicRouteTableId, privateRouteTableId);

            // Détacher et supprimer la passerelle Internet
            if (igwId != null) {
                detachAndDeleteInternetGateway(igwId, vpcId);
            }

            // Supprimer le VPC
            deleteVpc(vpcId);

            System.out.println("Suppression du réseau VPC terminée");

        } catch (Exception e) {
            System.err.println("Erreur lors du nettoyage: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getVpcId() {
        DescribeVpcsRequest request = DescribeVpcsRequest.builder()
                .filters(Filter.builder()
                        .name("cidr-block")
                        .values(vpcCidr)
                        .build())
                .build();

        DescribeVpcsResponse response = ec2Client.describeVpcs(request);
        if (!response.vpcs().isEmpty()) {
            return response.vpcs().get(0).vpcId();
        }
        return null;
    }

    private String getSubnetId(String vpcId, String cidrBlock) {
        DescribeSubnetsRequest request = DescribeSubnetsRequest.builder()
                .filters(
                        Filter.builder().name("vpc-id").values(vpcId).build(),
                        Filter.builder().name("cidr-block").values(cidrBlock).build()
                )
                .build();

        DescribeSubnetsResponse response = ec2Client.describeSubnets(request);
        if (!response.subnets().isEmpty()) {
            return response.subnets().get(0).subnetId();
        }
        return null;
    }

    private String getInstanceId(String subnetId) {
        if (subnetId == null) return null;

        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .filters(
                        Filter.builder().name("subnet-id").values(subnetId).build(),
                        Filter.builder().name("instance-state-name").values("running").build()
                )
                .build();

        DescribeInstancesResponse response = ec2Client.describeInstances(request);
        if (!response.reservations().isEmpty() && !response.reservations().get(0).instances().isEmpty()) {
            return response.reservations().get(0).instances().get(0).instanceId();
        }
        return null;
    }

    private String getNatGatewayId(String vpcId) {
        DescribeNatGatewaysRequest request = DescribeNatGatewaysRequest.builder()
                .filter(Filter.builder()
                        .name("vpc-id")
                        .values(vpcId)
                        .build())
                .build();

        DescribeNatGatewaysResponse response = ec2Client.describeNatGateways(request);
        if (!response.natGateways().isEmpty()) {
            return response.natGateways().get(0).natGatewayId();
        }
        return null;
    }

    private String getElasticIpAllocationId(String natGatewayId) {
        if (natGatewayId == null) return null;

        DescribeNatGatewaysRequest request = DescribeNatGatewaysRequest.builder()
                .natGatewayIds(natGatewayId)
                .build();

        DescribeNatGatewaysResponse response = ec2Client.describeNatGateways(request);
        if (!response.natGateways().isEmpty() && !response.natGateways().get(0).natGatewayAddresses().isEmpty()) {
            return response.natGateways().get(0).natGatewayAddresses().get(0).allocationId();
        }
        return null;
    }

    private String getInternetGatewayId(String vpcId) {
        DescribeInternetGatewaysRequest request = DescribeInternetGatewaysRequest.builder()
                .filters(Filter.builder()
                        .name("attachment.vpc-id")
                        .values(vpcId)
                        .build())
                .build();

        DescribeInternetGatewaysResponse response = ec2Client.describeInternetGateways(request);
        if (!response.internetGateways().isEmpty()) {
            return response.internetGateways().get(0).internetGatewayId();
        }
        return null;
    }

    private String getRouteTableId(String vpcId, String subnetId) {
        DescribeRouteTablesRequest request = DescribeRouteTablesRequest.builder()
                .filters(
                        Filter.builder().name("vpc-id").values(vpcId).build(),
                        Filter.builder().name("association.subnet-id").values(subnetId).build()
                )
                .build();

        DescribeRouteTablesResponse response = ec2Client.describeRouteTables(request);
        if (!response.routeTables().isEmpty()) {
            return response.routeTables().get(0).routeTableId();
        }
        return null;
    }

    private void terminateInstances(String... instanceIds) {
        for (String instanceId : instanceIds) {
            if (instanceId != null) {
                try {
                    TerminateInstancesRequest request = TerminateInstancesRequest.builder()
                            .instanceIds(instanceId)
                            .build();
                    ec2Client.terminateInstances(request);
                    waitForInstanceTermination(instanceId);
                } catch (Exception e) {
                    System.err.println("Erreur lors de la suppression de l'instance " + instanceId + ": " + e.getMessage());
                }
            }
        }
    }

    private void waitForInstanceTermination(String instanceId) throws InterruptedException {
        boolean isTerminated = false;
        while (!isTerminated) {
            DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                    .instanceIds(instanceId)
                    .build();
            DescribeInstancesResponse response = ec2Client.describeInstances(request);

            if (!response.reservations().isEmpty() && !response.reservations().get(0).instances().isEmpty()) {
                InstanceState state = response.reservations().get(0).instances().get(0).state();
                if (state.name() == InstanceStateName.TERMINATED) {
                    isTerminated = true;
                } else {
                    Thread.sleep(5000); // Attendre 5 secondes avant de vérifier à nouveau
                }
            } else {
                isTerminated = true;
            }
        }
    }

    private void deleteNatGateway(String natGatewayId) {
        if (natGatewayId != null) {
            try {
                DeleteNatGatewayRequest request = DeleteNatGatewayRequest.builder()
                        .natGatewayId(natGatewayId)
                        .build();
                ec2Client.deleteNatGateway(request);
                waitForNatGatewayDeletion(natGatewayId);
            } catch (Exception e) {
                System.err.println("Erreur lors de la suppression de la passerelle NAT: " + e.getMessage());
            }
        }
    }

    private void waitForNatGatewayDeletion(String natGatewayId) throws InterruptedException {
        boolean isDeleted = false;
        while (!isDeleted) {
            DescribeNatGatewaysRequest request = DescribeNatGatewaysRequest.builder()
                    .natGatewayIds(natGatewayId)
                    .build();
            DescribeNatGatewaysResponse response = ec2Client.describeNatGateways(request);

            if (response.natGateways().isEmpty() ||
                    response.natGateways().get(0).state() == NatGatewayState.DELETED) {
                isDeleted = true;
            } else {
                Thread.sleep(5000); // Attendre 5 secondes avant de vérifier à nouveau
            }
        }
    }

    private void releaseElasticIp(String allocationId) {
        try {
            ReleaseAddressRequest request = ReleaseAddressRequest.builder()
                    .allocationId(allocationId)
                    .build();
            ec2Client.releaseAddress(request);
        } catch (Exception e) {
            System.err.println("Erreur lors de la libération de l'adresse IP Elastic: " + e.getMessage());
        }
    }

    private void deleteSubnets(String... subnetIds) {
        for (String subnetId : subnetIds) {
            if (subnetId != null) {
                try {
                    DeleteSubnetRequest request = DeleteSubnetRequest.builder()
                            .subnetId(subnetId)
                            .build();
                    ec2Client.deleteSubnet(request);
                } catch (Exception e) {
                    System.err.println("Erreur lors de la suppression du sous-réseau " + subnetId + ": " + e.getMessage());
                }
            }
        }
    }

    private void deleteRouteTables(String... routeTableIds) {
        for (String routeTableId : routeTableIds) {
            if (routeTableId != null) {
                try {
                    DeleteRouteTableRequest request = DeleteRouteTableRequest.builder()
                            .routeTableId(routeTableId)
                            .build();
                    ec2Client.deleteRouteTable(request);
                } catch (Exception e) {
                    System.err.println("Erreur lors de la suppression de la table de routage " + routeTableId + ": " + e.getMessage());
                }
            }
        }
    }

    private void detachAndDeleteInternetGateway(String internetGatewayId, String vpcId) {
        try {
            DetachInternetGatewayRequest detachRequest = DetachInternetGatewayRequest.builder()
                    .internetGatewayId(internetGatewayId)
                    .vpcId(vpcId)
                    .build();
            ec2Client.detachInternetGateway(detachRequest);

            DeleteInternetGatewayRequest deleteRequest = DeleteInternetGatewayRequest.builder()
                    .internetGatewayId(internetGatewayId)
                    .build();
            ec2Client.deleteInternetGateway(deleteRequest);
        } catch (Exception e) {
            System.err.println("Erreur lors de la suppression de la passerelle Internet: " + e.getMessage());
        }
    }

    private void deleteVpc(String vpcId) {
        try {
            DeleteVpcRequest request = DeleteVpcRequest.builder()
                    .vpcId(vpcId)
                    .build();
            ec2Client.deleteVpc(request);
        } catch (Exception e) {
            System.err.println("Erreur lors de la suppression du VPC: " + e.getMessage());
        }
    }
}
