# upstream 2.2.33 통합 및 회귀 검증 — 2026-10-15

기존 `83c254d3`에 upstream `49db8e11` (`2.2.33`)까지의 커밋 3개를 통합했다. 작업 시작 시 로컬 변경은 없었으며, 원격에서 최신 upstream을 가져온 뒤 일반 병합을 적용했다. 충돌은 없었다.

## 반영한 upstream 변경

- `01ff612d`: 애니메이션 프레임 보간에 알파 가중치를 반영한다.
- `bd9a3c3c`: Witchery 가마솥 바닥이 그려지지 않는 문제를 수정한다. 적합한 block entity tessellator quad를 배치에 넣고, name tag의 예상 draw를 별도로 추적한다.
- `49db8e11`: SDL-GPU에서 필터링이 필요 없는 일부 2D sampler를 texture slot으로 옮겨 Iris의 sampler 여유를 늘린다. Vulkan용 사본의 갱신·해제, depth/sRGB 변환, sampler unit 변경 및 slot 초과 처리와 관련 회귀 테스트를 포함한다.

## 기존 수정 보존

이전 공통 upstream `0cae2be1`과 기존 HEAD 사이의 포크 변경 파일 98개를 비교했다. 97개는 기존 HEAD와 Git blob이 같고, 나머지 `Mixins.java`에는 upstream의 `MixinRender_NameTagDraws` 등록 한 줄만 추가됐다.

glyphfix의 Unicode 페이지 합성, GTNH private-use glyph 우선순위, 위아래첨자 처리, 커스텀 폰트 메트릭, font batching, 텍스처 바인딩·리소스 재로드 보호 및 관련 테스트를 유지했다. 기존 FFP 상태 복원, GregTech inventory lighting, item cutout lightmap, 청크 조명 snapshot 및 렌더링 최적화 수정도 그대로다.

`.github/workflows/release-tags.yml`, `gradle/mod-version.gradle.kts` 및 의존성 버전은 변경하지 않았다. Discord 알림이나 CurseForge/Modrinth 배포 설정을 추가하지 않았다. 외부 설치 라이브러리와 `.venv`는 수정하지 않았다.

새로 추가되거나 변경된 함수에는 간단한 기능 주석을 보완했다. 자동 병합 결과와 최종 소스를 비교했으며, 이 추가 편집은 모두 주석과 공백뿐이다. 변경 텍스트 53개 파일의 엄격한 UTF-8 디코딩과 `git diff --check`를 확인했다.

파일별 보존 근거는 `build/verification/upstream-20261015-2.2.33/preserved-fixes.json`, 병합 이후 코드 동등성 근거는 `merge-code-equivalence.json`에 있다.

## 검증

```powershell
$env:VERSION = '2.2.33fork-glyphfix1'
.\gradlew.bat check assemble --continue --stacktrace
```

첫 전체 실행은 `BUILD SUCCESSFUL`, 3분 4초였다. 함수 주석 정리를 마친 최종 소스로 다시 실행한 결과도 `BUILD SUCCESSFUL`, 2분 10초, 108 tasks 중 35개 실행 및 73개 up-to-date였다.

최종 실행 또는 Gradle이 동일 입력으로 확인한 결과는 다음과 같다.

| 테스트 | 전체 | 통과 | 건너뜀 | 실패/오류 |
| --- | ---: | ---: | ---: | ---: |
| Root unit / OpenGL core | 1,198 | 1,198 | 0 | 0 |
| GLSM unit / OpenGL / SPIR-V | 909 | 905 | 4 | 0 |
| SDL-GPU unit / GLSM-on-SDL / device | 1,018 | 1,016 | 2 | 0 |
| Tracy | 25 | 15 | 10 | 0 |
| Surround | 180 | 179 | 1 | 0 |
| 합계 | 3,330 | 3,313 | 17 | 0 |

glyphfix, 폰트 GL 상태 복원, Unicode 텍스처 수명, inventory lighting, item cutout lightmap, 청크 조명 및 새 sampler 관련 테스트가 통과했다. 건너뜀은 기존과 같은 GL context 기능 조건, SDL stencil 검사, Metal 전용 검사, optional Tracy native 및 Surround 사전조건에 따른 것이다.

전체 로그는 `check-assemble.log`, 최종 로그는 `check-assemble-final.log`, XML 집계와 건너뜀 사유는 `test-summary.json`에 있다. 경로는 모두 위 검증 폴더 기준이다. 실제 모드팩 화면 및 FPS 비교는 수행하지 않았으며, 게임 인스턴스 파일도 변경하지 않았다.

## 빌드 결과

배포용 JAR: `build/libs/angelica-2.2.33fork-glyphfix1.jar` (12,227,595 bytes).

SHA-256: `1B5307AA9A80ED82527AF8C00FF3D27D3179827348887AE0E370EAE8D29A745D`.

JAR에 glyphfix 관련 핵심 클래스가 포함된 것도 확인했다. 버전은 검증 빌드에 명시한 후보 버전이며, 원격 푸시·Git 태그·릴리스는 생성하지 않았다.
