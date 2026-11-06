# Correção do erro 92% — Voz em Texto 1.2

**Sintoma do teste no celular**: o download chegava a 92%, mostrava "Preparando reconhecimento offline" e terminava com "Modelo de reconhecimento incompleto".

**Causa identificada no código da versão 1.1**: a função `ModelDownloader.isInstalled()` e a validação da extração exigiam as pastas `am/`, `conf/` e `graph/` (formato Vosk V2). No entanto, o pacote oficial `vosk-model-small-pt-0.3` usa o formato antigo V1, com arquivos `final.mdl`, `mfcc.conf`, `HCLr.fst` e `Gr.fst` diretamente na pasta principal. O Vosk aceita os dois formatos.

**Correção**: `ModelDownloader.kt` agora valida ambas as estruturas do Vosk, testa os arquivos necessários, verifica a quantidade de bytes recebidos quando o servidor informa o tamanho, preserva a proteção contra entradas ZIP fora da pasta e dá mensagens mais específicas.

**Compatibilidade**: mesmo identificador Android `com.vozemtexto.app`; `versionCode = 3`, `versionName = 1.2.0`. O arquivo do workflow GitHub Actions permanece `.github/workflows/build-apk.yml` e é o mesmo que já produziu o APK da versão anterior.

**Como usar**: enviar este ZIP diretamente para a raiz do repositório com seu GitHub Manager, sem criar uma pasta adicional. Aguardar o workflow na aba Actions e baixar o artefato `VozEmTexto-APK-para-instalar`. Ao abrir a nova versão, toque em Iniciar e aceite o download do português novamente. Aguarde chegar a 100%.

**Se o Android não permitir atualizar**: é possível que um novo APK de depuração esteja assinado com uma chave diferente. Antes de desinstalar o antigo, salve seus textos em TXT; a desinstalação pode apagar as transcrições mantidas dentro do aplicativo.

**Verificação efetuada**: o arquivo Kotlin de download foi compilado com um stub Android e testado contra estruturas simuladas V1 e V2; a estrutura do ZIP e o workflow foram conferidos. **Não houve compilação completa do APK, nem teste de download no aparelho** neste ambiente. A execução do GitHub Actions e o teste no telefone são necessários.
