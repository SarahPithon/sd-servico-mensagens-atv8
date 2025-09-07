package br.com.servico_mensagens;

import java.util.Arrays;


public class Cliente3 {
    // Inicia o Cliente3 na porta 8003 e conecta com as portas 8001 e 8002
    public static void main(String[] args) {

        Cliente cliente = new Cliente("Cliente3", 8003, Arrays.asList(8001, 8002));
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nEncerrando Cliente3...");
            cliente.parar();
        }));
        
        try {
            cliente.iniciar();
        } catch (Exception e) {
            System.err.println("Erro no Cliente3: " + e.getMessage());
            e.printStackTrace();
        }
    }
}