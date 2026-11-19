# Raio — navegador ultra leve para Android

Nova base do repositório SuperLite: projeto Raio criado pelo Claude, em Kotlin com WebView do sistema, sem bibliotecas externas de runtime. O código anterior permanece no histórico Git (commit `d3dd2f3`).

APK de referência: aproximadamente **58 KB**, Android 5.0 ou mais novo. O tamanho não inclui o motor WebView do sistema nem representa o consumo de memória durante o uso.

## Instalar no celular
1. Compile e copie `dist/Raio.apk` para o celular.
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
- `Core.kt` preferências, tema, bloqueador
- `StartPage.kt` página inicial e de erro
- `Ic.kt` ícones desenhados em código
