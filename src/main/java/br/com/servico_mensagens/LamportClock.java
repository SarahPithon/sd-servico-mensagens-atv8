package br.com.servico_mensagens;

import java.util.concurrent.atomic.AtomicInteger;


public class LamportClock {
    private final AtomicInteger clock;
    
    public LamportClock() {
        this.clock = new AtomicInteger(0);
    }
    
    public LamportClock(int valorInicial) {
        this.clock = new AtomicInteger(valorInicial);
    }
    
    public int tick() {
        return clock.incrementAndGet();
    }
    
    public int update(int timestampRecebido) {
        int valorAtual;
        int novoValor;
        
        do {
            valorAtual = clock.get();
            novoValor = Math.max(valorAtual, timestampRecebido) + 1;
        } while (!clock.compareAndSet(valorAtual, novoValor));
        
        return novoValor;
    }
    
    public int getTime() {
        return clock.get();
    }
    
    public void setTime(int novoValor) {
        clock.set(novoValor);
    }
    
    @Override
    public String toString() {
        return "LamportClock{" + "time=" + clock.get() + '}';
    }
}