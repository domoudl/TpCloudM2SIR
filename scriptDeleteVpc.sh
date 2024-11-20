#!/bin/bash

# Variables
REGION="us-east-1"

# Récupérer l'ID du VPC basé sur le CIDR
VPC_ID=$(aws ec2 describe-vpcs --filters "Name=cidr-block,Values=192.168.0.0/16" --query 'Vpcs[0].VpcId' --output text)
echo "VPC ID trouvé: $VPC_ID"

# Récupérer les IDs des sous-réseaux
DMZ_SUBNET_ID=$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=$VPC_ID" "Name=cidr-block,Values=192.168.1.0/24" --query 'Subnets[0].SubnetId' --output text)
LAN_SUBNET_ID=$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=$VPC_ID" "Name=cidr-block,Values=192.168.2.0/24" --query 'Subnets[0].SubnetId' --output text)
echo "Sous-réseaux trouvés - DMZ: $DMZ_SUBNET_ID, LAN: $LAN_SUBNET_ID"

# Récupérer les IDs des instances
DMZ_INSTANCE_ID=$(aws ec2 describe-instances --filters "Name=subnet-id,Values=$DMZ_SUBNET_ID" "Name=instance-state-name,Values=running" --query 'Reservations[0].Instances[0].InstanceId' --output text)
LAN_INSTANCE_ID=$(aws ec2 describe-instances --filters "Name=subnet-id,Values=$LAN_SUBNET_ID" "Name=instance-state-name,Values=running" --query 'Reservations[0].Instances[0].InstanceId' --output text)
echo "Instances trouvées - DMZ: $DMZ_INSTANCE_ID, LAN: $LAN_INSTANCE_ID"

# Récupérer l'ID de la passerelle NAT
NAT_GATEWAY_ID=$(aws ec2 describe-nat-gateways --filter "Name=vpc-id,Values=$VPC_ID" --query 'NatGateways[0].NatGatewayId' --output text)
echo "Passerelle NAT trouvée: $NAT_GATEWAY_ID"

# Récupérer l'ID de l'adresse IP Elastic associée à la passerelle NAT
ELASTIC_IP_ALLOCATION_ID=$(aws ec2 describe-nat-gateways --nat-gateway-ids $NAT_GATEWAY_ID --query 'NatGateways[0].NatGatewayAddresses[0].AllocationId' --output text)
echo "Allocation ID de l'IP Elastic trouvée: $ELASTIC_IP_ALLOCATION_ID"

# Récupérer l'ID de la passerelle Internet
IGW_ID=$(aws ec2 describe-internet-gateways --filters "Name=attachment.vpc-id,Values=$VPC_ID" --query 'InternetGateways[0].InternetGatewayId' --output text)
echo "Passerelle Internet trouvée: $IGW_ID"

# Récupérer les IDs des tables de routage
RTB_PUBLIC_ID=$(aws ec2 describe-route-tables --filters "Name=vpc-id,Values=$VPC_ID" "Name=association.subnet-id,Values=$DMZ_SUBNET_ID" --query 'RouteTables[0].RouteTableId' --output text)
RTB_PRIVATE_ID=$(aws ec2 describe-route-tables --filters "Name=vpc-id,Values=$VPC_ID" "Name=association.subnet-id,Values=$LAN_SUBNET_ID" --query 'RouteTables[0].RouteTableId' --output text)
echo "Tables de routage trouvées - Publique: $RTB_PUBLIC_ID, Privée: $RTB_PRIVATE_ID"

# Supprimer les instances
echo "Suppression des instances..."
if [ ! -z "$DMZ_INSTANCE_ID" ]; then
    aws ec2 terminate-instances --instance-ids $DMZ_INSTANCE_ID
fi
if [ ! -z "$LAN_INSTANCE_ID" ]; then
    aws ec2 terminate-instances --instance-ids $LAN_INSTANCE_ID
fi

# Attendre que les instances soient terminées
echo "Attente de la terminaison des instances..."
if [ ! -z "$DMZ_INSTANCE_ID" ]; then
    aws ec2 wait instance-terminated --instance-ids $DMZ_INSTANCE_ID
fi
if [ ! -z "$LAN_INSTANCE_ID" ]; then
    aws ec2 wait instance-terminated --instance-ids $LAN_INSTANCE_ID
fi

# Supprimer la passerelle NAT
echo "Suppression de la passerelle NAT..."
if [ ! -z "$NAT_GATEWAY_ID" ]; then
    aws ec2 delete-nat-gateway --nat-gateway-id $NAT_GATEWAY_ID
    echo "Attente de la suppression de la passerelle NAT..."
    aws ec2 wait nat-gateway-deleted --nat-gateway-ids $NAT_GATEWAY_ID
fi

# Libérer l'adresse IP Elastic
if [ ! -z "$ELASTIC_IP_ALLOCATION_ID" ]; then
    echo "Libération de l'adresse IP Elastic..."
    aws ec2 release-address --allocation-id $ELASTIC_IP_ALLOCATION_ID
fi

# Supprimer les sous-réseaux
echo "Suppression des sous-réseaux..."
if [ ! -z "$DMZ_SUBNET_ID" ]; then
    aws ec2 delete-subnet --subnet-id $DMZ_SUBNET_ID
fi
if [ ! -z "$LAN_SUBNET_ID" ]; then
    aws ec2 delete-subnet --subnet-id $LAN_SUBNET_ID
fi

# Supprimer les tables de routage
echo "Suppression des tables de routage..."
if [ ! -z "$RTB_PUBLIC_ID" ]; then
    aws ec2 delete-route-table --route-table-id $RTB_PUBLIC_ID
fi
if [ ! -z "$RTB_PRIVATE_ID" ]; then
    aws ec2 delete-route-table --route-table-id $RTB_PRIVATE_ID
fi

# Détacher et supprimer la passerelle Internet
echo "Suppression de la passerelle Internet..."
if [ ! -z "$IGW_ID" ]; then
    aws ec2 detach-internet-gateway --internet-gateway-id $IGW_ID --vpc-id $VPC_ID
    aws ec2 delete-internet-gateway --internet-gateway-id $IGW_ID
fi

# Supprimer le VPC
echo "Suppression du VPC..."
if [ ! -z "$VPC_ID" ]; then
    aws ec2 delete-vpc --vpc-id $VPC_ID
fi

echo "Suppression du réseau VPC terminée"