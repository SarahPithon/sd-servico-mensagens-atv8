package br.com.servico_mensagens;

import java.util.Arrays;


public class Cliente2 {
    // Inicia o Cliente2 na porta 8002 e conecta com as portas 8001 e 8003
    public static void main(String[] args) {

        Cliente cliente = new Cliente("Cliente2", 8002, Arrays.asList(8001, 8003));
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nEncerrando Cliente2...");
            cliente.parar();
        }));
        
        try {
            cliente.iniciar();
        } catch (Exception e) {
            System.err.println("Erro no Cliente2: " + e.getMessage());
            e.printStackTrace();
        }
    }
}