# Raio — navegador leve para Android

**[⬇ Baixar Raio.apk atualizado](https://github.com/ezeq-ol/raio/releases/latest/download/Raio.apk)** · [Versões anteriores](https://github.com/ezeq-ol/raio/releases)

Versão **1.2**. Android 5.0 ou superior; sessão anônima isolada requer Android 9 ou superior. Kotlin e WebView do sistema, sem bibliotecas externas de runtime. O APK não contém o motor WebView; seu tamanho não representa o consumo de RAM ou armazenamento durante a navegação.

## Novidades da versão 1.2

- **Abas com limite de memória:** até oito abas, com até duas WebViews residentes por processo (uma em aparelhos identificados como de pouca RAM). As abas residentes preservam a página e seus formulários; as demais são suspensas com histórico e posição de rolagem na memória. Ao retornar a uma aba suspensa, seu conteúdo pode ser recarregado e formulários não são garantidos. Ao fechar o processo, só as URLs são restauradas, se habilitado.
- **Atualizações pelo GitHub:** Configurações → Verificar atualização do Raio. Ao abrir o navegador, consulta versões se passou um dia e listas se passou uma semana; isso pode ser desativado nas configurações. Baixar é uma escolha explícita; o Android confirma a instalação e, quando necessário, solicita autorização para instalar APKs pelo Raio.
- **Lista do bloqueador atualizável:** Configurações → Atualizar lista do bloqueador. Atualização por HTTPS a partir de `app/src/main/assets/domains.txt` deste repositório, sem trocar o APK. Domínios são validados, respostas e contagem são limitadas, e falhas mantêm a lista anterior. A lista é mantida no projeto; não é EasyList nem o motor do Brave.
- **Contador por aba:** o botão ● mostra o número de requisições de rede bloqueadas na navegação atual, sem contar elementos escondidos por filtros visuais. Menu → Desativar proteção neste site salva uma exceção para o hostname exato.
- **Sessão anônima isolada:** Android 9+ usa processo e diretório WebView separados, com cookies e armazenamento distintos do modo normal. Menu → Ligar modo anônimo; use Desligar modo anônimo ou Sair para encerrar. Configurações, favoritos e atalhos alterados na sessão privada são temporários; não alteram os dados normais. Capturas e miniaturas são bloqueadas nessa sessão.
- **Downloads no navegador:** Menu → Downloads mostra progresso, conclusão e erros, e abre arquivos concluídos. Remover um registro não remove o arquivo. Downloads comuns são salvos na pasta pública Downloads; APKs baixados pelo verificador de versões ficam na pasta de downloads externa do próprio app.
- **Controles por site:** toque no botão ● ou abra Menu → Proteção e permissões deste site. Ajuste JavaScript, cookies de terceiros e pop-ups por hostname; câmera, microfone e localização aproximada podem ser bloqueados por origem. Quando configurados para perguntar, só são autorizados após confirmação para o site HTTPS atual e permissão do Android. Cada solicitação pede consentimento novamente.
- **Interface:** aviso HTTP na barra, sugestões locais de favoritos/histórico e até oito atalhos editáveis e reorganizáveis em Menu → Editar atalhos.
- **Medições locais:** Configurações → Uso local de memória mostra PSS do processo, WebViews residentes e duração da criação da tela. Não representa todos os processos do WebView nem mede bateria.

## Instalar e atualizar

1. Baixe o APK pelo link acima.
2. Abra o arquivo e siga as confirmações de instalação do Android.
3. Instale sobre o Raio anterior para manter seus dados. A versão 1.2 mantém `com.raio.browser` e a assinatura de desenvolvimento das versões anteriores.

O SuperLite antigo usava `br.local.superlite`; é um app separado. O código anterior continua no histórico Git, a partir do commit `d3dd2f3`.

## Privacidade e limites

- O modo anônimo evita histórico/abas persistidos e separa os dados WebView, mas não oculta seu IP, atividade de sites, provedor ou rede. Arquivos baixados e compartilhamentos explícitos continuam fora da sessão. Não é apagamento forense. No encerramento normal, o perfil privado é removido; se o processo for morto, seu perfil anterior é removido antes de abrir a próxima sessão privada.
- Android 5 a 8 continuam com navegação normal; a sessão anônima isolada não fica disponível nesses sistemas.
- A atualização antiga que apenas alternava um indicador anônimo foi substituída pela sessão separada. A versão nova abre normalmente mesmo se esse indicador legado estava salvo.
- Consultas de atualização usam GitHub e seus servidores/CDNs, com os dados técnicos normais da conexão, como IP e User-Agent. Não enviam histórico, URLs visitadas, favoritos ou cookies de navegação. Consultas automáticas são desativáveis e não acontecem na sessão anônima.
- O bloqueador combina domínios, padrões de URL e filtros visuais. O filtro visual depende de JavaScript. Anúncios do YouTube podem continuar aparecendo, especialmente os servidos com o conteúdo. Use a exceção por site caso uma página apresente problemas.
- Tela cheia usa uma camada preta sobre a janela e restaura o navegador ao sair. Faixas pretas decorrentes da proporção original do vídeo são normais; o app não estica nem corta a imagem.
- Downloads `blob:`/`data:` e retomada manual de downloads não são suportados. O Android controla a fila e tentativas de rede.
- O APK continua assinado com chave de debug para distribuição de desenvolvimento pelo GitHub. A chave não é versionada; um build em outra máquina pode ter assinatura diferente.

## Compilar no Windows

Configure `JAVA_HOME` com um JDK 17 ou 21 e `ANDROID_HOME` com o Android SDK (padrão: `%LOCALAPPDATA%\Android\Sdk`). Instale a plataforma Android 35 e aceite as licenças do SDK.

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

Ou abra `gerar-apk.bat`. O script baixa Gradle 8.9 na primeira execução. Resultado: `dist/Raio.apk`. Também é possível abrir a pasta no Android Studio.

## Validação

Com Gradle 8.9 e o SDK configurados:

```text
gradle assembleRelease lintRelease testDebugUnitTest
node tools/test-shield.cjs
```

JUnit e Robolectric são dependências apenas de testes, sem inclusão no APK. Os testes cobrem regras de rede, validação das listas e URLs de release, versões, origem/permissões, preferências privadas, abas, fechamento de páginas, tela cheia, downloads e atalhos. Há testes de abertura das telas simulando Android 5 e 15 e testes de ciclo de vida no Android 9.

A execução desses testes não substitui testes em aparelho para reprodução do YouTube, consumo total de RAM, bateria e isolamento entre processos reais. Não havia dispositivo ADB conectado na preparação desta versão. `tools/measure-device.ps1` registra tempo de abertura e relatório de memória quando um aparelho estiver conectado e autorizado.

## Código principal

- `MainActivity.kt`: navegação, abas, tela cheia e interface.
- `RaioApp.kt`: início do aplicativo e processo anônimo separado.
- `AdBlock.kt`, `ShieldScript.kt`: filtros de rede e de página.
- `Updates.kt`, `SitePolicy.kt`: consultas ao GitHub e validação de respostas.
- `SitePermissions.kt`: consentimento por origem e permissões do Android.
- `UtilityScreens.kt`: downloads e atalhos.
- `Core.kt`: preferências e tema.
- `SettingsActivity.kt`, `LibraryActivity.kt`, `StartPage.kt`: configurações, biblioteca e página inicial.
