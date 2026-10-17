# upstream 2.2.34 통합 및 회귀 검증 — 2026-10-17

기존 `3a650dbf`에 upstream `ab3bf176` (`2.2.34`)까지의 커밋 5개를 통합했다. 작업 시작 시 로컬 변경은 없었으며, `git fetch upstream --tags`로 최신 상태를 확인한 뒤 일반 병합을 적용했다.

## 반영한 upstream 변경

- `f81ee4b0`: Distant Horizons 1.7.10 backport의 macOS Metal 호환성을 개선한다. SDL-GPU에서도 LWJGL3 function provider를 제공하되 OpenGL 확장 함수는 없는 것으로 응답한다.
- `8e180962`: SDL-GPU 장치 초기화와 관계없는 충돌로 렌더러가 OpenGL로 초기화되는 문제를 수정한다. 장치 시작 실패가 연속 3회 누적될 때 전환하며, 성공한 드라이버·요구사항 조합을 기억한다.
- `8537835d`: SDL-GPU의 EP 플레이어 캡처를 수정한다. vertex-write replay가 기록하는 image도 감지하고 캡처를 그리기 전에 현재 렌더 패스를 다시 바인딩한다.
- `cfc6e12e`: GPU compute culling의 프로그램·indexed buffer 바인딩을 여러 패스에서 공유하고, indexed buffer 조회 캐시를 추가한다. 버퍼 삭제 시 캐시도 무효화한다.
- `ab3bf176`: 엔티티·block entity·아이템 ID를 정점별로 전달한다. tessellator 배치 중간 ID 변경과 큰 unsigned ID를 보존하며, GLSL의 지역 변수 이름 가림과 attribute 위치 충돌도 처리한다.

## 충돌 해결 및 기존 수정 보존

충돌은 `GLStateManager.java`의 포인트 크기 한도 조회 한 곳이었다. upstream은 context별 지연 조회를 없애고 GL 초기화 시 한 번 조회하도록 변경했다. 이 구조를 따르면서 기존의 Core/GLES 프로파일에서 지원하지 않는 `GL_ALIASED_POINT_SIZE_RANGE` 조회를 피하는 조건을 초기화 코드로 옮겼다.

첫 전체 검증은 기존 `GLSM_LegacyStackQueries_GLTest`가 제거된 `GLContextState.maxPointSize` 필드를 참조해 컴파일 단계에서 실패했다. 해당 테스트는 내부 필드 조작을 제거하고 초기화된 한도가 실제 드라이버 한도와 일치하는지, 반복 조회 시 GL 오류가 없는지를 검사하도록 맞췄다. 나머지 테스트는 삭제하거나 비활성화하지 않았다.

이전 공통 upstream `49db8e11`과 기존 HEAD 사이의 포크 변경 파일 148개를 비교했다. 138개는 기존 HEAD와 Git blob이 같고, 9개는 upstream 변경과 결합했으며, 나머지 1개는 위 회귀 테스트의 구조 대응이다.

glyphfix의 Unicode 페이지 합성, GTNH private-use glyph 우선순위, 위아래첨자 처리, 커스텀 폰트 메트릭, font batching, 텍스처 바인딩·리소스 재로드 보호 및 관련 테스트를 유지했다. FFP 상태 복원·weather uniform, GregTech inventory lighting, item cutout lightmap, 청크 조명 snapshot과 기존 렌더링 최적화도 유지했다.

`.github/workflows/release-tags.yml`, `gradle/mod-version.gradle.kts`, 의존성 버전 및 기존 glyphfix 파일은 변경하지 않았다. Discord 알림이나 CurseForge/Modrinth 배포 설정을 추가하지 않았다. 외부 설치 라이브러리와 `.venv`는 수정하지 않았다.

변경된 함수 137개에 기능 주석이 있음을 확인했다. 명시한 충돌 해결과 테스트 대응을 제외하고, 병합 결과에 추가한 편집은 주석뿐임을 Java 토큰 비교로 확인했다. 변경된 Java 파일 44개와 이 문서의 엄격한 UTF-8 디코딩 및 `git diff --check`도 검사했다.

파일별 보존 근거, 주석 검사, 코드 동등성 근거는 `build/verification/upstream-20261017-2.2.34/`의 `preserved-fixes.json`, `final-function-comments.json`, `merge-code-equivalence.json`, `utf8-check.json`에 있다.

## 검증

```powershell
$env:VERSION = '2.2.34fork-glyphfix1'
.\gradlew.bat check assemble --continue --stacktrace --console=plain
```

최종 실행은 `BUILD SUCCESSFUL`, 32초, 108 tasks 중 15개 실행 및 93개 up-to-date였다. 첫 실행에서 통과한 root·SDL-GPU 등의 결과는 Gradle이 동일 입력으로 확인했고, 수정한 GLSM 테스트와 OpenGL·SPIR-V 테스트는 다시 실행했다.

| 테스트 | 전체 | 통과 | 건너뜀 | 실패/오류 |
| --- | ---: | ---: | ---: | ---: |
| Root unit / OpenGL core | 1,198 | 1,198 | 0 | 0 |
| GLSM unit / OpenGL / SPIR-V | 911 | 907 | 4 | 0 |
| SDL-GPU unit / GLSM-on-SDL / device | 1,023 | 1,021 | 2 | 0 |
| Tracy | 25 | 15 | 10 | 0 |
| Surround | 180 | 179 | 1 | 0 |
| 합계 | 3,337 | 3,320 | 17 | 0 |

glyphfix, 폰트 GL 상태 복원, Unicode 텍스처 수명, inventory lighting, item cutout lightmap, 청크 조명 및 새 material ID·indexed buffer·startup guard 관련 테스트가 통과했다. 건너뜀은 기존과 같은 GL context 기능 조건, SDL stencil 검사, Metal 전용 검사, optional Tracy native 및 Surround 사전조건에 따른 것이다.

첫 실행 로그는 `check-assemble.log`, 최종 성공 로그는 `check-assemble-final.log`, XML 집계와 건너뜀 사유는 `test-summary.json`에 있다. 경로는 모두 위 검증 폴더 기준이다. 실제 모드팩 화면 및 FPS 비교는 수행하지 않았으며, 게임 인스턴스 파일도 변경하지 않았다.

## 빌드 결과

배포용 JAR: `build/libs/angelica-2.2.34fork-glyphfix1.jar` (12,242,506 bytes).

SHA-256: `28F12855D08CE76ADD250142A0ECAA4FFE2F21E228CDD065615D2A867053FBFF`.

JAR에 glyphfix 핵심 클래스와 upstream의 `TessellatorEntityIds`가 포함된 것도 확인했다. 버전은 검증 빌드에 명시한 후보 버전이며, 원격 푸시·Git 태그·릴리스는 생성하지 않았다.
