# upstream 통합 및 RC-2 검증 — 2026-10-08

기존 `229087c459`에 upstream `159f7ff8cc55e1bdd2eea7d31d0a33495d192ccc`까지의 새 커밋 17개를 통합했다. 새 폰트 시트, Renderer 설정 탭, 셰이더 로딩/캐시 개선, legacy GL 조회, 청크 컬링, 누수 및 SDL GPU 수정이 포함된다. 원격 저장소에는 푸시하지 않았다.

기존 포크 변경 파일 82개 중 76개의 Git blob이 기존 HEAD와 같다. 나머지 6개는 upstream과 겹치는 폰트 배치/선택/인터페이스, shader transformer, DarkMode targets, mixin 등록 파일이다. GTNH 전용 글리프 우선순위, Unicode 페이지 합성 및 재로드 보호, 커스텀 폰트 메트릭, deferred raster 상태, FFP 상태/바인딩 복원, GregTech 조명 및 item cutout lightmap 수정은 유지했다. 자세한 비교는 로컬 `build/verification/upstream-20261008/preserved-fixes.json`에 있다.

폰트 배치 충돌은 upstream의 AA geometry 확장과 bitmap ascent 처리를 통합하면서 기존 provider별 sampling 경계와 커스텀 폰트 baseline을 유지하도록 해결했다.

## 인게임에서 발견하고 수정한 회귀

첫 통합 후보를 RC-2에서 실행했을 때 화학식과 위아래첨자 일부가 깨졌다. 활성 Zedtech-GTNH-1.8.0의 `nonlatin_european.png`는 128×304, `accented.png`는 144×312였지만, 새 정의는 각각 67행과 75행을 요구한다. 정수 나눗셈으로 셀 높이를 계산하면 다른 글리프 영역을 읽게 된다.

`FontProviderBitmap`이 정의와 정확히 나누어지는 bitmap grid인지 검사하도록 수정했다. 맞지 않는 시트는 기존 Unicode provider로 fallback한다. 리소스팩 파일 자체는 변경하지 않았다. 실제 시트 크기를 재현한 거부 사례, 현재 bundled/Modernity 시트 및 2배 시트 허용, 읽을 수 없는 이미지와 빈 정의를 포함한 회귀 테스트 10개를 추가했다.

같은 RC-2 리소스팩 순서에서 수정 JAR로 재실행하여 화학식, 위아래첨자, 한국어, 그리스 문자 및 GTNH private-use glyph가 다시 정상 표시되는 것을 확인했다. 리소스 재로드 뒤에도 동일한 문자열을 다시 검증했다.

## 자동 검증

최종 코드에서 root/GLSM/SPIR-V/SDL 테스트와 `assemble`이 성공했다. Tracy와 Surround 테스트도 성공했다. 총 3,133개 중 3,117개 통과, 16개 건너뜀, 실패 및 오류 0건이다.

| 테스트 | 전체 | 실패/오류 | 건너뜀 |
| --- | ---: | ---: | ---: |
| Root unit | 863 | 0 | 0 |
| Root OpenGL core | 274 | 0 | 0 |
| GLSM unit | 260 | 0 | 0 |
| GLSM OpenGL compatibility | 278 | 0 | 0 |
| GLSM OpenGL core | 316 | 0 | 2 |
| GLSM shared context | 1 | 0 | 0 |
| GLSM SPIR-V | 22 | 0 | 0 |
| SDL GPU unit | 849 | 0 | 2 |
| GLSM-on-SDL real context | 130 | 0 | 1 |
| Tracy | 26 | 0 | 10 |
| Surround runtime | 111 | 0 | 1 |
| Surround processor | 3 | 0 | 0 |

건너뛴 항목에는 Metal 전용 image atomics, optional Tracy native smoke 및 테스트 컨텍스트의 기능/사전조건 제한이 포함된다. XML 결과, `test-summary.json`, `upstream-20261008-fixed-tests.log`, backend test log를 로컬 build 폴더에 보관했다. 테스트한 소스의 UTF-8 및 `git diff --check`를 확인했다. 외부 설치 라이브러리와 `.venv`는 수정하지 않았다.

## 실제 RC-2 실행

PrismLauncher의 `GT_New_Horizons_2.9.0-RC-2_Java_17-26`에서 Java 25.0.1, NVIDIA RTX 3050 Laptop GPU, OpenGL 4.6/LWJGL3, 기존 322개 모드와 리소스팩으로 실행했다. 원본을 복제한 `_Angelica_upstream_20261008` 월드만 사용했다.

확인한 장면은 메인 메뉴, 월드 지형/기계/엔티티/HUD, 위의 폰트 샘플, 낮과 밤의 GregTech 기계 아이템(metadata 354), 야간 투시 중 인벤토리 색상, 새 Renderer 탭의 Active/OpenGL 상태, 리소스 재로드 뒤 폰트/텍스처 및 메인 메뉴 복귀다. 실제 reload는 08:31:47경 시작했고, block/item atlas가 08:31:58/08:32:04에 재생성됐으며 sound engine은 08:32:05에 다시 시작했다. 재로드 후 문자열과 장면은 08:33:05 스크린샷으로 저장했다. 게임을 정상 종료했다.

스크린샷은 Minecraft F2가 저장한 실제 framebuffer PNG이며 로컬 `build/verification/upstream-20261008/screenshots`에 있다. 로그 검색에서 GL 오류, shader compile/link 실패, mixin 적용 실패, font batch/page 실패 및 fatal exception은 발견하지 못했다. `OutOfMemoryError` 클래스의 Mixin metadata 조회 1건은 실제 예외가 아니므로 제외했다. 일반 모드팩 경고와 호환되지 않는 구형 bitmap sheet를 건너뛰는 경고는 남아 있다.

이번 실제 게임 테스트는 Windows/OpenGL 및 위의 장면에 대한 결과다. 외부 shaderpack은 활성화되어 있지 않았고, SDL GPU 게임 플레이, macOS/Metal 및 모든 모드 기능을 직접 실행한 것은 아니다. 해당 backend와 shader 변환 경로는 위의 자동 테스트 범위에서 검증했다. HoloInventory의 개별 hologram 장면은 이번 실행에서 따로 만들지 않았다.

원본 월드 540개 파일과 기존 NEI 캐시 87개 파일이 시작 전 SHA-256과 모두 일치한다. 테스트가 만든 NEI 캐시 2개와 복제 월드는 검증 폴더의 `tested-nei`/`tested-world`에 보관했다. `options.txt`, Angelica 설정 및 shader 설정은 백업에서 복원했다. `final-state.json`과 원본 manifest를 검증 폴더에 보관했다.

## 최종 JAR 및 백업

최종 JAR은 `build/libs/angelica-2.2.30fork-glyphfix1.jar`이며 RC-2의 mods에 설치되어 있다. 빌드 시 `VERSION=2.2.30fork-glyphfix1`을 명시했다.

SHA-256: `8FDA05BEF1005CC0F83A068D19961B309FF7CA98B20F8AB245C2699D0478BD3A`.

원본 `angelica-2.2.29fork-glyphfix3.jar`는 RC-2 mods의 `.before-upstream-20261008` 비활성 파일과 검증 폴더의 `original-angelica.jar`로 보관했다. 두 백업의 SHA-256은 원본과 같은 `FB6F2AFF99E3A7E87150BED043422BF491A9385631AAE888A0F7DF951943CBCE`다. 초기 회귀 후보와 로그도 `first-candidate.jar`/`first-attempt`로 보관했다.
