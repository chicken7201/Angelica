# BetterLoadingScreen 시작 로딩 중 텍스트 소실 조사

조사일: 2026-10-05. 조사 대상: 로컬 `master`, `639eef0e` (`2.2.28fork-glyphfix1`).

## 17:45 실행: 최초 예외 확인과 null texture 진단 수정

새로 첨부한 Prism 로그의 5301~5319행에 최초 `STARTUP_FAILURE` stack이 기록됐다.
원인은 `MixinTextureManager.angelica$loadingFontTextureDeleted()`의 null location 역참조다.
`SplashProgress.clearVanillaResources()` → `TextureManager.deleteTexture(null)` → 진단 handler의
`location.getResourcePath()` 순서로 실패했다. 이 진단 handler는 이번 조사에서 추가한 코드다.
보고서는 `crash-2026-10-05_17.45.55-client.txt`로 저장됐고 종료 코드는 -1이다.
따라서 이번 실행은 앞선 BetterCrashes priority 충돌이나 shader pipeline 내부 오류로 종료된 것이 아니다.

Forge의 cleanup은 splash가 꺼져 있으면 logo location을 `deleteTexture()`로 전달한다.
BLS가 vanilla logo를 생성하지 않은 실행에서는 location이 null일 수 있다.
원래 TextureManager는 map에서 null location을 조회하고 객체가 없으면 아무 작업 없이 반환한다.
진단 코드도 이 호출을 허용해야 한다.
삭제/등록 진단 함수 두 곳에 `location == null` guard만 추가했다.
TextureManager의 원래 delete/load 동작, reload lock, glyph metrics와 renderer 정책은 바꾸지 않았다.

이번 실행의 초기 Unicode 등록은 17:44:34에 BLS의 `SplashTextureManager@7ee9c3a2`에서 성공했고,
`BLS_FONT_EXCEPTION` / `BLS splash error`는 0회다.
BLS 종료는 17:45:55에 약 51ms로 완료됐다. 이는 초기 manager fallback 수정과 BLS 종료가 작동한 로그 증거이며,
화면의 시각적 출력 여부까지 로그만으로 보장하는 것은 아니다.

이전 JAR를 실제 SRG TextureManager에 적용해 `deleteTexture(null)`로 같은 NPE를 재현했다.
수정 JAR는 Java 25 / UniMixins 0.3.1에서 실제 변환된 TextureManager에 대해
null 삭제, Forge의 실제 null-logo cleanup body, null 등록의 반환값/등록 보존,
일반 font 등록/빈 texture 삭제, non-font 삭제를 모두 통과했다.
진단 옵션 true/false 두 설정으로 각각 실행했다.
Forge cleanup body는 테스트에서만 game-dependent static initializer와 분리하고 호출 이름을 SRG로 맞췄다.
외부 Forge 소스/JAR를 수정하지 않았다.

최신 배포 파일: `build/investigation/angelica-2.2.28fork-glyphfix1-loading-font-startup-fix-nullguard.jar`.
현재 설치된 Angelica 수정 JAR를 이 파일로 교체한다. 진단 옵션은 그대로 사용할 수 있다.
원래 crash report 파일과 첨부 로그는 저장소에 복사하거나 Git에 추가하지 않았다.
검증 기록은 `build/investigation/null-texture-diagnostics-build.log`,
`null-texture-before-smoke.log`, `null-texture-compat-smoke.log`, `null-texture-disabled-smoke.log`에 있다.
전체 GTNH 시작 완료는 다음 실행에서 확인해야 한다.

## 17:31 실행: 창 생성 전 종료와 BetterCrashes 호환 수정

사용자가 지정한 Prism 인스턴스의 `fml-client-latest.log`는 17:31:57에
`MixinMinecraftStartupDiagnostics` 적용 실패로 끝난다.
`@At("INVOKE") ... priority 1000 cannot inject into ... func_99999_d()V merged by ... bettercrashes ... priority 1000`
오류가 `Minecraft` 클래스 로드를 막아 게임 창이 생성되기 전에 프로세스가 종료됐다.
이는 앞서 추가한 진단 mixin의 호환성 오류다. JVM native crash나 font rendering 예외로 진단하지 않는다.
같은 폴더의 `latest.log`는 17:18:36 `Stopping!`으로 끝나는 이전 실행이므로 이번 실패 로그와 구분한다.
읽은 원본 로그와 외부 모드 JAR는 수정하거나 Git에 추가하지 않았다.

`MixinMinecraftStartupDiagnostics`를 제거하고
`MixinCrashReportStartupDiagnostics.angelica$logOriginalFailure()`를 추가했다.
`CrashReport.makeCrashReport()`의 HEAD에서 description이 `Initializing game`인 원래 Throwable을 기록한다.
BetterCrashes 1.4.7의 overwritten `Minecraft.run()`도 이 factory를 호출함을 실제 JAR 바이트코드로 확인했다.
Minecraft.run의 priority를 변경하거나 BetterCrashes 동작을 덮어쓰지 않는다.
앞선 초기 TextureManager fallback, texture generation/cleanup, null source filename 수정은 유지한다.

설치된 BetterCrashes 1.4.7의 실제 `MinecraftMixin`, `CrashReportMixin`과 Angelica mixin들을
Java 25 / UniMixins 0.3.1 transformer에서 함께 적용했다.
이전 JAR는 이번 게임 로그와 같은 priority 충돌을 재현했다.
수정 JAR는 실제 SRG Minecraft/CrashReport 변환과 JVM 연결, 원래 예외 hook의 생성자 이전 삽입,
null filename 비교 5개 case를 모두 통과했다. 전체 GTNH 시작 완료는 아직 실행 검증하지 않았다.
이번 변경은 진단 hook 위치만 바꾸므로 이전 글꼴 CPU/OpenGL 회귀 결과는 그대로 적용한다.

새 배포 파일: `build/investigation/angelica-2.2.28fork-glyphfix1-loading-font-startup-fix-bettercrashes.jar`.
현재 mods 폴더의 이전 `loading-font-startup-fix.jar`를 새 파일로 교체하고
`-Dangelica.debug.loadingFonts=true`를 유지해 재실행한다.
원래 100% 실패가 남으면 `STARTUP_FAILURE` / `Original client failure: Initializing game`의 stack과 crash report를 확인한다.
검증 로그는 Git에서 제외되는 `build/investigation/bettercrashes-compat-build.log`,
`bettercrashes-before-smoke.log`, `bettercrashes-compat-smoke.log`에 있다.

## 최신 런처 로그와 적용한 수정

사용자가 첨부한 Prism 콘솔 로그의 실행은 16:40:56~16:42:29이다.
아래 줄 번호는 첨부 텍스트 기준이다. 로그 원문은 저장소에 복사하거나 Git에 추가하지 않았다.

- 1415~1424행: BLS FontRenderer `@5ed55300`의 Unicode 페이지 등록이 전역 TextureManager의 null 때문에 실패한다.
- 5280행: `BLS_CLOSE_END`가 기록되어 BLS 종료가 완료됐다.
- 5286~5291행: Overworld pipeline 생성 로그 다음에 crash report의 source filename 비교 NPE로 Client thread가 종료된다.
- 최초 `startGame()` 예외는 이 콘솔 로그에도 없다. 마지막 정상 로그만으로 pipeline 자체가 원인이라고 확정할 수 없다.

확인된 초기 글자 누락은 다음 경로를 수정했다.
`FontRendererAccessor.angelica$getTextureManager()`와 `MixinFontRenderer`가 renderer의 `renderEngine`을 제공하고,
`BatchingFontRenderer.drawString()`, `drawCommands()`, `emitRangeThroughPipeline()`이 이를 Unicode provider에 전달한다.
`FontProviderUnicode.resolveTextureManager()`는 Minecraft 전역 manager가 없을 때 이 private manager를 사용한다.
전역 manager가 생기면 기존 DynamicTexture 객체/GL ID를 유지하며 등록을 옮긴다.
페이지는 실제 등록 manager를 보관하여 reload 시 해당 manager에서 등록을 제거한다.
동적 페이지 이름에는 resource generation을 넣어, manager별 독립 카운터가 reload 전후 같은 location을 재사용하지 않도록 한다.
manager가 모두 없으면 GPU allocation을 보류하며, 등록 실패 후 재시도는 기존 DynamicTexture를 재사용한다.
custom font provider, PUA/아래첨자 metrics, font 선택 정책은 변경하지 않았다.

확인된 보고서 작성 실패는 Angelica 자체 mixin
`MixinCrashReportCategory_SourceFile.angelica$compareSourceFiles()`로
`CrashReportCategory.firstTwoElementsOfStackTraceMatch()`의 파일명 비교 한 곳만 null-safe 처리했다.
이 mixin은 진단 옵션과 무관하게 적용하며 외부 Minecraft 소스/JAR를 수정하지 않는다.
이 수정은 원래 startup 실패를 해결했다고 보장하지 않는다.

진단 옵션을 켜면 `MixinCrashReportStartupDiagnostics.angelica$logOriginalFailure()`가
`CrashReport.makeCrashReport()`에서 report 생성 직전에 원래 초기화 Throwable을 기록한다.
다음 실행에서는 `event=STARTUP_FAILURE` 다음의 `Original client failure: Initializing game` 전체 stack과
새로 생성된 crash report를 확인한다. 이 로그에는 실제 실패 메서드와 `Caused by`가 남아야 한다.
초기 글자 수정은 전역 manager 등장 전 `UNICODE_TEXTURE_CREATE`, 이후 `UNICODE_TEXTURE_MANAGER_CHANGED`로 확인하며,
`BLS_FONT_EXCEPTION`의 null TextureManager 예외가 다시 발생하는지도 확인한다.

수정 배포 파일: `build/investigation/angelica-2.2.28fork-glyphfix1-loading-font-startup-fix.jar`.
기존 Angelica JAR를 이 파일로 교체하고 `-Dangelica.debug.loadingFonts=true`로 재실행한다.
`testfile/` 및 `build/investigation/`은 Git에서 제외된다.

## 후속 실행: 초기 글자 누락 및 100% 이후 응답 없음

대상은 `testfile/fml-client-latest.log`의 **16:19:54~16:22:53 실행**이다.
`old1` 파일은 이번 진단에 사용하지 않았다. 이 절은 후속 실행의 결과이며, 아래 최초 조사 당시의 가설을 보완한다.
이번 후속 진단에서는 Java 코드와 배포 JAR를 변경하지 않았다.

### 초기 글자 누락: 포크의 Unicode provider가 초기화되지 않은 전역 TextureManager를 사용

| 시점 / 로그 줄 | 확인한 동작 |
| --- | --- |
| 16:20:14 / 10405, 10423 | BLS의 일반 FontRenderer `@304ea5e` 생성 및 선택. 자체 `SplashTextureManager@33529cbe` 사용. Minecraft 전역 font/TextureManager는 아직 null. |
| 16:20:14 / 10551 | ASCII font texture 9를 shader 8 / VAO 4로 실제 bind하고 명령 제출. 모든 font GPU 자원이 처음부터 없는 상태는 아님. |
| 16:20:14 / 10727~10745 | `FontProviderUnicode.ensurePageTextureLocked()`가 null TextureManager의 `getDynamicTextureLocation()`을 호출하여 NPE. `getRenderInfo()` → `BatchingFontRenderer.drawString()` → BLS `drawMemoryUsage()` 경로. |
| 16:20:48 / 27386, 27406~27415 | Minecraft 전역 `TextureManager@375b3d75` 등장 직후 같은 provider의 Unicode 페이지 등록이 성공. BLS renderer는 계속 `@304ea5e`. |
| 16:21:30 / 42524~42527 | `BLS_CLOSE_BEGIN`부터 `BLS_CLOSE_END`까지 약 51ms. BLS 종료 및 GL splash 완료 기록. |
| 16:21:30 / 42533~42549 | Overworld pipeline 생성 로그 뒤 crash report 작성 중 `StackTraceElement.getFileName()`의 null로 NPE. |

정확한 실패 지점은 `FontProviderUnicode.ensurePageTexture()`의 전역 manager 조회(342행),
`ensurePageTextureLocked()`의 동적 텍스처 등록(365행)이다. BLS의 자체 TextureManager는 이미 존재하지만
provider는 이를 사용하지 않고 `Minecraft.getMinecraft().getTextureManager()`만 조회한다.
전역 manager가 나중에 생성되면 동일한 renderer/provider에서 등록과 Unicode bind가 성공한다.
따라서 **이번 초기 글자 누락의 직접 원인은 포크의 startup lifecycle 가정 불일치**로 확인된다.

`git blame`에서 현재 manager 조회/등록 경로는 `5c8ec239`로 추적된다.
합성 Unicode 페이지를 전역 manager에 동적으로 등록하는 설계는 `a7db9d69`부터 존재한다.
기반 upstream provider는 Unicode 페이지의 ResourceLocation 핸들을 반환하고
`FontStrategist.bindIntTexture()`가 해당 FontRenderer의 `bindTexture()`로 위임한다.
그 경로는 BLS가 가진 자체 manager를 사용할 수 있다.

BLS 1.7.18은 `renderProgress()`에서 이 예외를 잡아 화면 갱신을 다시 시도한다.
`drawMemoryUsage()`뿐 아니라 Unicode가 포함된 다른 텍스트 draw에서도 같은 실패가 가능하므로,
예외가 난 프레임은 그 이후 render 작업을 끝내지 못한다.
이 실행에서 `custom=false`였고, BLS renderer 교체나 Unicode bind ID 불일치/지속적 reload lock 차단은 관찰되지 않았다.
따라서 현재 로그는 custom font atlas 또는 stale FontRenderer를 직접 원인으로 지목하지 않는다.

추가로 generation 1의 page `ba` 등록 시작이 180회 반복된다.
코드는 null manager 호출 **이전**에 `new DynamicTexture(page.image)`를 실행하고 참조를 덮어쓰므로,
실패마다 미등록 GPU texture가 남을 수 있는 경로도 존재한다. 실제 누적 메모리 크기 및 100% 오류와의 인과관계는 확인하지 못했다.

### 100% 이후 응답 없음: 초기화 실패가 crash report 작성 실패로 가려짐

BLS 종료는 완료됐으므로 BLS render thread의 종료/join 대기에서 멈춘 로그가 아니다.
`Minecraft.run()`의 `startGame()` 예외 처리 경로는 최초 Throwable로 `Initializing game` report를 만든 뒤
`CrashReport.makeCategory("Initialization")`를 호출한다.
이번 stack의 `func_85058_a` → `func_85057_a` → `func_85069_a`는 그 경로와 일치한다.
`CrashReportCategory.firstTwoElementsOfStackTraceMatch()`는 source filename을 null 확인 없이 `.equals()`로 비교한다.
여기서 두 번째 NPE가 발생해 원래 Throwable 및 정상 crash report 출력이 가려졌다.

마지막 정상 로그의 호출 경로는
`shaders.startup.MixinMinecraft.angelica$shadersOnLoadingComplete()` →
`Iris.onLoadingComplete()` → `PipelineManager.preparePipeline("Overworld")`이다.
이 구간 또는 이어지는 startup 처리가 최초 실패의 조사 범위지만 **그 로그만으로 최초 예외의 클래스/발생 메서드를 확정할 수 없다.**
이 실행은 shaderpack이 비활성화되어 있었으므로 "Creating pipeline"만으로 외부 shaderpack 컴파일 오류라고 판단해서도 안 된다.

같은 인스턴스에서 16:26:51에 시작된 새 프로세스도 발견했으나 제공된 로그보다 나중 실행이다.
그 프로세스의 스레드 덤프를 16:21:30 실패 원인의 증거로 사용하지 않았다.
해당 실행의 16시대 crash report 파일도 생성되어 있지 않았다.

다음 진단은 `Minecraft.run()`이 잡은 최초 Throwable을 **crash report 작성 전** 한 번 기록하거나,
동일 실패 실행의 예외 이벤트/stack을 확보해야 한다. `getFileName()` NPE만 고쳐 원인 해결로 간주하거나,
shader pipeline을 추측으로 비활성화하는 수정은 근거가 부족하다.

## 결론과 한계

후속 실행의 **전체 검은 화면** 원인은 로그로 확정했다. 최초 진단 코드의
`MixinMinecraftDisplayerDiagnostics`가 `textureManager`를 vanilla `TextureManager` 타입으로 shadow했으나,
실제 BLS `1.7.18-GTNH`는 해당 필드를 `SplashTextureManager` 타입으로 선언한다.
`fontRenderer` 필드도 이 버전에는 없다. 이 때문에 Mixin 적용 단계에서 BLS 클래스 로딩이 실패했다.
이는 원래의 “배경은 보이고 글자만 사라지는” 문제와 별개이며 진단 코드 추가에서 발생한 오류다.

수정은 이 두 version-specific shadow와 해당 참조를 제거하는 범위로 제한했다.
두 버전에 공통인 `mc`, `preview`와 `fontRenderer(String)` 반환값으로 객체를 관찰한다.
BLS의 실제 font TextureManager identity는 `FONT_CREATED textures=`로 계속 확인할 수 있다.
`/testfile/`은 `.gitignore`에 추가했으며 로그 및 조사용 외부 JAR는 Git에 추가하지 않았다.

현재 코드만으로 실제 실행에서 텍스트가 영구적으로 사라지는 원인은 확정하지 못했다.
우선 의심하는 범위는 **로딩 중 TextureManager 교체/resource reload와 공유 font GPU 상태의 상호작용**이다.
이 범위는 증상이 중간에 시작되고 배경은 계속 보이는 현상에 부합하지만, 실제 GL 상태를 확보해야 한다.

포크의 최근 upstream 병합에는 Unicode 텍스처 검증 결과와 실제 bind 인자가 달라지는 구체적인 회귀 후보가 있다.
다만 이 후보만으로 모든 텍스트의 지속적인 소실을 설명할 수 없다. 다음 프레임은 최신 페이지 상태를 다시 조회하기 때문이다.
원인 수정은 적용하지 않았으며 선택형 진단만 추가했다.

최초 조사는 저장소 의존성 `BetterLoadingScreen:1.7.9-GTNH`를 사용했다.
후속 실행 로그에서 실제 설치 버전이 `1.7.18-GTNH`임을 확인하고, 설치된 JAR를 읽기 전용으로 복사하여
`MinecraftDisplayer`와 `SplashTextureManager`의 bytecode를 다시 조사했다.
이번 로그에서는 BLS 초기화 전에 클래스 로딩이 실패해 원래 텍스트 소실 시점의 render state는 확보하지 못했다.

## 1. upstream과 포크의 차이

`upstream/master`는 `0a4d4896`이며 현재 HEAD의 조상이다. 따라서 기반을 추정할 필요 없이
`git diff 0a4d4896 639eef0e`로 포크의 추가 변경을 비교했다. 조사 시작 시 작업 트리는 깨끗했다.
진단 추가 전 차이는 68개 파일, 3,745줄 추가/220줄 삭제였다.

FontRenderer batching mixin 자체와 기본 MC provider는 기반 upstream과 같았다.
중요한 포크 변경은 다음과 같다.

| 커밋 | 변경과 이번 문제와의 관계 |
| --- | --- |
| `c8cf064a` | GTNH PUA glyph를 system custom font보다 Unicode 리소스로 우선 처리. 특정 문자 선택에 영향을 주며 전체 텍스트 소실의 직접 근거는 없다. |
| `a7db9d69`, `6e1de7a3`, `5c8ec239` | Unicode 페이지 합성, lazy GPU 업로드, resource generation 및 재등록 처리. reload 경계의 주요 조사 대상이다. |
| `7e2bf516`, `7783ab38` | GTNH custom glyph bounds 유지 및 아래첨자 배치 보정. 폰트 전체의 lifecycle을 바꾸지는 않는다. |
| `e74deb01` | custom glyph bearings, baseline, atlas padding/metrics 변경. 현재 정상 초기 출력과 이후 소실을 이 변경만으로 설명하지 못했다. |
| `06e88ff3` | Unicode 검증부터 bind/draw까지 reload lock 보호와 오래된 배치 거부. 리소스가 바뀌는 순간 일부 글자가 생략될 수 있다. |
| `32737a6e` | 텍스트의 depth/polygon 상태 보존. 최신 병합은 upstream TextDrawState와 통합했다. 실제 state 확인이 필요하다. |
| `639eef0e` | upstream `75a02781`의 ResourceLocation 기반 bind와 포크의 Unicode 직접 GL ID/검증을 통합. 아래의 검증 ID와 bind ID 불일치 후보가 이 병합 결과에 있다. |

upstream `61bb779d`의 custom font CPU 측정/GPU 업로드 분리는 포크에도 반영되어 있다.
upstream `d189a644`는 당시 custom font resource pack을 즉시 등록하는 BLS 호환 변경이었다.
현재 provider는 직접 업로드하는 atlas 방식이므로 그 과거 구현을 현재 원인으로 간주하면 안 된다.

## 2. 확인한 실제 lifecycle

외부 모드의 코드는 수정하지 않았다. 다음은 **실제 설치된 BLS 1.7.18**의 bytecode와 로컬 Forge/Minecraft 패치 소스에서 확인한 흐름이다.

1. `alexiil.mods.load.MinecraftDisplayer.open(Configuration)`가 BLS 리소스 팩을 등록하고 `Minecraft.refreshResources()`를 호출한다.
2. `displayProgress(String, float, String, float)`는 threadedRendering 설정에 따라 shared context를 만들어
   `BLS Splash renderer` 스레드에서 렌더하거나 `renderProgressOnMainThread()`를 사용한다.
3. `renderProgress(...)`는 자체 manager의 `beginFrame()`/`endFrame()` 사이에서
   `displayProgressInWorkerThread(...)`를 실행하며 이 메서드가 `preDisplayScreen()`과 이미지/텍스트 렌더링을 수행한다.
4. `preDisplayScreen()`의 첫 호출은 현재 resource manager를 사용하는 자체 `SplashTextureManager`를 생성한다.
   **1.7.18은 이 메서드에서 `mc.renderEngine`이나 `mc.fontRenderer`를 교체하지 않는다.**
5. 실제 텍스트는 `drawImageRender(...)`가 `fontRenderer(String fontTexture)`로 가져온 renderer를 사용한다.
   이 메서드는 텍스처별로 **일반 `net.minecraft.client.gui.FontRenderer`**를 만들고 Map에 보관한다.
   자체 `SplashTextureManager`를 생성자에 전달하고, 생성 시 `onResourceManagerReload(...)`, Unicode/bidi flag 설정을 수행한다.
   이 메서드의 1.7.18 구현은 추가 `Minecraft.refreshResources()`를 호출하지 않는다.
6. `MixinFontRenderer.angelica$injectBatcher(...)`는 각 일반 FontRenderer 생성 시 `BatchingFontRenderer`를 연결한다.
   일반 `drawString`/`renderString`은 display list 밖에서는 Angelica batching으로 처리된다.
7. `Minecraft.startGame()`은 `FMLClientHandler.beginMinecraftLoading(...)` 이후 Minecraft의 TextureManager와
   기본/SGA FontRenderer를 다시 만들고 reload listener로 등록한다. BLS가 아직 실행되는 구간과 겹친다.
8. BLS 1.7.18에는 `fontRenderer` 필드가 없으며 **텍스처별 renderer Map과 자체 SplashTextureManager를 계속 보관**한다.
   Map의 renderer들은 resource reload listener로 등록하지 않고 생성 시 수동 reload만 한다.
9. `SplashTextureManager.endFrame()`은 사용하지 않은 자체 ResourceTexture를 해제한다.
   `MinecraftDisplayer.close()`는 BLS thread/context를 정리하고 자체 manager를 닫으며 renderer Map을 비운다.

1.7.9에는 `TextureManager` 및 `fontRenderer` 필드가 있고, 초기 `preDisplayScreen()`이 Minecraft의 두 객체를 교체한다.
이 버전의 동작을 실제 1.7.18에 그대로 적용하면 안 된다.

따라서 사용자 제시 흐름 중 “초기 정상 출력 → 로딩 중 manager/renderer 교체 → BLS가 일부 이전 객체 유지”는 가능하다.
하지만 “이전 객체의 모든 font texture가 무효화되어 계속 안 보인다”는 단계는 아직 확인되지 않았다.

## 3. 관련 클래스와 메서드: 사실과 가설

| 정확한 클래스/메서드 | 코드로 확인한 사실 | 아직 확인해야 할 부분 |
| --- | --- | --- |
| `FontStrategist.isSplashFontRendererActive(FontRenderer)` | FML SplashFontRenderer와 ModernSplash subclass를 판정한다. BLS의 일반 FontRenderer는 `isSplash=false`다. 값은 batcher 생성 시 고정된다. | BLS의 custom font 적용 여부는 실제 FontConfig 값에 달려 있다. |
| `FontStrategist.getFontProvider(...)` | 문자마다 provider를 다시 선택한다. custom 활성 시 GTNH PUA는 Unicode로 보낸다. BLS 일반 renderer는 custom provider를 사용할 수 있다. | 소실 시 provider 종류/identity가 달라졌는가? |
| `FontProviderCustom.InstLoader`, `setFont(...)`, `uploadAtlas(...)` | primary/fallback은 공유 singleton이다. `setFont`가 metrics를 지우고 atlas textures를 삭제한다. atlas는 draw 시 lazy 생성한다. | 로딩 중 실제 reset/delete가 발생하는가? 저장소 내 `reloadCustomFontProviders()` 호출은 FontConfigScreen에 있다. 정상 startup에서 이 설정 화면 경로가 실행되는 근거는 없다. |
| `FontProviderUnicode.onResourceManagerReload(...)` | singleton을 교체하지 않는다. glyph table/resource generation을 바꾸고 기존 페이지를 retire한다. | 소실 시 generation 교체와 texture 생성/삭제가 어떤 순서였는가? |
| `FontProviderUnicode.getRenderInfo(...)`, `ensurePageTextureLocked(...)` | glyph별 현재 generation을 조회하고, 등록 누락이나 `glTextureId=-1`이면 재등록/재업로드한다. BLS FontRenderer가 오래되었다고 옛 페이지를 고정적으로 참조하지 않는다. | 복구 뒤 실제 bind ID가 검증된 현재 ID와 일치하는가? |
| `BatchingFontRenderer.flushBatch()`, `drawCommands(...)` | TextureManager reload lock 획득 실패 시 해당 Unicode 포함 배치를 버린다. stale Unicode 명령도 검증 실패 시 생략한다. | 정상 reload 종료 뒤에도 계속 생략되는가? ASCII/custom까지 포함한 배치가 차단되는가? |
| `BatchingFontRenderer.drawCommands(...)` | 검증은 `unicodeTextureId`를 받지만 bind에는 `cmd.texture`를 전달한다. 부모 포크는 검증 결과 `texture`를 bind했다. | 실제 실행에서 ID가 달라지는가? 이 경우 한 배치가 잘못된 ID로 draw될 수 있지만 지속적 소실은 추가 근거가 필요하다. |
| `BatchingFontRenderer.setupFontDrawState()` | 공유 font shader를 사용하고 context별 VAO를 구성한다. immediate 경로에서는 이 메서드가 active texture unit을 0으로 설정하지 않는다. shader sampler의 기본 unit과의 불일치 가능성은 upstream에도 존재하는 코드다. | 소실 이후 `activeActual`, program, VAO, texture binding이 어떻게 달라지는가? 이 사실만으로 포크 원인이라 할 수 없다. |
| `BatchingFontRenderer.FontAAShader.getProgram()` | shader는 static lazy singleton이며 renderer는 shader ID와 uniform 위치를 보관한다. font resource reload에서 shader를 삭제/재생성하는 경로는 발견하지 못했다. | 초기화 중 공유 GPU 상태 변경 또는 GL cache와 실제 backend state 불일치가 있는가? |
| `mcpatcherforge.hd.MixinFontRenderer.modifyReadFontTexture1(...)` | HD font reload가 underlying renderer의 font location을 바꿀 수 있다. batcher는 생성 시 location을 별도로 보관한다. 이 코드는 upstream과 같다. | 사용 중인 리소스 팩에서 실제 font location이 바뀌는가? |
| `DarkModeFontTransform`, `MixinFontRenderer` | recolor/호환 처리는 존재하지만, startup에서 BLS renderer나 batcher를 새 객체로 교체하는 포크 코드는 발견하지 못했다. | compatibility 경로가 display list 등으로 바뀌는지 로그로 확인한다. |

## 4. 추가한 진단

JVM 인수로 아래 옵션을 넣고 **게임을 완전히 종료한 뒤 재실행**한다.

```text
-Dangelica.debug.loadingFonts=true
```

기본값은 꺼짐이다. Angelica font renderer와 기존 custom glyph 동작은 유지된다.
별도 진단 mixin은 옵션을 켠 경우에만 로드한다. BLS가 설치되지 않으면 `@Pseudo` 대상은 생략된다.
정상 사용 중에는 옵션을 제거한다.

`logs/latest.log` 또는 런처 로그에서 `[LoadingFont]`를 검색한다.
`seq`, 시작 이후 `ms`, `reload`, thread 이름으로 이벤트를 대조한다.
render state/texture 확인은 BLS thread에서 최대 1초에 한 번 샘플링하며 **바뀐 상태만 출력**한다.
glyph별 로그는 없다. renderer당 state/texture/geometry/outcome/exception 각 32회 제한 후 LIMIT 한 줄을 출력한다.
provider 첫 사용, 실제 객체 생성/리셋, texture 등록/삭제, resource reload는 해당 lifecycle 발생 시 출력한다.

| 이벤트 | 확인할 내용 |
| --- | --- |
| `BLS_OPEN`, `BLS_CLOSE_BEGIN/END` | 실제 BLS JAR 위치와 로딩 화면 구간 |
| `FONT_CREATED`, `BATCHER_CREATED`, `BLS_FONT_SELECTED` | 생성 시점, 실제 BLS renderer identity, batcher 및 GPU 자원 ID |
| `BLS_OBJECTS_CHANGED` | Minecraft font/TextureManager 교체 시점. BLS manager는 `FONT_CREATED textures=`와 대조 |
| `RESOURCE_RELOAD_BEGIN/END`, `FONT_RELOAD_BEGIN/END`, `TEXTURE_MANAGER_RELOAD_BEGIN/END` | 전체 reload, 개별 renderer reload, TextureManager reload 구간 |
| `CUSTOM_PROVIDER_CREATED/RESET`, `BLS_PROVIDER_FIRST_USE` | provider identity와 선택된 font, 초기화/변경 시점 |
| `FONT_TEXTURE_LOADED/DELETE`, `CUSTOM_TEXTURE_CREATE/DELETE`, `UNICODE_TEXTURE_CREATE/REPAIR/DELETE` | 실제 texture ID의 생성, 등록, 복구 및 삭제 |
| `UNICODE_GENERATION_CHANGED` | 새 generation과 retire한 페이지 수 |
| `BLS_RENDER_ROUTE` | batching 또는 display-list 호환 경로 |
| `BLS_DRAW_STATE` | Unicode/custom flag, AA 설정, shader ID, context, 실제/cached texture unit, depth/cull/alpha/color mask |
| `BLS_TEXTURE_BIND` | 저장된 handle, 검증된 texture ID, 각각의 GL 유효성, 실제 binding, font shader/program/VAO |
| `BLS_NO_GLYPH_GEOMETRY`, `BLS_GLYPH_GEOMETRY_READY` | 비공백 glyph 요청에서 geometry가 생성되지 않는 상태와 복귀 |
| `BLS_BATCH_BLOCKED/PARTIAL/SUBMITTED` | draw 명령 생략 여부와 GPU 제출 여부. SUBMITTED는 화면 출력 성공을 보장하지 않는다. |
| `BLS_FONT_EXCEPTION` | BLS가 잡고 반복하던 font 예외의 종류/메시지. 상세 stack은 인접한 기존 BLS 오류 로그를 확인한다. |

`BLS_TEXTURE_BIND`의 음수 `handle`은 정상적인 ResourceLocation 핸들이다.
`storedValid`는 양수 GL ID에만 적용되므로 ASCII 경로의 음수 handle에서 `storedValid=false`는 오류 증거가 아니다.

## 5. 다음 실행에서 판별할 순서

1. 텍스트가 사라진 대략적인 시간을 기록한다. `BLS_OPEN`이 없으면 옵션/진단 JAR 적용부터 확인한다.
2. `BLS_FONT_SELECTED selected=`와 그 renderer의 `FONT_CREATED`, `BATCHER_CREATED`를 연결한다.
   BLS cache와 `mcFont` identity가 다르다는 사실 자체는 정상일 수 있다.
3. 소실 직전 `BLS_OBJECTS_CHANGED` 또는 reload가 있었는지 확인한다.
   캐시 renderer에 `FONT_RELOAD_END`가 없더라도 그것만으로 texture가 무효하다고 판단하지 않는다.
4. `BLS_PROVIDER_FIRST_USE`에서 ASCII/custom/Unicode 중 실제 경로를 확인한다.
   ASCII/custom이 정상적으로 제출되는 동안 전체 글자가 사라지면 Unicode만의 generation 가설은 우선순위를 낮춘다.
5. `UNICODE_TEXTURE_REPAIR` 뒤 Unicode `BLS_TEXTURE_BIND`에서 `handle`, `texture`, `boundActual`을 비교한다.
   `handle != texture`이고 `boundActual == handle`이면 최근 병합의 bind ID 불일치가 실행에서도 확인된다.
6. reload 종료 뒤에도 `BLS_BATCH_BLOCKED`가 지속되는지, `BLS_NO_GLYPH_GEOMETRY`인지,
   geometry/draw는 정상 제출되는데 invisible인지 구분한다.
7. 정상 제출인데 invisible이면 해당 bind 로그에서 active unit이 0인지, 실제 program이 `shaderExpected`인지,
   VAO와 texture validity가 유지되는지, colorMask/alphaRef/cull 상태가 소실 전후 달라졌는지 확인한다.
8. `BLS_FONT_EXCEPTION`과 같은 시점의 `BLS splash error` stack을 함께 확인한다.

## 6. 검증

컴파일과 배포 JAR 빌드가 성공했다. 기존 CPU 회귀 테스트 29개와 실제 OpenGL 테스트 22개가 모두 통과했다.
OpenGL 테스트는 진단 옵션을 켠 상태로 실행하여 provider 생성/리셋과 texture 생성/삭제 로그도 확인했다.
리맵 결과에 신규 FontRenderer 진단 메서드와 TextureManager load/delete 메서드가 포함되는 것을 확인했다.

검은 화면 수정 후 배포 JAR를 재빌드했다. Java 25 / UniMixins 0.3.1의 실제 Mixin transformer로
설치된 BLS 1.7.18 JAR와 저장소 의존성 1.7.9 JAR의 `MinecraftDisplayer`에 진단 mixin을 각각 적용했다.
두 버전 모두 모든 hook 삽입(6곳)과 변환된 클래스의 JVM 연결 검증을 통과했다.
수정 전 JAR는 1.7.18에서 실행 로그와 동일한 `@Shadow field textureManager` 오류로 실패하는 것도 재현했다.
독립 검증 fixture의 FML bootstrap/appender 경고는 전체 게임 환경을 구성하지 않은 데서 발생한다.
전체 GTNH 게임 시작과 원래 텍스트 소실 현상의 재현은 아직 검증하지 못했다.

검증 명령:

```powershell
.\gradlew.bat --offline :compileJava :compileMixinJava :test --tests '*FontGlyphRangesTest' --tests '*CustomGlyphMetricsTest' --tests '*UnicodeGlyphMetricsTest' --tests '*UnicodeGlyphPageTest' --tests '*FontProviderUnicodeTest' --tests '*UnicodeTextureLifecycleTest' -x :glCoreTest :assemble
.\gradlew.bat --offline -I build/investigation/diagnostics-test.gradle :glCoreTest --tests '*CustomFontTextureUploadGLTest' --tests '*UnicodeTextureBindingGLTest' --tests '*FontProviderCustomGLTest' --tests '*FontRenderStateGLTest' :assemble
.\gradlew.bat --offline --no-configuration-cache -I build/investigation/mixin-smoke.gradle :compileMixinJava :assemble :loadingFontSmokeClasspath
# 실제 설치와 같은 Java 25 실행 파일로 아래 argument file을 실행했다.
java '@build/investigation/mixin-smoke-fixed.args'
java '@build/investigation/mixin-smoke-older.args'
```

`diagnostics-test.gradle`은 build 디렉터리의 임시 파일로 테스트 JVM에 위 진단 옵션만 설정한다.
실행 결과는 `build/investigation/validation.log`, `build/investigation/gl-validation.log`에 있다.
검은 화면 수정의 빌드/적용 검증 결과는 `black-screen-build.log`, `mixin-smoke-fixed.log`, `mixin-smoke-older.log`에 있다.
모두 Git에서 제외되는 `build/investigation/`에 보관한다.
배포 JAR 복사본은 `build/investigation/angelica-2.2.28fork-glyphfix1-loading-font-diagnostics-fixed.jar`이다.
이전 `loading-font-diagnostics.jar` 복사본도 수정된 내용으로 교체했다.
게임의 기존 Angelica JAR를 이 파일로 교체하고 JVM 옵션을 추가하면 된다.

위 검증은 최초 진단 및 검은 화면 수정 단계의 기록이다. 최신 수정/검증은 문서 상단과 아래 후속 검증을 따른다.
font 비활성화, PUA 처리 제거, 아래첨자 보정 제거 또는 glyph 정책 변경은 포함하지 않는다.

## 최신 수정의 후속 검증

최종 빌드와 CPU 29개 / OpenGL 25개 테스트가 모두 통과했다.
신규 `UnicodeLoadingTextureGLTest` 3개는 전역 manager가 없는 초기 등록, 같은 GPU texture를 유지하는 manager 전환,
reload 이후 이전 location 거부/새 generation 등록, manager 부재 시 allocation 보류,
등록 실패 재시도의 동일 DynamicTexture 재사용을 확인한다.
이 fixture는 게임의 TextureUtil mixin 없이 실행하므로, private 테스트 manager가 vanilla core-profile allocation을 보완해
실제 GL bitmap을 공급한다. 이는 manager 전환/cleanup 검증용 adapter이며 게임 소스에는 포함되지 않는다.

Java 25 / UniMixins 0.3.1 transformer로 실제 SRG Minecraft의 CrashReportCategory와 Minecraft를 변환했다.
null/null, null/파일명, 파일명/null, 동일 파일명, 다른 파일명 5가지 비교가 정상 동작했고,
최초 Throwable 로그 hook 삽입과 변환된 클래스의 JVM 연결도 통과했다.
BLS 1.7.18과 1.7.9의 실제 MinecraftDisplayer 변환/연결도 통과했다.
GTNH 전체 시작 완료와 최초 startup 예외의 해결 여부는 다음 실행에서 확인해야 한다.

결과 파일은 Git에서 제외되는 `build/investigation/`의
`startup-fix-final-validation.log`, `startup-fix-mixin.log`,
`startup-fix-bls-1.7.18.log`, `startup-fix-bls-1.7.9.log`이다.
최종 배포 복사본은 문서 상단의 `loading-font-startup-fix.jar`이며 이전 진단 JAR 복사본도 같은 내용으로 교체했다.
