# Suporte a arquivos de configuração com e sem `.json`

Correção aplicada à versão `SNetS-SDM-QKD.zip` analisada em 20/10/2026.

## Comportamento do simulador

O `SimulationFileManager` agora aceita, para cada configuração:

- `network` ou `network.json`
- `simulation` ou `simulation.json`
- `physicalLayer` ou `physicalLayer.json`
- `traffic` ou `traffic.json`
- `others` ou `others.json`
- `qkdPhysicalLayer` ou `qkdPhysicalLayer.json`

Se os dois formatos existirem simultaneamente, o `.json` tem prioridade e um aviso é escrito no console.

Na gravação, o simulador preserva o formato de um arquivo que já existe. Para um arquivo novo, usa `.json`.
Isso evita criar uma cópia sem extensão depois de executar um caso originalmente criado com `.json`.

## Launcher

`launcher/src/launcher/Workspace.java` foi atualizado com a mesma regra. Assim, a interface web reconhece,
abre, edita, copia e executa casos que usem arquivos `.json`, arquivos legados sem extensão ou uma mistura dos dois.
Casos novos criados pelo launcher passam a gravar as configurações como `.json`.

O `launcher/snets-launcher.jar` incluído neste pacote já foi recompilado com a correção.

## Ferramentas auxiliares

Também foram ajustados leitores auxiliares que verificavam diretamente o arquivo `simulation`/`physicalLayer`:

- `qkd.tools.QKDPhysicalLayerMigration`
- `qkd.ladder.QKDLadderMain`
- `qkd.reference.QKDNativeReferenceMain`

Os geradores/verificadores que ainda escrevem arquivos sem extensão continuam válidos, pois o formato legado permanece suportado.

## Validação realizada

- `SimulationFileManager.java`: compilado com Java 8 usando stubs apenas para Gson; sem erros de compilação.
- Launcher: todos os arquivos `launcher/src/launcher/*.java` compilados com Java 8 e `snets-launcher.jar` regenerado.
- `Workspace.isCase(...)`: testado com caso somente `.json`, somente sem extensão e formato misto; os três foram reconhecidos.
- Resolução do `SimulationFileManager`: testada para prioridade `.json`, fallback legado e criação de arquivo novo `.json`.

O ambiente de validação não possui Maven, portanto não foi executado `mvn package` do projeto completo.
