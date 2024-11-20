package org.example;

public class Main {
    public static void main(String[] args) {
//        CreationVpc creationVpc = new CreationVpc();
//        creationVpc.setupInfrastructure();
        DeleteVpc deleteVpc = new DeleteVpc();
        deleteVpc.cleanup();
    }
}