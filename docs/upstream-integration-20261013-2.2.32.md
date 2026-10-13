# upstream 2.2.32 통합 및 회귀 검증 — 2026-10-13

기존 `5c1805e1`에 upstream `0cae2be1` (`2.2.32`)을 통합했다. 새 upstream 커밋은 Chisel energised voidstone의 테두리 모서리 누락 수정 1개다.

## 변경 내용과 보존 확인

- `AngelicaChunkBuildContext`의 material 선택을 `QuadMaterialSelector`로 분리했다. 같은 면에 base material이 필요한 quad가 있으면 뒤따르는 overlay도 같은 render pass에 남겨 테두리 누락을 방지한다. 정적 불투명 면의 pass 최적화와 shader override 처리는 유지한다.
- upstream의 selector 회귀 테스트 7개를 반영했다. 수정한 함수와 새 함수에는 간단한 기능 주석을 추가했다.
- upstream의 LWJGL `3.4.2` 변경은 로컬에서 이미 적용한 값과 같다. 충돌한 설명 주석은 로컬 버전을 유지했다. 최종 `gradle/libs.versions.toml`은 기존 HEAD와 같다.
- 이전 upstream `85d0641a` 대비 기존 포크 변경 파일 94개를 비교했고, 모두 기존 HEAD와 Git blob이 같다. glyphfix, GTNH private-use glyph 및 위아래첨자 처리, Unicode 페이지 합성·재로드 보호, 커스텀 폰트 메트릭, FFP 상태 복원, GregTech 조명, item cutout lightmap 및 청크 조명 snapshot 수정이 보존됐다.
- `.github/workflows/release-tags.yml`과 `gradle/mod-version.gradle.kts`도 기존 HEAD와 같다. 릴리스 자동화 변경, Discord 알림 및 CurseForge/Modrinth 배포 추가는 없다. 외부 설치 라이브러리와 `.venv`는 수정하지 않았다.

파일별 비교 근거는 `build/verification/upstream-20261013-2.2.32/preserved-fixes.json`에 있다. 이번 통합의 코드 변경은 chunk context 및 새 selector와 테스트 파일 3개뿐이다.

## 검증

```powershell
$env:VERSION = '2.2.32fork-glyphfix1'
.\gradlew.bat check assemble --continue --stacktrace
```

`BUILD SUCCESSFUL`, 2분 14초, 108 tasks 중 40개 실행, 68개 up-to-date.

| 테스트 결과 | 전체 | 통과 | 건너뜀 | 실패/오류 |
| --- | ---: | ---: | ---: | ---: |
| 이번에 실행한 root unit | 918 | 918 | 0 | 0 |
| 이번에 실행한 root OpenGL core | 279 | 279 | 0 | 0 |
| 동일 입력으로 up-to-date 확인된 GLSM/SDL/Tracy/Surround | 2,095 | 2,078 | 17 | 0 |
| 합계 | 3,292 | 3,275 | 17 | 0 |

새 Chisel 테스트 7개, glyph metrics·Unicode 페이지 합성·fallback·재로드 보호, font raster state, item cutout lightmap 및 청크 조명 snapshot 관련 기존 root 테스트가 통과했다. 건너뜀은 이전 검증과 같은 backend 기능·플랫폼·optional native 및 테스트 사전조건에 따른 것이다. 변경 텍스트의 엄격한 UTF-8 디코딩과 `git diff --check`도 통과했다.

로그와 XML 집계는 `build/verification/upstream-20261013-2.2.32/check-assemble.log`, `test-summary.json`에 있다. 변경 범위와 자동 테스트 결과를 검토해 이번 통합에서는 PrismLauncher RC-2 실행을 추가하지 않았다. 따라서 실제 모드팩 화면 확인이나 FPS 비교 결과는 포함하지 않는다. 인스턴스 파일도 변경하지 않았다.

## 빌드 결과

배포용 JAR: `build/libs/angelica-2.2.32fork-glyphfix1.jar`.

SHA-256: `D36EC68AB264B9036C94384770E2F5DAB7FA48C2F634853AE9D568E963E2329D`.

빌드에 명시한 후보 버전이며 Git 태그 생성이나 원격 푸시·릴리스는 하지 않았다.
