# SuperLite

Navegador Android minimalista feito em Java, usando o WebView do sistema. Projeto inicial para evoluir em conjunto.

## Recursos atuais

- Barra de endereço com pesquisa pelo Google.
- Voltar, avançar, recarregar e página inicial local.
- Indicador de carregamento e mensagem de erro de conexão.
- Uma única janela, sem bibliotecas de terceiros.
- Somente a permissão de internet; sem SDKs de publicidade ou análise adicionados ao app.

O APK inicial tem aproximadamente 17 KB porque o motor WebView é fornecido pelo Android. Esse tamanho não representa o consumo total de memória ou armazenamento durante o uso.

## Compilar no Windows

Requisitos: PowerShell, JDK 21 e Android SDK com `platforms;android-35`, `build-tools;36.1.0` e `platform-tools`.

Defina `JAVA_HOME` para o JDK e `ANDROID_HOME` para o SDK. Sem essas variáveis, o script usa o `javac.exe` do PATH e o SDK em `%LOCALAPPDATA%\Android\Sdk`.

```powershell
./build.ps1
```

Resultado: `dist/SuperLite.apk`. O script compila Java, gera DEX, empacota, alinha, assina e verifica o APK, sem Gradle.

Na primeira compilação, é criada uma chave local de desenvolvimento em `build/local.jks`, com senha de desenvolvimento `android`. Guarde essa chave para instalar atualizações sobre a mesma instalação. Ela não é versionada. Uma nova chave exige desinstalar a versão anterior, o que pode apagar seus dados. Para distribuição pública, configure uma chave de produção protegida.

## Instalar

Depois de conectar o telefone ao ADB:

```powershell
adb install -r dist/SuperLite.apk
adb shell am start -n br.local.superlite/.MainActivity
```

Android mínimo: 8.0 (API 26). Identificador: `br.local.superlite`. Versão inicial: 1.0.

## Estrutura

- `AndroidManifest.xml`: permissões e configuração do aplicativo.
- `src/br/local/superlite/MainActivity.java`: interface e navegação.
- `build.ps1`: compilação e assinatura local.

## Validação e limites

A primeira versão foi instalada e aberta em um Samsung SM-A032M com Android 13. Foi observado o carregamento de uma página da internet, sem erro fatal de Java nos registros consultados.

Esta é uma versão inicial: não há abas, gerenciador de downloads, seletor para upload de arquivos, favoritos ou modo anônimo. Não contorna erros de certificado TLS. A compatibilidade com divisão de tela continua dependendo do sistema do aparelho; a configuração redimensionável do app não libera esse recurso no Android.
