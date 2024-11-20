package org.example;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

public class CreationVpc {
    private final Ec2Client ec2Client;
    private final String region = "us-east-1";
    private final String vpcCidr = "192.168.0.0/16";
    private final String dmzCidr = "192.168.1.0/24";
    private final String lanCidr = "192.168.2.0/24";
    private final String keyName = "key2";
    private final String amiId = "ami-012967cc5a8c9f891";

    public CreationVpc() {
        this.ec2Client = Ec2Client.builder()
                .region(Region.of(region))
                .build();
    }

    public void setupInfrastructure() {
        try {
            // Créer le VPC
            String vpcId = createVPC();
            System.out.println("VPC créé avec l'ID: " + vpcId);

            // Créer les sous-réseaux
            String dmzSubnetId = createSubnet(vpcId, dmzCidr, region + "a");
            System.out.println("Sous-réseau DMZ créé avec l'ID: " + dmzSubnetId);

            String lanSubnetId = createSubnet(vpcId, lanCidr, region + "b");
            System.out.println("Sous-réseau LAN créé avec l'ID: " + lanSubnetId);

            // Créer et attacher la passerelle Internet
            String igwId = createAndAttachInternetGateway(vpcId);
            System.out.println("Passerelle Internet créée et attachée avec l'ID: " + igwId);

            // Configurer les tables de routage
            String publicRouteTableId = createPublicRouteTable(vpcId, igwId, dmzSubnetId);
            String privateRouteTableId = createPrivateRouteTable(vpcId, lanSubnetId);

            // Créer les instances
            String dmzInstanceId = createEC2Instance(dmzSubnetId, true);
            System.out.println("Instance DMZ créée avec l'ID: " + dmzInstanceId);

            String lanInstanceId = createEC2Instance(lanSubnetId, false);
            System.out.println("Instance LAN créée avec l'ID: " + lanInstanceId);

            // Créer et configurer la passerelle NAT
            String natGatewayId = createNatGateway(dmzSubnetId);
            System.out.println("Passerelle NAT créée avec l'ID: " + natGatewayId);

            // Attendre que la passerelle NAT soit disponible
            waitForNatGateway(natGatewayId);

            // Ajouter la route NAT à la table de routage privée
            addNatGatewayRoute(privateRouteTableId, natGatewayId);

            System.out.println("Configuration du réseau VPC terminée avec succès!");

        } catch (Exception e) {
            System.err.println("Erreur lors de la configuration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String createVPC() {
        CreateVpcRequest request = CreateVpcRequest.builder()
                .cidrBlock(vpcCidr)
                .build();

        CreateVpcResponse response = ec2Client.createVpc(request);
        return response.vpc().vpcId();
    }

    private String createSubnet(String vpcId, String cidrBlock, String az) {
        CreateSubnetRequest request = CreateSubnetRequest.builder()
                .vpcId(vpcId)
                .cidrBlock(cidrBlock)
                .availabilityZone(az)
                .build();

        CreateSubnetResponse response = ec2Client.createSubnet(request);
        return response.subnet().subnetId();
    }

    private String createAndAttachInternetGateway(String vpcId) {
        CreateInternetGatewayRequest createRequest = CreateInternetGatewayRequest.builder().build();
        CreateInternetGatewayResponse createResponse = ec2Client.createInternetGateway(createRequest);
        String igwId = createResponse.internetGateway().internetGatewayId();

        AttachInternetGatewayRequest attachRequest = AttachInternetGatewayRequest.builder()
                .internetGatewayId(igwId)
                .vpcId(vpcId)
                .build();

        ec2Client.attachInternetGateway(attachRequest);
        return igwId;
    }

    private String createPublicRouteTable(String vpcId, String igwId, String subnetId) {
        CreateRouteTableRequest createRequest = CreateRouteTableRequest.builder()
                .vpcId(vpcId)
                .build();

        CreateRouteTableResponse response = ec2Client.createRouteTable(createRequest);
        String routeTableId = response.routeTable().routeTableId();

        CreateRouteRequest routeRequest = CreateRouteRequest.builder()
                .routeTableId(routeTableId)
                .destinationCidrBlock("0.0.0.0/0")
                .gatewayId(igwId)
                .build();

        ec2Client.createRoute(routeRequest);

        AssociateRouteTableRequest associateRequest = AssociateRouteTableRequest.builder()
                .subnetId(subnetId)
                .routeTableId(routeTableId)
                .build();

        ec2Client.associateRouteTable(associateRequest);

        // Correction de la partie problématique
        ModifySubnetAttributeRequest modifyRequest = ModifySubnetAttributeRequest.builder()
                .subnetId(subnetId)
                .mapPublicIpOnLaunch(AttributeBooleanValue.builder().value(true).build())
                .build();

        ec2Client.modifySubnetAttribute(modifyRequest);

        return routeTableId;
    }

    private String createPrivateRouteTable(String vpcId, String subnetId) {
        CreateRouteTableRequest createRequest = CreateRouteTableRequest.builder()
                .vpcId(vpcId)
                .build();

        CreateRouteTableResponse response = ec2Client.createRouteTable(createRequest);
        String routeTableId = response.routeTable().routeTableId();

        AssociateRouteTableRequest associateRequest = AssociateRouteTableRequest.builder()
                .subnetId(subnetId)
                .routeTableId(routeTableId)
                .build();

        ec2Client.associateRouteTable(associateRequest);

        return routeTableId;
    }

    private String createEC2Instance(String subnetId, boolean isPublic) {
        RunInstancesRequest request = RunInstancesRequest.builder()
                .imageId(amiId)
                .instanceType(InstanceType.T2_MICRO)
                .maxCount(1)
                .minCount(1)
                .keyName(keyName)
                .subnetId(subnetId)
                .build();

        RunInstancesResponse response = ec2Client.runInstances(request);
        return response.instances().get(0).instanceId();
    }

    private String createNatGateway(String subnetId) {
        AllocateAddressRequest allocateRequest = AllocateAddressRequest.builder()
                .domain(DomainType.VPC)
                .build();

        AllocateAddressResponse allocateResponse = ec2Client.allocateAddress(allocateRequest);

        CreateNatGatewayRequest request = CreateNatGatewayRequest.builder()
                .subnetId(subnetId)
                .allocationId(allocateResponse.allocationId())
                .build();

        CreateNatGatewayResponse response = ec2Client.createNatGateway(request);
        return response.natGateway().natGatewayId();
    }

    private void waitForNatGateway(String natGatewayId) {
        boolean isAvailable = false;
        while (!isAvailable) {
            DescribeNatGatewaysRequest request = DescribeNatGatewaysRequest.builder()
                    .natGatewayIds(natGatewayId)
                    .build();

            DescribeNatGatewaysResponse response = ec2Client.describeNatGateways(request);
            NatGatewayState state = response.natGateways().get(0).state();

            if (state == NatGatewayState.AVAILABLE) {
                isAvailable = true;
            } else {
                try {
                    Thread.sleep(20000); // Attendre 20 secondes avant de vérifier à nouveau
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void addNatGatewayRoute(String routeTableId, String natGatewayId) {
        CreateRouteRequest request = CreateRouteRequest.builder()
                .routeTableId(routeTableId)
                .destinationCidrBlock("0.0.0.0/0")
                .natGatewayId(natGatewayId)
                .build();

        ec2Client.createRoute(request);
}
}
