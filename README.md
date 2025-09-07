# Sistema distribuído com mural de mensagens

Sistema distribuído com 3 nós (clientes) que implementa um mural de mensagens sincronizado usando sockets TCP, algoritmo de Lamport e persistência em JSON.

## Funcionalidades

- **Clientes**:

  - Foram criado 3 clientes, cada um é executado em uma porta diferente:
  - Cliente 1: 8001
  - Cliente 2: 8002
  - Cliente 3: 8003

- **Sistema de Autenticação**:

  - Para o usuário conseguir ter acesso de determinadas funcionalidades do sistema é necessário realizar um cadastro.

- **Validação de Credenciais**:

  - Na hora de realizar alguma funcionalidade que exige autenticação, o usuário digita as informações de cadastro e é liberado para utilizar a funcionalidade, caso as credenciais estejam corretas.

- **Conexão Manual**:

  - Por questões de didática e se tratar de um ambiente simulado, consideremos aplicar a conexão entre os clientes de maneira manual, no menu disponibilizado para o usuário tem uma opção que tem a funcionalidade de tentar conectar o cliente com os outros.

- **Mural Individual**:

  - Cada cliente possui seu próprio mural localmente, onde cada um tem a obrigação de auto gerenciar seu próprio mural, com objetivo de que todos os murais estejam condizentes.

- **Exportação PDF**:

  - Para ajudar com a questão visual, aplicamos a geração de PDF, a qualquer momento o usuário pode estar solicitando a cópia do mural, se tiver acesso as mensagens privadas serão 'impressas', mas se não tiver só vai mostrar as mensagens públicas.

- **Sincronização**:

  - Para conseguir deixar todos os murais individuais condizentes um com o outro, é utilizada conexões via sockets para que qualqauer mensagem adicionada mural, por qualquer cliente seja repassada para os outros murais, garantindo a consistência dos dados.

- **Algoritmo de Lamport**:

  - Por mais que não foi solicitada na atividade, adicionamos o relógio de lamport para ajudar na organização da mensagem e saber a ordem dos eventos.

- **Recuperação**:

  - Se algum cliente cair, a conexão dos demais continua funcionando normalmente e independente de quantas mensagens foram adicionadas no mural enquanto o cliente estava fora do ar, quando ele voltar, ele vai conseguir sincronizar seu mural com o dos outros, garantindo assim a consistência dos dados.

## Como utilizar o sistema

### 1. Compilar o Projeto

- Primeiramente é interessante compilar o projeto na sua máquina.

```bash
mvn clean compile
```

### 2. Executar os Clientes

- Após compilar execute os clientes, você pode utilizar atalhos da IDE que esteja utilizando, mas caso não esteja utilizando alguma IDE ou prefira executar em linha de código é só seguir os códigos abaixo:

Abra 3 terminais diferentes e execute cada cliente:

**Terminal 1 - Cliente1:**

```bash
mvn exec:java -Dexec.mainClass="br.com.servico_mensagens.Cliente1"
```

**Terminal 2 - Cliente2:**

```bash
mvn exec:java -Dexec.mainClass="br.com.servico_mensagens.Cliente2"
```

**Terminal 3 - Cliente3:**

```bash
mvn exec:java -Dexec.mainClass="br.com.servico_mensagens.Cliente3"
```

## Como utilizar

- Quando os clientes são inicializados, você vai ter acesso ao menu com as funcionalidades que o sistema oferece, algumas delas necessitam de autenticação, sendo necessário informar as credenciais que foi inserida no 'cadastro'.

### Menu

```
=== MENU PRINCIPAL ===
[Não autenticado]
1. Fazer login/autenticação
2. Postar mensagem
3. Ver mural
4. Extrair mural em PDF
5. Conectar com outros clientes
6. Sair
Escolha uma opção:
```

### Funcionalidades

- **Opção 1 - Fazer login/autenticação**:

  - Observe que acima da opção 1 informa que o usuário não está autenticado, sendo assim, você pode ir na opção 1 e realizar sua autenticação, informando seu nick e sua senha. A partir daí o usuário possui uma credencial para ter acesso há algumas funcionalidades do sistema.

- **Opção 2 - Postar mensagem**:

  - Está opção é dedicada para postar uma mensagem no mural, mas para isso é necessário ter uma credencial, caso contrário, não vai conseguir completar a ação.

- **Opção 3 - Ver mural**:

  - Nesta opção é possível visualizar sua cópia do mural.

- **Opção 4 - Extrair mural em PDF**:

  - Como informamos anteriormente, achamos interessante a exportação em PDF, pois facilita a visualização do mural quando tem muitas mensagens.

- **Opção 5 - Conectar com outros clientes**:

  - Como optamos por realizar a conexão com outros clientes de forma manual, adicionamos essa opção com esse objetivo, o cliente só vai conseguir se conectar com os outros caso execute essa opção.

- **Opção 6 - Sair**:

  - Essa opção é para finalizar a exeução.

## Alguns testes interessantes para visualizar o funcionamento do sistema:

### 1. Teste de Comunicação Normal

1. Inicie os 3 clientes
2. Poste mensagens em cada cliente
3. Verifique se as mensagens aparecem em todos os murais

### 2. Teste de Recuperação (Tolerância a Falhas)

1. Inicie os 3 clientes
2. Poste algumas mensagens
3. Encerre um cliente
4. Continue postando mensagens nos outros clientes
5. Reinicie o cliente que foi encerrado
6. Verifique se ele sincroniza automaticamente as mensagens perdidas

### 3. Teste do Algoritmo de Lamport

1. Observe os números entre colchetes nas mensagens
2. Poste mensagens rapidamente em diferentes clientes
3. Verifique se a ordenação lógica é mantida

## Arquivos Gerados

Cada cliente gera seu próprio arquivo JSON:

- `Cliente1_mural.json`
- `Cliente2_mural.json`
- `Cliente3_mural.json`

Esses arquivos contêm:

```json
{
  "contador": 5,
  "mensagens": [
    {
      "id": "Cliente1_1_1705312245123",
      "conteudo": "Primeira mensagem",
      "autor": "Cliente1",
      "timestamp": "2024-01-15T10:30:45",
      "lamportClock": 1
    }
  ]
}
```

## Arquitetura

### Classes Principais

- **Cliente**: Classe base com funcionalidades de socket, JSON e Lamport
- **MuralMensagens**: Gerencia o mural com contador e persistência JSON
- **Mensagem**: Representa uma mensagem individual
- **LamportClock**: Implementa o algoritmo de relógio lógico de Lamport
- **Cliente1/2/3**: Classes executáveis para cada nó

### Comunicação

- **Protocolo**: TCP Sockets
- **Formato**: JSON
- **Tipos de Mensagem**:
  - `NOVA_MENSAGEM`: Propaga nova mensagem
  - `SOLICITAR_SINCRONIZACAO`: Solicita sincronização
  - `RESPOSTA_SINCRONIZACAO`: Responde com mensagens faltantes

## Visão geral

- O sistema é tolerante a falhas: clientes podem sair e voltar
- Mensagens são persistidas em disco (formato JSON)
- Cada mensagem tem ID único para evitar duplicatas
- Sincronização incremental, para não transferir todo mural quando o cliente voltar a funcionar
- Autenticação de usuário
- Geração de relatórios PDF com timestamp
- Algoritmo de Lamport
