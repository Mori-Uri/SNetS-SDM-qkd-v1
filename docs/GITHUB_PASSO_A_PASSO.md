# Como publicar o simulador no GitHub e compartilhar com o coorientador

O repositório será **privado**: só você e quem você convidar podem ver o código. O arquivo `.gitignore` da pasta já
deixa de fora os PDFs dos artigos (direitos autorais), os documentos .docx, as saídas do plano de verificação
(`simulations/QKD_ladder`), as cópias de segurança e os arquivos temporários do launcher. O que vai para o GitHub
(cerca de 70 MB):

- o código (`src`);
- o launcher;
- os casos de simulação;
- os dois JARs;
- a documentação.

## 1. Conta no GitHub

1. Entre em <https://github.com>. Se não lembrar a senha, use **Forgot password?** com o seu e-mail.
2. Ative a verificação em duas etapas se o GitHub pedir: **Settings › Password and authentication**.

## 2. Instalar o GitHub Desktop

1. Baixe em <https://desktop.github.com> e instale.
2. Abra o programa e clique em **Sign in to GitHub.com**. O navegador abre para você autorizar.
3. Em **Configure Git**, confirme o nome e o e-mail que aparecerão nos commits.

## 3. Criar o repositório a partir da pasta do simulador

1. No GitHub Desktop: **File › Add local repository…**.
2. Em **Local path**, escolha `C:\Users\uriel\Documents\Simulador\SNetS-SDM-qkd-v1`.
3. Ele vai avisar que a pasta ainda não é um repositório Git. Clique em **create a repository**.
4. Na janela **Create a new repository**:
   - **Name**: `SNetS-SDM-QKD`;
   - **Description**: por exemplo, "SNetS-SDM com addon QKD (dissertação)";
   - **Git ignore** e **License**: deixe **None**, porque a pasta já tem `.gitignore` e `LICENSE`.
5. Clique em **Create repository**.

O GitHub Desktop faz o primeiro commit com todos os arquivos que o `.gitignore` não exclui. Confira a lista à esquerda:
não deve aparecer nenhum `.pdf`, nem `docs/artigos`, nem `simulations/QKD_ladder`.

## 4. Enviar para o GitHub (privado)

1. Clique em **Publish repository**, no alto.
2. Deixe marcado **Keep this code private**.
3. Clique em **Publish repository** e espere o envio terminar. Pode demorar alguns minutos por causa dos JARs.

## 5. Convidar o coorientador

1. No navegador, abra o repositório (no GitHub Desktop: **Repository › View on GitHub**).
2. Vá em **Settings › Collaborators › Add people**.
3. Digite o usuário ou o e-mail do GitHub dele e escolha o nome dele na lista.
4. Envie o convite. Ele recebe um e-mail e precisa aceitar.

## 6. Dia a dia: enviar alterações

1. Depois de mudar o código (ou depois de eu instalar uma nova parte), abra o GitHub Desktop. As alterações aparecem
   na aba **Changes**.
2. Escreva um resumo no campo **Summary**, por exemplo "Parte 26: PICh por enlace e QTx/QRx".
3. Clique em **Commit to main** e depois em **Push origin**.

Para trazer alterações que o coorientador fizer, use **Fetch origin** e depois **Pull origin**.

## Observações

- Arquivos acima de 100 MB não são aceitos pelo GitHub. Hoje o maior é o JAR do simulador, com cerca de 23 MB.
- Para incluir um arquivo que o `.gitignore` exclui (por exemplo, um PDF seu), edite o `.gitignore` ou adicione o
  arquivo pelo terminal com `git add -f <arquivo>`.
- Antes de cada commit, o GitHub Desktop mostra exatamente o que será enviado. Confira se não há resultados pesados ou
  arquivos pessoais na lista.
