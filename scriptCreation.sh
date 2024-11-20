#!/bin/bash

# Variables
VPC_CIDR="192.168.0.0/16"
DMZ_CIDR="192.168.1.0/24"
LAN_CIDR="192.168.2.0/24"
REGION="us-east-1"
KEY_NAME="key2"

# Creer le VPC
VPC_ID=$(aws ec2 create-vpc --cidr-block $VPC_CIDR --region $REGION --query 'Vpc.VpcId' --output text)
echo "VPC cree avec l'ID: $VPC_ID"

# Creer le sous-reseau DMZ (public)
DMZ_SUBNET_ID=$(aws ec2 create-subnet --vpc-id $VPC_ID --cidr-block $DMZ_CIDR --availability-zone ${REGION}a --query 'Subnet.SubnetId' --output text)
echo "Sous-reseau DMZ cree avec l'ID: $DMZ_SUBNET_ID"

# Creer le sous-reseau LAN (prive)
LAN_SUBNET_ID=$(aws ec2 create-subnet --vpc-id $VPC_ID --cidr-block $LAN_CIDR --availability-zone ${REGION}b --query 'Subnet.SubnetId' --output text)
echo "Sous-reseau LAN cree avec l'ID: $LAN_SUBNET_ID"

# Creer une passerelle Internet
IGW_ID=$(aws ec2 create-internet-gateway --query 'InternetGateway.InternetGatewayId' --output text)
echo "Passerelle Internet creee avec l'ID: $IGW_ID"

# Attacher la passerelle Internet au VPC
aws ec2 attach-internet-gateway --vpc-id $VPC_ID --internet-gateway-id $IGW_ID
echo "Passerelle Internet attachee au VPC"

# Creer une table de routage publique et associer le sous-reseau DMZ
RTB_PUBLIC_ID=$(aws ec2 create-route-table --vpc-id $VPC_ID --query 'RouteTable.RouteTableId' --output text)
aws ec2 create-route --route-table-id $RTB_PUBLIC_ID --destination-cidr-block 0.0.0.0/0 --gateway-id $IGW_ID
aws ec2 associate-route-table --subnet-id $DMZ_SUBNET_ID --route-table-id $RTB_PUBLIC_ID
aws ec2 modify-subnet-attribute --subnet-id $DMZ_SUBNET_ID --map-public-ip-on-launch
echo "Table de routage publique creee et associee au sous-reseau DMZ"

# Creer une table de routage privee et associer le sous-reseau LAN
RTB_PRIVATE_ID=$(aws ec2 create-route-table --vpc-id $VPC_ID --query 'RouteTable.RouteTableId' --output text)
aws ec2 associate-route-table --subnet-id $LAN_SUBNET_ID --route-table-id $RTB_PRIVATE_ID
echo "Table de routage privee creee et associee au sous-reseau LAN"

# Creer une instance dans le sous-reseau DMZ
DMZ_INSTANCE_ID=$(aws ec2 run-instances --image-id ami-012967cc5a8c9f891 --count 1 --instance-type t2.micro --key-name $KEY_NAME --subnet-id $DMZ_SUBNET_ID --associate-public-ip-address --query 'Instances[0].InstanceId' --output text)
echo "Instance DMZ creee avec l'ID: $DMZ_INSTANCE_ID"

# Creer une instance dans le sous-reseau LAN
LAN_INSTANCE_ID=$(aws ec2 run-instances --image-id ami-012967cc5a8c9f891 --count 1 --instance-type t2.micro --key-name $KEY_NAME --subnet-id $LAN_SUBNET_ID --query 'Instances[0].InstanceId' --output text)
echo "Instance LAN creee avec l'ID: $LAN_INSTANCE_ID"

# Creer une passerelle NAT
NAT_GATEWAY_ID=$(aws ec2 create-nat-gateway --subnet-id $DMZ_SUBNET_ID --allocation-id $(aws ec2 allocate-address --query 'AllocationId' --output text) --query 'NatGateway.NatGatewayId' --output text)
echo "Passerelle NAT creee avec l'ID: $NAT_GATEWAY_ID"

# Attendre que la passerelle NAT soit disponible
aws ec2 wait nat-gateway-available --nat-gateway-ids $NAT_GATEWAY_ID
echo "Passerelle NAT disponible"

# Mettre à jour la table de routage privee pour utiliser la passerelle NAT
aws ec2 create-route --route-table-id $RTB_PRIVATE_ID --destination-cidr-block 0.0.0.0/0 --nat-gateway-id $NAT_GATEWAY_ID
echo "Route ajoutee à la table de routage privee pour utiliser la passerelle NAT"

echo "Configuration du reseau VPC terminee"
