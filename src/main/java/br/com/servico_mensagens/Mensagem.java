package br.com.servico_mensagens;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class Mensagem {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("conteudo")
    private String conteudo;
    
    @JsonProperty("autor")
    private String autor;
    
    @JsonProperty("timestamp")
    private String timestamp;
    
    @JsonProperty("lamportClock")
    private int lamportClock;
    
    @JsonProperty("tipo")
    private String tipo; 

    public Mensagem() {}
    
    public Mensagem(String id, String conteudo, String autor, int lamportClock) {
        this.id = id;
        this.conteudo = conteudo;
        this.autor = autor;
        this.lamportClock = lamportClock;
        this.tipo = "publica"; 
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
    
    public Mensagem(String id, String conteudo, String autor, int lamportClock, String tipo) {
        this.id = id;
        this.conteudo = conteudo;
        this.autor = autor;
        this.lamportClock = lamportClock;
        this.tipo = tipo;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getConteudo() {
        return conteudo;
    }
    
    public void setConteudo(String conteudo) {
        this.conteudo = conteudo;
    }
    
    public String getAutor() {
        return autor;
    }
    
    public void setAutor(String autor) {
        this.autor = autor;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    public int getLamportClock() {
        return lamportClock;
    }
    
    public void setLamportClock(int lamportClock) {
        this.lamportClock = lamportClock;
    }
    
    public String getTipo() {
        return tipo;
    }
    
    public void setTipo(String tipo) {
        this.tipo = tipo;
    }
    
    @Override
    public String toString() {
        String tipoIndicador = "publica".equals(tipo) ? "[PÚBLICA]" : "[PRIVADA]";
        return String.format("%s [%d] %s (%s): %s", tipoIndicador, lamportClock, autor, timestamp, conteudo);
    }
}