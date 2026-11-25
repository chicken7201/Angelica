# upstream 통합 및 회귀 검증 — 2026-11-25

기존 `a4a899dc`에 upstream `master`의 `fbfcafc7b579b9054ddaca1e9888ba244bf4549d`까지 6개 커밋을 병합했다. 이 커밋은 `2.2.47` 태그와 같다. 시작 시 작업 트리는 깨끗했다.

## 반영한 변경

- 반투명 지형과 타일 엔티티의 정렬, 렌더링 순서 및 중간 flush 처리를 개선했다.
- 반투명 레이어 합성, terrain split, glow depth와 depth peeling을 적용했다.
- 기존 반투명 정렬 설정을 새 설정으로 이전하는 upstream 구현을 적용했다.
- LiquidLogged 유체 높이를 청크 snapshot에 보존한다.

## 충돌 해결과 로컬 수정 보존

`StateSet`, `Program`, `ShaderManager`, `RetainedTesrGroupsGLTest`의 충돌을 해결했다. 같은 기능은 upstream 구현을 사용했다. `StateSet`의 글꼴 polygon-offset 보호는 upstream에도 포함되어 있어 upstream 상태 구성을 사용한다. upstream의 blend override 검사와 retained group 회귀 검사도 적용했다.

`Program`과 `ShaderManager`는 upstream의 peeling/coverage 유니폼과 로컬의 독립 날씨 유니폼을 함께 갱신한다. 날씨 값으로 공통 FFP 블록 레이아웃이 달라지지 않도록 한 기존 수정은 유지했다.

이전 공통 upstream과 기존 HEAD 사이에서 로컬 변경 파일은 413개다. 이 중 388개는 기존 HEAD와 내용이 완전히 같다. 나머지 25개는 upstream 변경과 결합했다. 이 25개 중 20개는 주석과 공백을 제외하면 upstream 코드와 같다. 나머지 5개에는 독립 날씨 유니폼, OpenGL 4.0 미지원 환경의 ARB blend 함수 경로, 로컬 mixin 등록, terrain depth 상태 복원 코드가 남는다. upstream 전용 Java 파일 38개도 주석과 공백을 제외하면 upstream과 같다.

glyphfix 글꼴 코드와 테스트는 기존 HEAD와 완전히 같다. Unicode 페이지 합성, GTNH private-use glyph 우선순위, 위아래첨자 처리, glyph metric, texture 수명과 binding, resource reload 보호를 유지했다. inventory lighting, item cutout lightmap, chunk lighting snapshot 등 기존 수정도 유지했다.

`.github/workflows/release-tags.yml`과 `gradle/mod-version.gradle.kts`는 기존 HEAD와 같다. Discord 알림이나 CurseForge/Modrinth 배포를 추가하지 않았다. 외부 라이브러리 설치 파일과 `.venv`는 수정하지 않았다. 변경 파일의 UTF-8 디코딩과 `git diff --check`를 확인했다. 새 함수에는 간략한 설명 주석을 추가했다.

## 검증 및 산출물

```powershell
$env:VERSION = '2.2.47fork-glyphfix1'
.\gradlew.bat check assemble -x :lwjgl3-backend:glesTest --continue --stacktrace --console=plain
```

이 Windows 환경에서 GLES 초기화가 실패한 이전 검증 기록에 따라 GLES 전용 작업만 명령행에서 제외했다. 소스에서 검사를 삭제하거나 비활성화하지 않았다.

검사는 총 4,316개 중 4,298개 통과, 18개 건너뜀, 실패·오류 0개였다. glyphfix, font GL 상태, Unicode texture 수명과 binding, inventory lighting, item cutout lightmap, terrain depth, 날씨 렌더링 및 새 반투명 합성·정렬·유체 높이 회귀 검사를 포함한다. 실제 모드팩 플레이와 화면·FPS 비교는 수행하지 않았다.

검증 로그와 집계는 `build/verification/upstream-20261125/`에 있다. `check-assemble.log`는 첫 성공 실행, `check-assemble-final.log`는 최종 소스 재검증이다. `preservation.json`, `local-code-differences.json`, `upstream-code-equivalence.json`, `test-summary.json`에 보존 검사와 테스트 집계를 기록했다.

기존 각 모듈의 `build/libs` JAR 24개를 정리한 뒤 빌드했다. 배포용 JAR은 `build/libs/angelica-2.2.47fork-glyphfix1.jar`이다. 버전은 로컬 검증용이며 원격 push, tag 또는 release 생성은 하지 않았다.
