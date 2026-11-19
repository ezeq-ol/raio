# Raio — navegador ultra leve para Android

## Baixar o aplicativo

**[⬇ Baixar Raio.apk para Android](https://github.com/ezeq-ol/raio/releases/latest/download/Raio.apk)**

[Ver versões e arquivos para download](https://github.com/ezeq-ol/raio/releases). Android 5.0 ou superior.

Nova base do repositório SuperLite: projeto Raio criado pelo Claude, em Kotlin com WebView do sistema, sem bibliotecas externas de runtime. O código anterior permanece no histórico Git (commit `d3dd2f3`).

Versão atual: **Raio 1.1**, Android 5.0 ou mais novo. O tamanho do APK não inclui o motor WebView do sistema nem representa o consumo de memória durante o uso.

## Novidades da versão 1.1

- Vídeo em tela cheia em uma camada preta sobre toda a janela, sem reservar espaço para as barras e margens do navegador. Oculta barras do sistema, permite a área do recorte da tela e restaura o layout ao sair.
- Proteção agressiva ligada por padrão: lista local ampliada de domínios de anúncios/rastreadores, regras de URL, filtros visuais e uso do botão de pular anúncio do YouTube quando disponível.
- Para ajustar: **Configurações → Proteção agressiva**. Para um site com problemas: **Menu → Desativar proteção neste site**. A exceção é salva para o hostname exato; recarregar aplica a alteração.
- Instale o APK sobre o Raio 1.0 para atualizar. Mantém o identificador Android e a mesma assinatura local; não é preciso desinstalar.

O bloqueador não é o motor do Brave nem uma implementação de EasyList. As regras são embarcadas, sem atualização automática, proxy ou envio de navegação. O WebView não intercepta todos os tipos de requisição; anúncios de vídeo do YouTube podem continuar aparecendo, inclusive inserções servidas com o conteúdo. O filtro visual depende de JavaScript. A proteção agressiva pode afetar alguns sites; use a exceção por site.

Faixas pretas necessárias para preservar a proporção original do vídeo são normais. A alteração elimina o espaço do navegador ao redor do player, sem esticar nem cortar a imagem.

## Instalar no celular
1. [Baixe Raio.apk](https://github.com/ezeq-ol/raio/releases/latest/download/Raio.apk) pelo celular.
2. Abra o arquivo e permita "instalar apps desconhecidos" quando o Android pedir.
3. Toque no ícone do raio chamado **Raio**.

## O que tem
- Página inicial local (abre instantaneamente, sem rede) com busca e atalhos
- Barra de endereço inteligente (URL ou pesquisa), barra inferior com voltar, avançar, início, abas e menu
- Até 8 abas, favoritos, histórico, localizar na página, compartilhar, downloads, vídeo em tela cheia
- Pressionar e segurar em link/imagem: abrir em nova aba, copiar, baixar
- Configurações: mecanismo de busca (Google, DuckDuckGo, Bing, Brave, Ecosia), página inicial, restaurar abas,
  bloqueio de anúncios e rastreadores, economia de dados (sem imagens), JavaScript, versão desktop,
  tamanho do texto, tema claro/escuro/automático, escurecer sites, modo anônimo, "Não rastrear",
  cookies de terceiros, bloquear pop-ups, limpar dados ao sair, limpar histórico/cache/cookies,
  definir como navegador padrão

## Por que é leve
- Zero bibliotecas externas (sem AndroidX); interface desenhada em código, sem imagens
- Uma única WebView reaproveitada entre as abas
- Bloqueio de anúncios por lista de domínios (rápido)
- Código reduzido com R8 no build de release

## Gerar o APK de novo
Configure `JAVA_HOME` com um JDK 17 ou 21 e `ANDROID_HOME` com o Android SDK (por padrão, `%LOCALAPPDATA%\Android\Sdk`). Instale a plataforma Android 35 e aceite as licenças do SDK.

Execute `powershell -ExecutionPolicy Bypass -File .\build.ps1` ou dê duplo clique em `gerar-apk.bat`. O script baixa Gradle 8.9 na primeira execução e gera `dist/Raio.apk`. Também é possível abrir esta pasta no Android Studio.

O release usa a chave de debug local para permitir instalação de desenvolvimento. Essa chave não é versionada; builds em máquinas diferentes podem ter assinaturas diferentes.

## Migração e limites conhecidos

- O identificador agora é `com.raio.browser`; o SuperLite anterior usava `br.local.superlite`. Os apps são instalados separadamente, sem migração automática de dados.
- O modo anônimo suprime o salvamento de histórico e abas enquanto ativo, mas compartilha cookies e armazenamento com a navegação normal. A limpeza ao sair depende do encerramento normal da Activity; não garante limpeza se o processo for morto.
- As abas reaproveitam uma WebView: alternar recarrega a URL, sem histórico, formulários ou posição de rolagem independentes por aba.
- Câmera, microfone e geolocalização de sites são negados. Downloads `blob:` e `data:` não são suportados.
- Não foi realizado teste em aparelho nesta migração; nenhum dispositivo ADB estava conectado.

## Arquivos principais
`app/src/main/java/com/raio/browser/`
- `MainActivity.kt` navegador, abas, menu
- `SettingsActivity.kt` configurações
- `LibraryActivity.kt` favoritos e histórico
- `Core.kt` preferências e tema
- `AdBlock.kt` regras locais de rede
- `ShieldScript.kt` filtros visuais e controle de pular anúncio
- `StartPage.kt` página inicial e de erro
- `Ic.kt` ícones desenhados em código

## Validar alterações

Com Gradle 8.9 e o SDK configurados, execute `gradle assembleRelease lintRelease testDebugUnitTest`. Para testar o filtro visual sem Android, execute `node tools/test-shield.cjs` (Node.js).

Os testes cobrem regras de rede, preservação de URLs de reprodução/login, limites de domínio, filtros visuais e controles de pular anúncio. A confirmação visual da tela cheia ainda exige teste em aparelho; não havia dispositivo ADB conectado na preparação da versão 1.1.
