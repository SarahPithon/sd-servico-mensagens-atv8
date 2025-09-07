package br.com.servico_mensagens;

import java.util.Arrays;


public class Cliente1 {
    // Inicia o Cliente1 na porta 8001 e conecta com as portas 8002 e 8003
    public static void main(String[] args) {

        Cliente cliente = new Cliente("Cliente1", 8001, Arrays.asList(8002, 8003));
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nEncerrando Cliente1...");
            cliente.parar();
        }));
        
        try {
            cliente.iniciar();
        } catch (Exception e) {
            System.err.println("Erro no Cliente1: " + e.getMessage());
            e.printStackTrace();
        }
    }
}