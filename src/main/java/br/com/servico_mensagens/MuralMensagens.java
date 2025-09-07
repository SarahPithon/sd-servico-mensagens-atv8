package br.com.servico_mensagens;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class MuralMensagens {
    private final String arquivoJson;
    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Cria um mural para um cliente específico e prepara o arquivo JSON
    public MuralMensagens(String nomeCliente) {
        this.arquivoJson = nomeCliente + "_mural.json";
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        inicializarArquivoJson();
    }
    
    // Cria o arquivo JSON do mural se ele não existir ainda
    private void inicializarArquivoJson() {
        File arquivo = new File(arquivoJson);
        if (!arquivo.exists()) {
            try {
                ObjectNode muralNode = objectMapper.createObjectNode();
                muralNode.put("contador", 0);
                muralNode.set("mensagens", objectMapper.createArrayNode());
                objectMapper.writeValue(arquivo, muralNode);
            } catch (IOException e) {
                System.err.println("Erro ao criar arquivo JSON: " + e.getMessage());
            }
        }
     }
     
    // Lê o conteúdo do arquivo JSON do mural
    private JsonNode lerArquivoJson() {
        try {
            File arquivo = new File(arquivoJson);
            if (arquivo.exists()) {
                return objectMapper.readTree(arquivo);
            }
        } catch (IOException e) {
            System.err.println("Erro ao ler arquivo JSON: " + e.getMessage());
        }
        return null;
    }
    
    // Salva o conteúdo no arquivo JSON do mural
    private void escreverArquivoJson(JsonNode conteudo) {
        try {
            objectMapper.writeValue(new File(arquivoJson), conteudo);
        } catch (IOException e) {
            System.err.println("Erro ao escrever arquivo JSON: " + e.getMessage());
        }
    }
     
    // Adiciona uma nova mensagem no mural e salva no arquivo
    public void adicionarMensagem(Mensagem mensagem) {
        lock.writeLock().lock();
        try {
            JsonNode muralNode = lerArquivoJson();
            if (muralNode != null) {
                ObjectNode mural = (ObjectNode) muralNode;
                ArrayNode mensagens = (ArrayNode) mural.get("mensagens");
                
                ObjectNode novaMensagem = objectMapper.createObjectNode();
                novaMensagem.put("id", mensagem.getId());
                novaMensagem.put("conteudo", mensagem.getConteudo());
                novaMensagem.put("autor", mensagem.getAutor());
                novaMensagem.put("timestamp", mensagem.getTimestamp());
                novaMensagem.put("lamportClock", mensagem.getLamportClock());
                novaMensagem.put("tipo", mensagem.getTipo());
                mensagens.add(novaMensagem);
                int novoContador = mural.get("contador").asInt() + 1;
                mural.put("contador", novoContador);
            
                escreverArquivoJson(mural);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // Adiciona várias mensagens de uma vez, evitando duplicatas
    public void adicionarMensagens(List<Mensagem> novasMensagens) {
        lock.writeLock().lock();
        try {
            JsonNode muralNode = lerArquivoJson();
            if (muralNode != null) {
                ObjectNode mural = (ObjectNode) muralNode;
                ArrayNode mensagens = (ArrayNode) mural.get("mensagens");
                int contador = mural.get("contador").asInt();
                
                for (Mensagem mensagem : novasMensagens) {
                    if (!mensagemExisteNoJson(mensagens, mensagem.getId())) {
                        ObjectNode novaMensagem = objectMapper.createObjectNode();
                        novaMensagem.put("id", mensagem.getId());
                        novaMensagem.put("conteudo", mensagem.getConteudo());
                        novaMensagem.put("autor", mensagem.getAutor());
                        novaMensagem.put("timestamp", mensagem.getTimestamp());
                        novaMensagem.put("lamportClock", mensagem.getLamportClock());
                        novaMensagem.put("tipo", mensagem.getTipo());
                        mensagens.add(novaMensagem);
                        contador++;
                    }
                }
                mural.put("contador", contador);
                escreverArquivoJson(mural);
            }
        } finally {
            lock.writeLock().unlock();
        }
     }
     

    // Verifica se uma mensagem já existe no mural pelo ID
    private boolean mensagemExisteNoJson(ArrayNode mensagens, String id) {
        for (JsonNode mensagem : mensagens) {
            if (mensagem.get("id").asText().equals(id)) {
                return true;
            }
        }
        return false;
    }
     

    // Substitui todo o mural com mensagens de outro cliente 
    public void substituirMural(List<Map<String, Object>> mensagensData, int novoContador) {
        lock.writeLock().lock();
        try {
            ObjectNode novoMural = objectMapper.createObjectNode();
            ArrayNode novasMensagens = objectMapper.createArrayNode();
            
            for (Map<String, Object> msgData : mensagensData) {
                ObjectNode mensagem = objectMapper.createObjectNode();
                mensagem.put("id", (String) msgData.get("id"));
                mensagem.put("conteudo", (String) msgData.get("conteudo"));
                mensagem.put("autor", (String) msgData.get("autor"));
                mensagem.put("timestamp", (String) msgData.get("timestamp"));
                mensagem.put("lamportClock", (Integer) msgData.get("lamportClock"));
                novasMensagens.add(mensagem);
            }
            novoMural.put("contador", novoContador);
            novoMural.set("mensagens", novasMensagens);
            escreverArquivoJson(novoMural);
        } finally {
            lock.writeLock().unlock();
        }
    }
    

    // Pega mensagens a partir de uma posição específica 
    public List<Mensagem> getMensagensAPartirDe(int contadorInicial) {
        lock.readLock().lock();
        try {
            List<Mensagem> resultado = new ArrayList<>();
            JsonNode muralNode = lerArquivoJson();
            if (muralNode != null) {
                ArrayNode mensagens = (ArrayNode) muralNode.get("mensagens");
                for (int i = contadorInicial; i < mensagens.size(); i++) {
                    JsonNode msgNode = mensagens.get(i);
                    Mensagem mensagem = new Mensagem(
                        msgNode.get("id").asText(),
                        msgNode.get("conteudo").asText(),
                        msgNode.get("autor").asText(),
                        msgNode.get("lamportClock").asInt()
                    );
                    mensagem.setTimestamp(msgNode.get("timestamp").asText());
                    resultado.add(mensagem);
                }
            }
            return resultado;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // Pega mensagens filtradas por tipo (publica ou privada)
    public List<Mensagem> getMensagensPorTipo(String tipo) {
        lock.readLock().lock();
        try {
            List<Mensagem> resultado = new ArrayList<>();
            JsonNode muralNode = lerArquivoJson();
            if (muralNode != null) {
                ArrayNode mensagens = (ArrayNode) muralNode.get("mensagens");
                for (JsonNode msgNode : mensagens) {
                    String tipoMensagem = msgNode.has("tipo") ? msgNode.get("tipo").asText() : "publica";
                    if (tipo.equals(tipoMensagem)) {
                        Mensagem mensagem = new Mensagem(
                            msgNode.get("id").asText(),
                            msgNode.get("conteudo").asText(),
                            msgNode.get("autor").asText(),
                            msgNode.get("lamportClock").asInt(),
                            tipoMensagem
                        );
                        mensagem.setTimestamp(msgNode.get("timestamp").asText());
                        resultado.add(mensagem);
                    }
                }
            }
            return resultado;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void salvar() {

    }
    
    // Pega quantas mensagens tem no mural
    public int getContador() {
        lock.readLock().lock();
        try {
            JsonNode muralNode = lerArquivoJson();
            if (muralNode != null) {
                return muralNode.get("contador").asInt();
            }
            return 0;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // Pega todas as mensagens do mural
    public List<Mensagem> getMensagens() {
        lock.readLock().lock();
        try {
            List<Mensagem> resultado = new ArrayList<>();
            JsonNode muralNode = lerArquivoJson();
            if (muralNode != null) {
                ArrayNode mensagens = (ArrayNode) muralNode.get("mensagens");
                for (JsonNode msgNode : mensagens) {
                    String tipoMensagem = msgNode.has("tipo") ? msgNode.get("tipo").asText() : "publica";
                    Mensagem mensagem = new Mensagem(
                        msgNode.get("id").asText(),
                        msgNode.get("conteudo").asText(),
                        msgNode.get("autor").asText(),
                        msgNode.get("lamportClock").asInt(),
                        tipoMensagem
                    );
                    mensagem.setTimestamp(msgNode.get("timestamp").asText());
                    resultado.add(mensagem);
                }
            }
            return resultado;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // Transforma o mural em uma string para mostrar no console
    @Override
    public String toString() {
        lock.readLock().lock();
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("=== MURAL DE MENSAGENS ===").append("\n");
            
            JsonNode muralNode = lerArquivoJson();
            if (muralNode != null) {
                sb.append("Contador: ").append(muralNode.get("contador").asInt()).append("\n");
                sb.append("Mensagens:").append("\n");
                
                ArrayNode mensagens = (ArrayNode) muralNode.get("mensagens");
                for (JsonNode msgNode : mensagens) {
                    Mensagem mensagem = new Mensagem(
                        msgNode.get("id").asText(),
                        msgNode.get("conteudo").asText(),
                        msgNode.get("autor").asText(),
                        msgNode.get("lamportClock").asInt()
                    );
                    mensagem.setTimestamp(msgNode.get("timestamp").asText());
                    sb.append("  ").append(mensagem.toString()).append("\n");
                }
            } else {
                sb.append("Contador: 0\n");
                sb.append("Mensagens: Nenhuma\n");
            }
            return sb.toString();
        } finally {
            lock.readLock().unlock();
        }
    }
}