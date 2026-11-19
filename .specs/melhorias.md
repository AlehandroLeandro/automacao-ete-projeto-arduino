# Conexão com arduino
- [x] Conexão com Arduino por chave de dispositivo (login, leituras, tela Automação) → #1 (backend), #2 (frontend)

irei dar um paço adiante no projeto, vou fazer a coleta de informações em tempo real por meio de sensores e arduíno. quero permitir que o sistema receba as informações do arduíno. para evitar de ter que criar um usuário para o arduíno, quero que ele faça a autenticação por meio de uma chave.
essa chave vai ser colocada no código do arduíno e ele vai enviar ela para fazer seu login. após logado ele continua usando o sistema por meio dos jwt tokens que irão na requisição de forma automática.

para isso funcionar corretamente, quero que no sistema eu tenha uma tela para poder gerar e vizualizar essas chaves dos arduínos, podendo atribuir um nome a cada uma delas, assim eu sei qual é o equipamento que está coletando as informações. essa tela deve ser visível somente para perfil adm, dentro da aba de mais. nela terá um novo botão além dos já existentes - Relatórios, Vazão, etc. adicionar o botão de Automação
nessa tela teremos a listagem das chaves já cadastradas, e um botão para adicionar novas chaves. o valor da chave será gerado de forma aleatória, com 24 caracteres contento letras maiusculas e minusculas, números e símbolos. as chaves podem ser desativadas e também excluidas. desativar ela só para de funcionar, excluída ela realmente é peridida e uma nova deve ser gerada.

usando essa chave o arduíno poderá fazer a requisição para o post de vazão, e de pH entrada e de saída