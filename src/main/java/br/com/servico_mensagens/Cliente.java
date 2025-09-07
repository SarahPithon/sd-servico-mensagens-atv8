package br.com.servico_mensagens;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class Cliente {
    private final String nome;
    private final int porta;
    private final List<Integer> portasOutrosClientes;
    private final MuralMensagens mural;
    private final LamportClock lamportClock;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean ativo;
    private final ExecutorService executorService;
    
    private ServerSocket serverSocket;
    private Scanner scanner;
    private String senhaUsuario;
    private String nickUsuario;
    private boolean autenticado;
    private final Set<Integer> conexoesAtivas;
    
    // Cria um novo cliente com nome, porta e lista de portas dos outros clientes
    public Cliente(String nome, int porta, List<Integer> portasOutrosClientes) {
        this.nome = nome;
        this.porta = porta;
        this.portasOutrosClientes = new ArrayList<>(portasOutrosClientes);
        this.mural = new MuralMensagens(nome);
        this.lamportClock = new LamportClock();
        this.objectMapper = new ObjectMapper();
        this.ativo = new AtomicBoolean(false);
        this.executorService = Executors.newCachedThreadPool();
        this.scanner = new Scanner(System.in);
        this.conexoesAtivas = new HashSet<>();
        this.autenticado = false;
        this.nickUsuario = null;
        this.senhaUsuario = null;
    }
    

    // Inicia o cliente: servidor, sincronização e interface do usuário
    public void iniciar() {
        try {
            ativo.set(true);
            
            iniciarServidor();
            solicitarSincronizacao();
            iniciarInterfaceUsuario();
            
        } catch (Exception e) {
            System.err.println("Erro ao iniciar cliente: " + e.getMessage());
            parar();
        }
    }
    
    // Cria o servidor TCP que vai escutar conexões de outros clientes
    private void iniciarServidor() throws IOException {
        serverSocket = new ServerSocket(porta);
        System.out.println(nome + " iniciado na porta " + porta);

        executorService.submit(() -> {
            while (ativo.get() && !serverSocket.isClosed()) {
                try {
                    Socket clienteSocket = serverSocket.accept();

                    executorService.submit(() -> processarConexao(clienteSocket));
                } catch (IOException e) {
                    if (ativo.get()) {
                        System.err.println("Erro ao aceitar conexão: " + e.getMessage());
                    }
                }
            }
        });
    }
    
    // Processa uma conexão recebida de outro cliente
    private void processarConexao(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
            
            String linha = reader.readLine();
            if (linha != null) {
                Map<String, Object> dados = objectMapper.readValue(linha, Map.class);
                String tipo = (String) dados.get("tipo");
                
                switch (tipo) {
                    case "NOVA_MENSAGEM":
                        processarNovaMensagem(dados);
                        writer.println("OK");
                        break;
                    case "SOLICITAR_SINCRONIZACAO":
                        processarSolicitacaoSincronizacao(dados, writer);
                        break;
                    case "RESPOSTA_SINCRONIZACAO":
                        processarRespostaSincronizacao(dados);
                        break;
                    case "SOLICITAR_MURAL_COMPLETO":
                        processarSolicitacaoMuralCompleto(dados, writer);
                        break;
                    case "RESPOSTA_MURAL_COMPLETO":
                        processarRespostaMuralCompleto(dados);
                        break;
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao processar conexão: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {}
        }
    }
    
    // Processa uma nova mensagem recebida de outro cliente
    private void processarNovaMensagem(Map<String, Object> dados) {
        try {
            Map<String, Object> mensagemData = (Map<String, Object>) dados.get("mensagem");
            int timestampRecebido = (Integer) dados.get("lamportClock");
            lamportClock.update(timestampRecebido);
            
            String tipoMensagem = mensagemData.containsKey("tipo") ? (String) mensagemData.get("tipo") : "publica";
            Mensagem mensagem = new Mensagem(
                (String) mensagemData.get("id"),
                (String) mensagemData.get("conteudo"),
                (String) mensagemData.get("autor"),
                (Integer) mensagemData.get("lamportClock"),
                tipoMensagem
            );
            mensagem.setTimestamp((String) mensagemData.get("timestamp"));
            mural.adicionarMensagem(mensagem);
            
            System.out.println("\n[NOVA MENSAGEM RECEBIDA] " + mensagem);
            System.out.print("Digite uma mensagem (ou 'sair' para encerrar): ");
            
        } catch (Exception e) {
            System.err.println("Erro ao processar nova mensagem: " + e.getMessage());
        }
    }
    
    // Responde a uma solicitação de sincronização enviando mensagens faltantes
    private void processarSolicitacaoSincronizacao(Map<String, Object> dados, PrintWriter writer) {
        try {
            int contadorSolicitante = (Integer) dados.get("contador");
            
            Map<String, Object> resposta = new HashMap<>();
            resposta.put("tipo", "RESPOSTA_SINCRONIZACAO");
            resposta.put("contador", mural.getContador());
            resposta.put("mensagens", mural.getMensagensAPartirDe(contadorSolicitante));
            resposta.put("remetente", nome);
            writer.println(objectMapper.writeValueAsString(resposta));
            
        } catch (Exception e) {
            System.err.println("Erro ao processar solicitação de sincronização: " + e.getMessage());
        }
    }
    
    // Processa a resposta de sincronização e adiciona mensagens faltantes
    private void processarRespostaSincronizacao(Map<String, Object> dados) {
        try {
            int contadorRemetente = (Integer) dados.get("contador");
            String remetente = (String) dados.get("remetente");
            
            if (contadorRemetente > mural.getContador()) {
                List<Map<String, Object>> mensagensData = (List<Map<String, Object>>) dados.get("mensagens");
                List<Mensagem> mensagens = new ArrayList<>();
                
                for (Map<String, Object> msgData : mensagensData) {
                    Mensagem mensagem = new Mensagem(
                        (String) msgData.get("id"),
                        (String) msgData.get("conteudo"),
                        (String) msgData.get("autor"),
                        (Integer) msgData.get("lamportClock")
                    );
                    mensagem.setTimestamp((String) msgData.get("timestamp"));
                    mensagens.add(mensagem);
                }
                mural.adicionarMensagens(mensagens);
                System.out.println("\n[SINCRONIZAÇÃO] Recebidas " + mensagens.size() + " mensagens de " + remetente);
            }
        } catch (Exception e) {
            System.err.println("Erro ao processar resposta de sincronização: " + e.getMessage());
        }
    }

    // Solicita sincronização com todos os outros clientes para pegar mensagens perdidas
    private void solicitarSincronizacao() {
        System.out.println("\n[SINCRONIZAÇÃO] Verificando murais de outros clientes...");
        Map<String, Map<String, Object>> respostasRecebidas = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(portasOutrosClientes.size());
        
        for (int portaOutro : portasOutrosClientes) {
            executorService.submit(() -> {
                try {
                    Map<String, Object> solicitacao = new HashMap<>();
                    solicitacao.put("tipo", "SOLICITAR_MURAL_COMPLETO");
                    solicitacao.put("contador", mural.getContador());
                    solicitacao.put("remetente", nome);
                    

                    Map<String, Object> resposta = enviarMensagemComResposta(portaOutro, solicitacao);
                    if (resposta != null) {
                        respostasRecebidas.put("Cliente_" + portaOutro, resposta);
                    }
                } catch (Exception e) {

                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        analisarEAtualizarMural(respostasRecebidas);
    }

    // Responde com o mural completo quando outro cliente solicita
    private void processarSolicitacaoMuralCompleto(Map<String, Object> dados, PrintWriter writer) {
        try {
            Map<String, Object> resposta = new HashMap<>();
            resposta.put("tipo", "RESPOSTA_MURAL_COMPLETO");
            resposta.put("contador", mural.getContador());
            resposta.put("mensagens", mural.getMensagens());
            resposta.put("remetente", nome);
            writer.println(objectMapper.writeValueAsString(resposta));
            
        } catch (Exception e) {
            System.err.println("Erro ao processar solicitação de mural completo: " + e.getMessage());
        }
    }

    // Processa a resposta do mural completo e substitui o mural local se necessário
    private void processarRespostaMuralCompleto(Map<String, Object> dados) {
        try {
            int contadorRemetente = (Integer) dados.get("contador");
            String remetente = (String) dados.get("remetente");
            
            if (contadorRemetente > mural.getContador()) {
                List<Map<String, Object>> mensagensData = (List<Map<String, Object>>) dados.get("mensagens");
                mural.substituirMural(mensagensData, contadorRemetente);

                System.out.println("\n[SINCRONIZAÇÃO COMPLETA] Mural atualizado com " + contadorRemetente + " mensagens de " + remetente);
                System.out.println("Seu mural foi sincronizado com o mais recente disponível.");
            }
            
        } catch (Exception e) {
             System.err.println("Erro ao processar resposta de mural completo: " + e.getMessage());
         }
     }

     // Envia uma mensagem para outro cliente e espera uma resposta
     private Map<String, Object> enviarMensagemComResposta(int porta, Map<String, Object> dados) {
         try (Socket socket = new Socket("localhost", porta);
              PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
              BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
             
             writer.println(objectMapper.writeValueAsString(dados));
             String resposta = reader.readLine();
             
             if (resposta != null && !resposta.equals("OK")) {
                 return objectMapper.readValue(resposta, Map.class);
             }
         } catch (Exception e) {}
         return null;
     }

     // Analisa as respostas de sincronização e atualiza o mural com o mais recente
     private void analisarEAtualizarMural(Map<String, Map<String, Object>> respostas) {
         if (respostas.isEmpty()) {
             System.out.println("[SINCRONIZAÇÃO] Nenhum cliente respondeu. Mantendo mural local.");
             return;
         }
         
         String melhorCliente = null;
         int maiorContador = mural.getContador();
         Map<String, Object> melhorResposta = null;
         
         for (Map.Entry<String, Map<String, Object>> entry : respostas.entrySet()) {
             Map<String, Object> resposta = entry.getValue();
             int contador = (Integer) resposta.get("contador");
             
             if (contador > maiorContador) {
                 maiorContador = contador;
                 melhorCliente = entry.getKey();
                 melhorResposta = resposta;
             }
         }

         if (melhorResposta != null) {
             List<Map<String, Object>> mensagensData = (List<Map<String, Object>>) melhorResposta.get("mensagens");
             mural.substituirMural(mensagensData, maiorContador);
             
             System.out.println("[SINCRONIZAÇÃO] Mural atualizado com " + maiorContador + " mensagens de " + melhorCliente);
         } else {
             System.out.println("[SINCRONIZAÇÃO] Mural local está atualizado.");
         }
     }
     
    // Faz o login do usuário com nick e senha
    private void realizarAutenticacao() {
        if (autenticado) {
            System.out.println("\nVocê já está autenticado como: " + nickUsuario);
            System.out.print("Deseja fazer login com outro usuário? (s/n): ");
            String resposta = scanner.nextLine().trim().toLowerCase();
            if (!resposta.equals("s") && !resposta.equals("sim")) {
                return;
            }
        }
        System.out.println("\n=== AUTENTICAÇÃO - " + nome.toUpperCase() + " ===");
        
        while (true) {
            System.out.print("Digite seu nick: ");
            String nick = scanner.nextLine().trim();
            
            if (nick.isEmpty()) {
                System.out.println("Nick não pode estar vazio. Tente novamente.");
                continue;
            }
            
            this.nickUsuario = nick;
            break;
        }
        
        while (true) {
            System.out.print("Digite sua senha: ");
            String senha = scanner.nextLine().trim();
            
            if (senha.isEmpty()) {
                System.out.println("Senha não pode estar vazia. Tente novamente.");
                continue;
            }
            
            System.out.print("Confirme sua senha: ");
            String confirmacao = scanner.nextLine().trim();
            
            if (senha.equals(confirmacao)) {
                this.senhaUsuario = senha;
                this.autenticado = true;
                System.out.println("Autenticação realizada com sucesso!\n");
                System.out.println("Bem-vindo, " + nickUsuario + "!");
                return;
            } else {
                System.out.println("Senhas não coincidem. Tente novamente.\n");
            }
        }
    }
    
    // Inicia o menu principal do cliente
    private void iniciarInterfaceUsuario() {
        System.out.println("\n=== " + nome.toUpperCase() + " ===");
        
        while (ativo.get()) {
            exibirMenu();
            String opcao = scanner.nextLine().trim();
            
            switch (opcao) {
                case "1":
                    realizarAutenticacao();
                    break;
                case "2":
                    solicitarMensagem();
                    break;
                case "3":
                    verMural();
                    break;
                case "4":
                    extrairMuralPDF();
                    break;
                case "5":
                    conectarComOutrosClientes();
                    break;
                case "6":
                    System.out.println("Encerrando " + nome + "...");
                    return;
                default:
                    System.out.println("Opção inválida. Tente novamente.\n");
            }
        }
        parar();
    }
    
    // Mostra o menu de opções para o usuário
    private void exibirMenu() {
        System.out.println("\n=== MENU PRINCIPAL ===");
        if (autenticado) {
            System.out.println("[Usuário: " + nickUsuario + " - Autenticado]");
        } else {
            System.out.println("[Não autenticado]");
        }
        System.out.println("1. Fazer login/autenticação");
        System.out.println("2. Postar mensagem");
        System.out.println("3. Ver mural");
        System.out.println("4. Extrair mural em PDF");
        System.out.println("5. Conectar com outros clientes");
        System.out.println("6. Sair");
        System.out.print("Escolha uma opção: ");
    }
    
    // Exibe submenu para visualizar mensagens públicas ou privadas
    private void verMural() {
        System.out.println("\n=== VER MURAL ===");
        System.out.println("1. Ver mensagens públicas");
        System.out.println("2. Ver mensagens privadas");
        System.out.println("3. Ver todas as mensagens");
        System.out.print("Escolha uma opção: ");
        
        String opcao = scanner.nextLine().trim();
        
        switch (opcao) {
            case "1":
                exibirMensagensPorTipo("publica");
                break;
            case "2":
                if (!autenticado) {
                    System.out.println("\nVocê precisa estar autenticado para ver mensagens privadas.");
                    System.out.print("Deseja fazer login agora? (s/n): ");
                    String resposta = scanner.nextLine().trim().toLowerCase();
                    if (resposta.equals("s") || resposta.equals("sim")) {
                        realizarAutenticacao();
                        if (autenticado) {
                            exibirMensagensPorTipo("privada");
                        }
                    }
                } else {
                    exibirMensagensPorTipo("privada");
                }
                break;
            case "3":
                if (!autenticado) {
                    System.out.println("\nVocê precisa estar autenticado para ver todas as mensagens.");
                    System.out.print("Deseja fazer login agora? (s/n): ");
                    String resposta = scanner.nextLine().trim().toLowerCase();
                    if (resposta.equals("s") || resposta.equals("sim")) {
                        realizarAutenticacao();
                        if (autenticado) {
                            exibirTodasMensagens();
                        }
                    } else {
                        // Se não autenticado, mostra apenas as públicas
                        exibirMensagensPorTipo("publica");
                    }
                } else {
                    exibirTodasMensagens();
                }
                break;
            default:
                System.out.println("Opção inválida.");
        }
    }
    
    // Exibe mensagens filtradas por tipo
    private void exibirMensagensPorTipo(String tipo) {
        List<Mensagem> mensagens = mural.getMensagensPorTipo(tipo);
        if (mensagens.isEmpty()) {
            System.out.println("\nNenhuma mensagem " + tipo + " encontrada.");
        } else {
            System.out.println("\n=== MENSAGENS " + tipo.toUpperCase() + "S ===");
            for (Mensagem msg : mensagens) {
                System.out.println(msg.toString());
            }
        }
    }
    
    // Exibe todas as mensagens (públicas e privadas)
    private void exibirTodasMensagens() {
        System.out.println(mural.toString());
    }
    
    // Pede para o usuário digitar uma mensagem e a envia
    private void solicitarMensagem() {
        if (!autenticado) {
            System.out.println("\nVocê precisa estar autenticado para enviar mensagens.");
            System.out.print("Deseja fazer login agora? (s/n): ");
            String resposta = scanner.nextLine().trim().toLowerCase();
            if (resposta.equals("s") || resposta.equals("sim")) {
                realizarAutenticacao();
                if (!autenticado) {
                    return;
                }
            } else {
                return;
            }
        }
        System.out.print("\nDigite sua mensagem: ");
        String mensagem = scanner.nextLine().trim();
        
        if (mensagem.isEmpty()) {
            System.out.println("Mensagem não pode estar vazia.");
            return;
        }
        
        System.out.println("\nTipo de mensagem:");
        System.out.println("1. Pública (visível para todos)");
        System.out.println("2. Privada (apenas para usuários autenticados)");
        System.out.print("Escolha o tipo (1 ou 2): ");
        String tipoEscolha = scanner.nextLine().trim();
        
        String tipoMensagem = "publica"; // padrão
        if ("2".equals(tipoEscolha)) {
            tipoMensagem = "privada";
        } else if (!"1".equals(tipoEscolha)) {
            System.out.println("Opção inválida. Usando tipo público como padrão.");
        }
        
        System.out.print("Digite seu nick: ");
        String nickConfirmacao = scanner.nextLine().trim();
        System.out.print("Digite sua senha: ");
        String senhaConfirmacao = scanner.nextLine().trim();
        
        if (!nickConfirmacao.equals(this.nickUsuario) || !senhaConfirmacao.equals(this.senhaUsuario)) {
            System.out.println("Nick ou senha incorretos. Mensagem não enviada.");
            return;
        }
        postarMensagem(mensagem, tipoMensagem);
    }

    // Testa conexão com todos os outros clientes e atualiza lista de conexões ativas
    private void conectarComOutrosClientes() {
        System.out.println("\n=== CONECTAR COM OUTROS CLIENTES ===");
        conexoesAtivas.clear();
    
        for (int porta : portasOutrosClientes) {
            System.out.print("Testando conexão com porta " + porta + "... ");
            if (testarConexao(porta)) {
                conexoesAtivas.add(porta);
                System.out.println("✓ Conectado");
            } else {
                System.out.println("✗ Falhou");
            }
        }
        System.out.println("\nConexões ativas: " + conexoesAtivas.size() + "/" + portasOutrosClientes.size());
        if (!conexoesAtivas.isEmpty()) {
            System.out.println("Portas conectadas: " + conexoesAtivas);
        } else {
            System.out.println("Nenhuma conexão ativa. Mensagens serão armazenadas localmente.");
        }
    }

    // Gera um arquivo PDF com todas as mensagens do mural
    private void extrairMuralPDF() {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String nomeArquivo = nome + "_mural_" + timestamp + ".pdf";
            PdfWriter writer = new PdfWriter(nomeArquivo);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);
            
            Paragraph titulo = new Paragraph("MURAL DE MENSAGENS - " + nome.toUpperCase())
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(16)
                    .setBold();
            document.add(titulo);
            
            Paragraph dataGeracao = new Paragraph("Gerado em: " + 
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(10);
            document.add(dataGeracao);
            document.add(new Paragraph("\n" + "=".repeat(50) + "\n"));
            
            List<Mensagem> mensagens = mural.getMensagens();
            if (mensagens.isEmpty()) {
                document.add(new Paragraph("Nenhuma mensagem encontrada no mural."));
            } else {
                document.add(new Paragraph("Total de mensagens: " + mensagens.size() + "\n"));
                
                for (int i = 0; i < mensagens.size(); i++) {
                    Mensagem msg = mensagens.get(i);
                
                    Paragraph cabecalho = new Paragraph()
                            .add(new Text("Mensagem #" + (i + 1)).setBold())
                            .add(new Text(" | Autor: " + msg.getAutor()))
                            .add(new Text(" | Timestamp: " + msg.getTimestamp()))
                            .add(new Text(" | Clock: " + msg.getLamportClock()));
                    document.add(cabecalho);
                    
                    Paragraph conteudo = new Paragraph("Conteúdo: " + msg.getConteudo())
                            .setMarginLeft(20);
                    document.add(conteudo);
                    
                    Paragraph id = new Paragraph("ID: " + msg.getId())
                            .setMarginLeft(20)
                            .setFontSize(8)
                            .setItalic();
                    document.add(id);
                    
                    if (i < mensagens.size() - 1) {
                        document.add(new Paragraph("-".repeat(30)));
                    }
                }
            }
            document.close();
            System.out.println("\n✓ PDF gerado com sucesso: " + nomeArquivo);
            System.out.println("Arquivo salvo no diretório: " + System.getProperty("user.dir"));
            
        } catch (Exception e) {
            System.err.println("Erro ao gerar PDF: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Testa se consegue conectar com um cliente em uma porta específica
    private boolean testarConexao(int porta) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", porta), 2000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // Posta uma mensagem no mural e envia para outros clientes conectados
    private void postarMensagem(String conteudo, String tipo) {
        try {
            int timestamp = lamportClock.tick();
            String autor = autenticado ? nickUsuario : nome;
            String id = autor + "_" + timestamp + "_" + System.currentTimeMillis();
            Mensagem mensagem = new Mensagem(id, conteudo, autor, timestamp, tipo);
            mural.adicionarMensagem(mensagem);
            
            Map<String, Object> dados = new HashMap<>();
            dados.put("tipo", "NOVA_MENSAGEM");
            dados.put("mensagem", mensagem);
            dados.put("lamportClock", timestamp);
            dados.put("remetente", nome);
            
            if (conexoesAtivas.isEmpty()) {
                System.out.println("[AVISO] Nenhuma conexão ativa. Use a opção 3 do menu para conectar com outros clientes.");
            } else {
                for (int portaOutro : conexoesAtivas) {
                    executorService.submit(() -> {
                        try {
                            enviarMensagem(portaOutro, dados);
                        } catch (Exception e) {
                            System.err.println("Erro ao enviar mensagem para porta " + portaOutro + ": " + e.getMessage());
                            conexoesAtivas.remove(portaOutro);
                        }
                    });
                }
            }
            System.out.println("[MENSAGEM POSTADA] " + mensagem);
            
        } catch (Exception e) {
            System.err.println("Erro ao postar mensagem: " + e.getMessage());
        }
    }
    

    // Envia uma mensagem para outro cliente 
    private void enviarMensagem(int porta, Map<String, Object> dados) throws IOException {
        try (Socket socket = new Socket("localhost", porta);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            writer.println(objectMapper.writeValueAsString(dados));
            reader.readLine();
        }
    }
    
    // Para o cliente: fecha servidor, threads e salva o mural
    public void parar() {
        ativo.set(false);
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Erro ao fechar servidor: " + e.getMessage());
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
        mural.salvar();
        
        System.out.println(nome + " encerrado.");
    }
    
    public String getNome() {
        return nome;
    }
    
    public int getPorta() {
        return porta;
    }
    
    public MuralMensagens getMural() {
        return mural;
    }
    
    public LamportClock getLamportClock() {
        return lamportClock;
    }
}