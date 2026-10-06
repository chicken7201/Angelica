# HoloInventory / Night Vision 색상 오염 조사

조사일: 2026-10-07. 작업 소스: `8a33b0b8b1d564469e777ed10872f537d4228c12` 기반 Angelica 포크.

결론: Angelica가 아이템 렌더 진입 때 **HUD에서 비활성화한 lightmap을 다시 활성화**한다. Night Vision이 변경한 월드 lightmap이 홀로그램 아이템의 색에 곱해진다. 측정한 active texture unit, texture binding, blend에서는 실제 GL과 GLSM 캐시의 불일치를 발견하지 않았다. cutout 종료 시 lightmap 복원도 정상이다. 오류는 복원 전에 이미 잘못된 상태로 아이템을 그린다는 점이다.

HoloInventory의 Beta 3/RC2 버전 차이와 과거 Angelica의 정상 동작은 조사 전제로 사용하지 않았다. HoloInventory JAR은 실제 호출 경로를 확인하는 읽기 전용 참고 자료로 사용했다.

## 1. HoloInventory 실제 렌더 호출 경로

제공된 `holoinventory-2.5.17-GTNH.jar`의 SHA-256:

```text
D5C37A1CAF311A3D7B586F65C52E38011F596C28A39A9E2502FF23451078CAE0
```

아래 경로는 해당 JAR의 `javap -p -c` 결과에서 확인했다. 괄호 안 이름은 배포 JAR의 SRG 메서드 이름이다.

```text
GuiIngameForge.renderGameOverlay
  → renderHelmet
  → RenderGameOverlayEvent.Post(HELMET)
  → Renderer.INSTANCE.renderEvent
  → Renderer.doEvent
  → Renderer.renderHologram
  → Renderer.doRenderHologram
  → Renderer.renderHologramItems
  → GroupRenderer.renderItems
      RenderHelper.enableStandardItemLighting (func_74519_b)
  → GroupRenderer.renderItem
      fakeEntityItem.setEntityItemStack
  → GroupRenderer.doRenderEntityItem
      glPushMatrix, transforms
      RenderItem.renderInFrame = true
      fancy item option save / enable
  → ClientHandler.RENDER_ITEM.doRender
      ClientHandler$1.doRender (func_76986_a)
  → super RenderItem.doRender(EntityItem, double, double, double, float, float)
  → ForgeHooksClient.renderEntityItem
      custom IItemRenderer.ENTITY, if registered
      otherwise block / multipass icon / ordinary icon branch
  → RenderBlocks.renderBlockAsItem or RenderItem.renderDroppedItem
  → ItemRenderer.renderItemIn2D / Tessellator draw
```

실제 렌더 객체는 RenderItem의 하위 클래스인 `ClientHandler$1`이다. `doRender`를 부모로 전달하고 렌더 예외를 처리하며, 아이템의 bobbing/spreading을 끈다. `renderItemIntoGUI`는 호출하지 않는다.

GroupRenderer는 `finally`에서 `renderInFrame`, fancy-items 옵션과 matrix를 복원한다. 그룹 렌더가 끝나면 `RenderHelper.disableStandardItemLighting`을 호출한다. HoloInventory가 이 아이템을 감싸는 것은 **matrix push/pop**이다. 계측에서 확인한 attribute scope는 Angelica가 생성한다.

HoloInventory는 RenderItem을 직접 호출하므로 RenderManager의 일반적인 엔티티별 lightmap 좌표 설정을 거치지 않는다. 마지막 월드 brightness 좌표를 물려받는다. HUD의 secondary texture unit이 비활성인 동안에는 이 좌표로 lightmap을 샘플링하지 않는다.

## 2. Angelica 개입 지점

현재 RC2 폴더의 실제 파일 상태:

| 대상 | 확인 결과 |
|---|---|
| `angelica-2.2.29.jar.disabled` | 비활성 원본 2.2.29 |
| `angelica-2.2.9fork-glyphfix2.jar` | 현재 활성 포크 |
| 런타임 | Java 25.0.1, OpenGL 4.6 core, LWJGL3 backend |
| shaderpack | 활성 shaderpack 없음; GLSM의 FFP shader emulation 사용 |
| HUD caching | 시작 모듈은 활성, `hudCachingActive=false` |

2.2.29와 현재 활성 포크의 HUD 진입, `ItemIdManager.beginCutout`, `GbufferPrograms.setCutoutDefaults` 바이트코드를 각각 확인했다. 문제를 만드는 호출은 양쪽에 존재한다. 이 확인은 과거 버전 간 regression 판정이 아니다.

핵심 경로:

```text
MixinEntityRenderer_HUDCaching
  → HUDCaching.renderCachedHud
      lighting disable
      active texture = TEXTURE1
      texture 2D disable
      active texture = TEXTURE0
      texture 2D enable
  → GuiIngameForge / HoloInventory / RenderItem.doRender
  → shaders.MixinRenderItem.iris$droppedItemRender (@Surround)
  → ItemIdManager.beginCutout
      pushState(StateSet.CUTOUT)
  → GbufferPrograms.setCutoutDefaults
      alpha test enable, GL_GREATER / 0.1
      getTextureUnitStates(1).enable()  ← 문제
  → item geometry draw
  → ItemIdManager.endCutout
      popStateTo(savedDepth)
```

`hudCachingActive=false`일 때도 HUD 진입의 lightmap disable과 RenderItem Mixin은 실행된다. HUD caching을 켰을 때의 별도 경로는 HoloInventory 기본 이벤트를 억제하고 `HoloInventoryReflectionCompat.renderEvent(fake HELMET Post)`로 다시 호출한다. 이 환경에서 실제 사용한 경로는 캐싱이 꺼진 일반 HELMET Post 경로다.

다른 관련 개입도 확인했다.

| Minecraft/Forge 작업 | Angelica 개입과 이번 증상과의 관계 |
|---|---|
| RenderHelper의 lighting/light/color-material 설정 | GLSMRedirector가 GL 호출을 상태 관리로 전달; 아이템 호출 계측에서 lighting은 item lighting으로 활성 |
| OpenGlHelper.setActiveTexture | GL13 또는 ARB multitexture 호출을 GLSM active-unit 관리로 전달 |
| OpenGlHelper.setLightmapTextureCoords | GL13/ARB multitexture 경로가 GLSM brightness 좌표와 shader attribute를 갱신; 이번 Holo 직접 호출은 새 좌표를 설정하지 않음 |
| TextureManager/TextureUtil atlas binding | `glBindTexture`가 per-unit GLSM binding cache를 갱신; native binding과 비교해 일치 확인 |
| glColor*, glLight*, lighting | core profile에서는 FFP 상태와 shader uniform/attribute로 에뮬레이션 |
| blending/alpha | GLSM 상태 관리; item cutout은 alpha-test scope를 저장/복원 |
| dropped-item instancing / VBO capture | RenderItem의 geometry 호출에 개입; 관련 capture 회귀 테스트 통과 |
| MCPatcher CIT / NotFine glint / GUI RenderItem Mixin | texture/glint/GUI 관련 개입 확인; HUD item의 unit 1 강제 활성화가 이번 오류를 직접 설명함 |

## 3. Night Vision ON/OFF state 차이

사용자가 사용한 장비는 Thaumic Horizons의 Illumine Lens, 전환 키는 Shift+0이다. RC2의 `ThaumicHorizons-1.8.27.jar`에서 `ItemLensFire.handleRender`가 Night Vision 포션 효과를 부여하는 코드를 확인했다. 렌즈 코드와 라이브러리는 수정하지 않았다.

Minecraft `EntityRenderer.updateLightmap`은 Night Vision의 밝기 계수로 lightmap의 RGB를 변환한 뒤 DynamicTexture에 업로드한다. Night Vision은 아이템의 `glColor`에 파란색을 직접 설정하지 않는다. 관측된 색은 lightmap texture에서 유입된다.

**OFF에서는 정상으로 보이고 ON에서만 파래지는 이유**는 이 계산의 순서에 있다. 밝은 lightmap texel의 원래 R/G/B는 모두 1을 넘을 수 있다. NV OFF에서는 각각 1로 clamp되어 흰색이 된다. NV ON에서는 그 clamp 전에 `min(1/R, 1/G, 1/B)`를 곱한다. 이때 가장 큰 B가 1이 되고 R/G는 1 미만의 비율로 남는다. 같은 texel이 OFF에서는 흰색, ON에서는 푸른색이 될 수 있다. unit 1을 잘못 켜는 오류는 양쪽에 존재하지만 OFF의 흰색 곱셈은 눈에 띄지 않는다.

로컬 Minecraft 소스의 수치 예제: texel `(15,15)`, sun brightness `0.2`, torch 값 `1.5`, NV strength `1`, gamma `0`이면 NV 전 RGB는 약 `(1.580592,1.839792,2.7804)`다. 최종 OFF RGB는 **`(252,252,252)`**, ON RGB는 **`(146,169,252)`**다. 실제 RC2의 `options.txt`에서도 `gamma:0.0`을 확인했다. sun/torch 값은 설명용 입력이며 현재 월드에서 측정한 값이 아니다. 이 예제는 실제 ON texel `(145,168,252)`와 부합하지만 OFF texel의 실측을 대신하지 않는다. torch flicker, 월드 환경 또는 custom lightmap에 따라 구체적인 숫자는 달라진다.

실제 JVM에 읽기 전용 checkpoint를 일시 삽입했다. Holo GroupRenderer 진입/종료, RenderItem 진입/종료, cutout 진입 전/후, 복원 전/후를 기록했다. native active unit을 일시 전환해 binding을 조회한 후 원래 native unit으로 즉시 복원했다. 색상·lighting·blend·플레이어·월드 상태는 변경하지 않았다.

수정 전, Night Vision ON에서 확보한 실제 샘플:

| 상태 | Holo/item 직전 | cutout 진입 후 | cutout 복원 후 |
|---|---|---|---|
| attribute depth | 0 | 1 | 0 |
| active unit, cache/native | 0/0 | 0/0 | 0/0 |
| unit 1 binding, cache/native | 37/37 | 37/37 | 37/37 |
| unit 1 FFP enable | false | **true** | false |
| FFP FragmentKey lightmap | false | **true** | false |
| brightness coordinates | 기존 값 | 기존 값 | 기존 값 |
| item 진입 color | `(1,1,1,1)` | `(1,1,1,1)` | item renderer가 남긴 값 |
| blend enable/factors | cache/native 일치 | 일치 | 일치 |

파란 lightmap 샘플은 brightness `(240,240)`, texel `(15,15)`의 RGB **`(145,168,252)`**였다. 기존 밝기 좌표는 다른 월드 렌더에 따라 달라졌으며, `(144,16)`에서 `(252,204,142)` 같은 따뜻한 색도 관측했다. 즉 같은 오류가 위치/조명에 따라 다른 tint를 만들 수 있다. Night Vision ON에서 파란 lightmap을 읽은 샘플이 사용자가 보고한 파란 tint를 설명한다.

core profile에는 고정 기능의 native `GL_TEXTURE_2D` enable, `GL_CURRENT_COLOR`, `GL_LIGHTING`이 없다. 해당 항목을 native `glIsEnabled/glGet`으로 조회해 캐시와 비교하는 것은 유효하지 않다. 따라서 unit-enable/color/lighting은 GLSM FFP 상태와 FragmentKey를 확인했고, **실제 framebuffer 픽셀 테스트**로 shader 적용을 별도로 검증했다. native GL과 직접 대조한 항목은 active unit, binding, blend다. 모든 FFP uniform의 원시 GPU 값까지 비교했다고 주장하지 않는다.

측정 범위의 한계: 확보한 Holo 호출은 수정 전 182개, 수정 후 148개이며 포션 플래그가 모두 ON이었다. 사용자가 렌즈를 전환했지만 raw potion flag OFF인 Holo 샘플은 확보하지 못했다. 그 이유는 이번 조사에서 특정하지 않았다. 따라서 실제 OFF lightmap RGB를 측정했다고 보고하지 않는다. OFF 정상/ON 파란 tint라는 최초 재현 조건과 수정 후 양쪽 정상은 사용자 확인이며, neutral/blue lightmap의 동일 아이템 픽셀 비교는 별도의 GPU 재현 테스트다.

## 4. 실제 원인

`ItemIdManager.beginCutout`은 GUI/HUD를 포함해 RenderItem을 부르는 모든 곳에 적용된다. 그런데 world/hand rendering용 `GbufferPrograms.setCutoutDefaults`를 호출하여 호출자가 꺼둔 lightmap unit 1까지 켠다.

unit 1의 기존 binding과 brightness 좌표는 유지된다. FragmentKey는 활성화된 lightmap unit을 shader에 포함하고, 아이템 색에 Night Vision lightmap 샘플을 곱한다. 렌더 후 `StateSet.CUTOUT`이 unit 1을 정상적으로 끄더라도 이미 그려진 픽셀은 복원되지 않는다.

이는 **아이템 진입의 상태 계약을 Angelica가 잘못 바꾼 오류**다. 이번 계측에서 push/pop cache desynchronization은 발견하지 않았다. HoloInventory가 엔티티형 렌더를 HUD에서 직접 호출한다는 사실만으로 잘못된 OpenGL 사용이라고 판정하지 않았다. vanilla의 disabled lightmap을 유지하면 이 호출은 Night Vision lightmap을 샘플링하지 않는다.

## 5. 수정

프로덕션 수정은 `src/main/java/net/coderbot/iris/uniforms/ItemIdManager.java` 한 파일이다. `beginCutout`에서 lightmap을 켜는 공용 기본값 호출을 제거하고, 필요한 alpha 설정 두 개만 유지했다.

```diff
- GbufferPrograms.setCutoutDefaults();
+ GLStateManager.enableAlphaTest();
+ GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1F);
```

item ID와 StateSet.CUTOUT 저장/복원은 유지한다. HUD에서 들어오면 lightmap OFF를 유지하고, 월드/손 렌더에서 이미 켜진 lightmap도 유지한다. HandRenderer가 직접 호출하는 `GbufferPrograms.setCutoutDefaults`는 그대로다. 색상 reset, Angelica 비활성화, 최적화 기능 비활성화는 사용하지 않았다. 함수 설명 주석은 사용자 지침에 맞춰 추가했고 UTF-8을 유지했다.

검증용 JAR 두 개는 각 원본을 복사하고 `net/coderbot/iris/uniforms/ItemIdManager.class` 한 엔트리의 해당 호출만 교체했다. 원본 JAR의 다른 모든 엔트리는 SHA-256 내용 비교로 동일함을 확인했다. 새 의존성을 추가하지 않았고, HoloInventory/Thaumic Horizons/.venv/외부 라이브러리는 수정하지 않았다.

| 결과 파일 | 기반 | 확인 |
|---|---|---|
| `build/verification/holo-nightvision/angelica-2.2.29-holo-lightmapfix.jar` | 비활성 원본 2.2.29 | 6,318 엔트리 중 한 클래스만 변경 |
| `build/verification/holo-nightvision/angelica-2.2.9fork-glyphfix2-holo-lightmapfix.jar` | 현재 활성 포크 | 6,335 엔트리 중 한 클래스만 변경; 기존 glyphfix 포함 내용 유지 |

두 JAR의 수정 클래스는 ASM BasicVerifier로 모든 메서드의 스택 분석도 통과했다. 모드 버전 metadata는 원본과 같다. 현재 포크의 관련 테스트 및 실게임 검증 결과를 원본 2.2.29 전체 런타임 검증으로 확대 해석하지 않는다.

설치된 RC2 JAR는 변경하지 않았다. 검증한 함수는 현재 JVM에 임시 적용되어 게임을 종료할 때까지 유지된다. 재시작 후에도 유지하려면 게임을 종료한 뒤 **현재 포크용 결과 JAR로 기존 활성 Angelica를 교체**해야 한다. Angelica JAR 두 개를 동시에 활성화하면 안 된다. 원본 2.2.29로 돌아갈 때는 원본용 결과 파일을 사용한다.

## 6. Regression 검증

프로덕션 수정 전에 새 GPU 테스트를 먼저 실행했다. 기존 구현에서는 4개 중 HUD pixel 비교와 nested caller-state 보존 두 테스트가 실패했다. 원인 특정 후 최소 수정을 적용하자 4개 모두 통과했다.

| 실제 GPU framebuffer 비교 | neutral lightmap | blue lightmap |
|---|---|---|
| 기존 item cutout, HUD unit 1 OFF 입력 | `[204,204,204]` | **`[51,102,204]`** |
| 수정 item cutout, HUD unit 1 OFF 입력 | `[204,204,204]` | `[204,204,204]` |
| 수정 item cutout, world unit 1 ON 입력 | 해당 없음 | `[51,102,204]` 유지 |

여기서 두 texture는 경로를 분리 검증하는 synthetic lightmap이다. 실제 Night Vision OFF/ON lightmap의 원시 픽셀이라고 표현하지 않는다. item color `(0.8,0.8,0.8,1)`는 테스트 입력이며 증상 제거용 reset이 아니다.

관련 회귀 테스트 총 **26개 통과, failure/error/skip 0**:

| suite | 통과 |
|---|---:|
| ItemCutoutLightmapGLTest | 4 |
| ItemTemplateCaptureGLTest | 3 |
| GLSM_PopAttribMaskFidelity_GLTest | 12 |
| GLSM_RetainedState_GLTest | 7 |

검증 내용은 HUD neutral/blue pixel 동일성, world lightmap 유지, hand defaults 유지, nested custom renderer 예외 시 alpha/lightmap 복원, native binding/active unit과 cache 일치, color/lighting 보존, 기존 item geometry capture, attribute mask fidelity 및 retained state 복원이다.

수정 후 실게임 148개 Holo cutout 샘플에서 unit 1 enable이 모두 false였다. 이전에는 같은 진입 구간이 true였다. 사용자는 렌즈 ON/OFF 전환 후 **“해결됐어. 다른 건 괜찮아.”**라고 확인했다. 요청한 홀로그램·손 아이템·바닥 아이템 확인에 대한 응답이다.

진단 transformer는 마지막에 제거했고 `PROBE REMOVED`를 기록했다. 현재 JVM의 검증한 최소 수정은 유지했다. 전체 모드팩 테스트나 모든 shaderpack/차원에 대한 검증을 수행한 것은 아니다.

재현/검증 명령:

```powershell
.\gradlew.bat --offline :glCoreTest --tests '*ItemCutoutLightmapGLTest' --console=plain
.\gradlew.bat --offline :glCoreTest --tests '*ItemCutoutLightmapGLTest' --tests '*ItemTemplateCaptureGLTest' :glsm:glCoreTest --tests '*GLSM_PopAttribMaskFidelity_GLTest' --tests '*GLSM_RetainedState_GLTest' --console=plain
```

조사 증거는 `build/verification/holo-nightvision/`에 보관했다: Holo/Angelica/lens bytecode, 수정 전·후 live state 로그, 수정 전 실패 및 수정 후 성공 XML, regression-results.json, live-summary.json, vanilla-lightmap-example.json, jar-verification.txt, 읽기 전용 probe와 단일 클래스 JAR 생성/검증 도구. 해당 디렉터리는 기존 gitignore 정책상 commit 대상이 아니다.

참고: [공개 증상 보고 #26647](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues/26647)도 같은 파란 tint를 기술한다. 그 보고를 이번 원인 판정이나 특정 버전의 regression 근거로 사용하지 않았다.
