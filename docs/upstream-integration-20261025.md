# 최신 upstream 통합 및 회귀 검증 — 2026-10-25

기존 `c1cff54a`에 GTNewHorizons/Angelica `master`의 `34ed1d101b1b466e78ed6fe263744ba888bed0a9`까지 22개 커밋을 병합했다. 시작 시 작업 트리는 깨끗했다. `git fetch --prune upstream`과 GitHub branch API로 최신 HEAD를 확인했고, 검증 중 다시 조회한 HEAD도 같았다. 최근 릴리스 `2.2.37` 이후의 커밋 4개까지 포함한다.

## 반영한 upstream 변경

- BetterLoadingScreen 로딩 스레드에서 modern bitmap font를 조회하다 발생하는 동시 수정 오류를 방지한다.
- Birch/spruce 잎의 biome 색상, 연결된 유리판 및 여러 렌더링 오류를 수정한다.
- 엔티티 텍스처 atlas와 애니메이션 TESR instancing을 적용하고, 모드가 batched TESR용 shader를 등록할 수 있게 한다.
- 엔티티 shadow replay와 exit-state capture를 도입하고, shadow camera 및 armor/item glint material 분리를 반영한다.
- SDL-GPU framebuffer의 resize·texture 삭제 후 attachment 보존, uniform buffer 읽기 및 texture 처리 수정을 반영한다.
- Streaming ring fence 처리, questbook 종료 시 GPU stall, mailbox present mode 프레임 제한을 개선한다.
- Tile entity lightmap 조회를 tick 단위로 캐시하고, NBT block mapping에 metadata 조건을 반영한다.
- Celeritas `2.5.13-GTNH`, 개발용 OpenComputers 의존성, 일본어 번역 및 upstream 테스트 구조 개선을 반영한다. 설치된 외부 라이브러리 소스는 수정하지 않았다.

## 충돌 해결과 로컬 수정 보존

충돌 파일은 9개였다. 같은 기능의 충돌은 upstream 구현을 우선했다. SDL 테스트 agent는 upstream의 공통 GLSM redirect agent를 사용하며, biome tint는 upstream의 arm/disarm 방식, 나머지 model batching·parameterized test·중복 테스트 정리도 upstream 구조를 따른다.

`CompatUniformManager`는 upstream의 fog·model-view-projection uniform과 로컬 inventory lightmap uniform을 서로 다른 location으로 결합했다. 기존 lightmap 값의 변경 감지와 context 전환 시 무효화도 유지했다.

`Uniforms`는 upstream의 fog 계산·matrix helper와 프레임 종료 시 binding 무효화를 적용했다. 프레임이 끝나면 로컬 `boundOffset`도 폐기해, upstream의 새 fence 수명 규칙대로 다음 프레임에 새 범위를 할당한다. 같은 프레임 내 context handoff에서는 로컬의 바인딩 복원과 불필요한 uniform 재업로드 방지 기능을 유지한다. 기존 upstream의 `unchangedContentStillWritesAfterFrameEnd`와 로컬의 `unchangedUniformDataIsNotRewrittenAfterRebind` 검사를 함께 유지했다.

첫 검증에서 다음 테스트 환경 차이를 확인해 보완했다.

- 이미지 바인딩 테스트 4개가 저장 공간 없는 texture ID로 인해 `GL_INVALID_VALUE`를 발생시켰다. upstream의 새 삭제·기본값 검사는 유지하면서 로컬의 완전한 array texture fixture를 복원했다.
- 로컬 `ItemCutoutLightmapGLTest` 4개는 pixel·상태 검사를 통과했으나, cleanup의 FFP 비활성화가 upstream의 새 shared fixture 규칙과 충돌했다. cleanup이 FFP를 활성 상태로 남기도록 맞췄다.

검사를 삭제하거나 비활성화하지 않았다. 이전 공통 upstream `ab3bf176`과 기존 HEAD 사이의 로컬 변경 파일 180개 중 138개는 기존 HEAD와 Git blob이 같다. 41개는 upstream 변경과 결합했고, 나머지 1개는 위 item cutout 테스트의 lifecycle 대응이다.

Unicode 페이지 합성, GTNH private-use glyph 우선순위, 위아래첨자 통일, 커스텀 font metric, font batching, texture binding·resource reload 보호를 유지했다. FFP 상태 복원·weather uniform 분리, GregTech inventory lighting, item cutout lightmap, 청크 조명 snapshot 및 기존 버그 수정도 유지했다.

`.github/workflows/release-tags.yml`과 `gradle/mod-version.gradle.kts`는 기존 HEAD와 Git blob이 같다. upstream의 build/test workflow 변경도 별도로 검토했다. 공통 build-and-test workflow를 master로 되돌리는 변경이며, release workflow에는 적용하지 않았다. 로컬 태깅·버전·빌드·배포 동작을 보존했으며 Discord 알림이나 CurseForge/Modrinth 배포를 추가하지 않았다. `.venv`도 수정하지 않았다.

변경 함수의 기능 주석을 보완했다. Java 278개 파일은 명시한 충돌 해결·fixture 대응을 저장한 기준 소스와 토큰이 같으며, 이후 추가 편집은 주석뿐이다. 변경 함수 1,864개의 기능 주석, 엄격한 UTF-8 디코딩, `git diff --check`도 확인했다.

보존 및 검증 근거는 `build/verification/upstream-20261025/`의 `preserved-fixes.json`, `merge-code-equivalence.json`, `utf8-check.json`, `final-function-comments.json`에 있다.

## 검증

```powershell
$env:VERSION = '2.2.37fork-glyphfix1'
.\gradlew.bat check assemble --continue --stacktrace --console=plain
```

최종 실행은 `BUILD SUCCESSFUL`, 4분 34초였다. 110 tasks 중 63개를 실행했고 47개는 up-to-date였다. 결과 XML 16개 테스트 작업을 최종 실행 로그와 대조했다.

| 테스트 | 전체 | 통과 | 건너뜀 | 실패/오류 |
| --- | ---: | ---: | ---: | ---: |
| Root unit / OpenGL core | 1,450 | 1,450 | 0 | 0 |
| GLSM unit / OpenGL / SPIR-V | 1,115 | 1,110 | 5 | 0 |
| SDL-GPU unit / GLSM-on-SDL / device | 1,017 | 1,015 | 2 | 0 |
| Tracy | 25 | 15 | 10 | 0 |
| Surround | 180 | 179 | 1 | 0 |
| 합계 | 3,787 | 3,769 | 18 | 0 |

glyphfix, Unicode font texture 수명, 폰트 GL 상태 복원, inventory lighting, item cutout lightmap, 청크 조명, 새 exit-state capture·shadow replay·texture atlas·shader·fence 관련 검사를 포함한다. 건너뜀 사유는 검증 폴더의 `test-summary.json`에 기록했다.

최초 로그는 `check-assemble.log`, 최종 로그는 `check-assemble-final.log`다. 실제 모드팩 화면 및 FPS 비교는 수행하지 않았고 게임 인스턴스 파일도 변경하지 않았다.

## 빌드 결과

배포용 JAR: `build/libs/angelica-2.2.37fork-glyphfix1.jar`.

크기: 12,510,688 bytes. SHA-256: `1E9F949A96E4B8F6B0B03BFC5D43220AB2CADBD5E065AA3819FC949BC3F24EB1`.

JAR에 glyphfix 핵심 클래스와 새 `EntityShadowReplay`, `EntityTextureAtlas`, `ExitStateCapture`가 포함된 것도 확인했다.

버전은 검증 빌드에 지정한 후보 이름이며 upstream `2.2.37` 이후 변경도 포함한다. 원격 push·Git tag·release는 생성하지 않았다.
