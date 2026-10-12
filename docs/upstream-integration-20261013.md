# upstream 통합 및 회귀 검증 — 2026-10-13

기존 `ca91fa95`에 upstream `85d0641a`까지의 새 커밋 15개를 통합했다. upstream 릴리스 `2.2.31`과 이후의 splash display list, Railcraft lantern, SDL-GPU 빈 버퍼 처리 및 테스트 개선이 포함된다. Thaumcraft portal, Little Tiles, AE2 cable, Serene Seasons, legacy GL 조회 및 shader 변환 수정도 반영했다. 원격 푸시나 릴리스 태그 생성은 하지 않았다.

## 로컬 기능 및 최적화 보존

기존 포크 변경 파일 89개 중 74개가 기존 HEAD와 바이트 단위로 같다. 나머지 15개는 upstream과 겹치는 파일 14개와 아래의 Unicode 로딩 테스트 환경 보정 1개다. 비교 결과는 `build/verification/upstream-20261013/preserved-fixes.json`에 있다.

glyphfix의 font batching, GTNH private-use glyph 우선순위, 위아래첨자 및 커스텀 폰트 메트릭, 리소스팩 Unicode 페이지 합성, 호환되지 않는 bitmap sheet fallback, 재로드 중 텍스처 보호 구현은 변경하지 않았다. GregTech 조명, HUD/Night Vision item cutout lightmap, worker/deferred 청크 AO snapshot, 시작 화면 이후 FFP 상태 복원도 유지했다.

캐시된 inventory mesh의 단일 draw, live lightmap 적용, uniform의 동일 내용 업로드 생략 및 기존 buffer range 재사용을 검증하는 테스트가 통과했다. 새 point-size 수정은 context별 최대값을 한 번 조회하고 캐시하는 구조를 유지한다. FPS 또는 전체 프레임 시간의 무회귀를 실측으로 보장한 결과는 아니다.

`.github/workflows/release-tags.yml`와 `gradle/mod-version.gradle.kts`는 기존 HEAD와 같다. upstream의 build/test workflow 개선은 반영했고 Discord 알림이나 CurseForge/Modrinth 배포 설정은 추가하지 않았다. 외부 설치 라이브러리와 `.venv`는 수정하지 않았다.

## 통합 과정의 수정

- 테스트 파일 2개의 병합 충돌에서 로컬 FFP replay/context handoff 및 inventory lighting 회귀 테스트를 유지하고 upstream의 parameterized test와 공용 helper를 반영했다.
- upstream에서 geometry shader 인자가 제거된 `Program.create` API에 맞춰 로컬 weather uniform 테스트를 수정했다.
- 기존 `3.4.2-SNAPSHOT`이 원격 저장소에서 해결되지 않아 LWJGL을 정식 `3.4.2`로 고정했다. `lwjgl3Release` 및 lwjgl3ify 런타임 버전과 일치하며 최종 전체 테스트가 이 버전으로 통과했다.
- 새 core-context GL 오류 검사가 기존 Unicode 로딩 테스트의 무변환 바닐라 `DynamicTexture` 할당 오류를 발견했다. 기존 fixture upload adapter에서 정확한 `GL_INVALID_OPERATION`을 확인하고 실제 업로드 후 `GL_NO_ERROR`를 검사하도록 보완했다. production 폰트 코드는 변경하지 않았다.
- image binding 테스트의 미생성 텍스처 이름을 실제 mip/layer가 있는 array texture로 교체했다. 유효한 입력에서도 binding tuple 보존을 검증하며 GL 오류 검사를 유지한다.
- upstream 최대 point-size 조회가 core profile에서 제거된 `GL_ALIASED_POINT_SIZE_RANGE`를 호출해 `GL_INVALID_ENUM`을 남기는 런타임 버그를 수정했다. core/GLES는 지원되는 range만 조회하고 compatibility 경로의 추가 조회는 유지한다. 첫 조회의 오류 없음과 캐시 재사용을 검사하는 회귀 테스트를 추가했다.

## 자동 검증

최종 명령:

```powershell
$env:VERSION = '2.2.31fork-glyphfix1'
.\gradlew.bat check assemble --continue --stacktrace
```

`BUILD SUCCESSFUL`, 108 tasks, 최종 실행 1분 24초. 실행 또는 Gradle이 동일 입력으로 확인한 테스트 결과는 총 3,285개 중 3,268개 통과, 17개 건너뜀, 실패/오류 0건이다.

| 테스트 | 전체 | 통과 | 건너뜀 |
| --- | ---: | ---: | ---: |
| Root unit | 911 | 911 | 0 |
| Root OpenGL core | 279 | 279 | 0 |
| GLSM unit | 268 | 268 | 0 |
| GLSM OpenGL compatibility | 267 | 267 | 0 |
| GLSM OpenGL core | 348 | 344 | 4 |
| GLSM shared context | 1 | 1 | 0 |
| GLSM SPIR-V | 25 | 25 | 0 |
| SDL GPU unit | 819 | 819 | 0 |
| GLSM-on-SDL real context | 139 | 138 | 1 |
| SDL GPU device | 23 | 22 | 1 |
| Tracy | 25 | 15 | 10 |
| Surround runtime | 111 | 110 | 1 |
| Surround obfuscated | 56 | 56 | 0 |
| Surround MixinExtras | 10 | 10 | 0 |
| Surround processor | 3 | 3 | 0 |

건너뛴 항목은 테스트 context의 기능/드라이버 조건, Metal 전용 native 검사, optional Tracy native smoke 및 Surround의 사전조건에 따른 것이다. `git diff --check` 및 변경 텍스트 파일의 UTF-8 검사를 통과했다.

최종 로그와 결과는 `build/verification/upstream-20261013/check-assemble-complete.log`, `test-summary.json`, `utf8-check.json`에 있다. OpenGL/SDL 테스트는 실제 GPU context를 사용했으며 모든 모드팩 장면이나 플랫폼을 검증한 것은 아니다.

## RC-2 검증의 범위

PrismLauncher `GT_New_Horizons_2.9.0-RC-2_Java_17-26`의 설정을 백업하고 복제 월드를 준비했으나 Computer Use의 앱 접근 승인이 시간 초과됐다. 새 후보 JAR의 실제 RC-2 화면 검사와 FPS 비교를 완료하지 못했다. 기존 JAR로 기준 실행을 요청했으나 비교 가능한 benchmark 결과는 수집하지 않았다. 따라서 이번 결과에 실제 RC-2 시각 검증 또는 성능 실측 성공을 포함하지 않는다.

후보 JAR은 RC-2에 설치하지 않았다. 기존 `angelica-2.2.30fork-glyphfix2.jar`의 SHA-256이 그대로이며 인스턴스 및 게임 설정은 백업으로 복원했다. 원본 월드 544개 파일과 NEI 91개 파일의 해시가 모두 시작 전과 같다. 사용하지 않은 복제 월드는 검증 폴더의 `unused-test-world`에 보관했다. 근거는 `final-state.json`과 `original-worlds-after.json`에 있다.

## 빌드 결과

배포용 JAR: `build/libs/angelica-2.2.31fork-glyphfix1.jar`.

SHA-256: `B38B46009214C1C5B6386D0AAFD10C91DB4B64C47D0FE71E1D025BE9726162D8`.

버전은 검증 빌드에 명시한 값이며 새 Git 태그나 원격 릴리스는 생성하지 않았다.
