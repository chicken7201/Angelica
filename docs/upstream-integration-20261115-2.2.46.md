# upstream 통합 및 회귀 검증 — 2026-11-15

기존 `1cccd10d`에 `GTNewHorizons/Angelica`의 `master` 커밋 `996fb54cb05e256ea6039f4e2c8457a221a179b7`까지 5개 커밋을 병합했다. 이 커밋은 upstream `2.2.46` 태그와 같다. 시작할 때 작업 트리는 깨끗했다.

## 반영한 upstream 변경

- 모드용 날씨 column API 및 provider weather instancing을 적용했다.
- FluidLogged와 LiquidLogged에 저장된 유체를 청크 mesher에서 렌더링한다.
- LOTR 호환성 및 Biomes O' Plenty 잎 렌더링 수정을 반영했다.
- Extra Utilities ethereal connected texture 렌더러가 월드 참조를 보유하는 누수를 수정했다.

## 충돌 해결과 기존 기능 보존

충돌은 `FFPUniformBlock`, `Uniforms`, `FlybyRunner`, `AngelicaChunkBuilderMeshingTask` 네 파일에서 발생했다. 같은 기능은 upstream 구현을 따랐다. Flyby의 biome 검색·survival 상태 복원과 유체 렌더링은 upstream 구조를 사용한다.

기존 날씨 유니폼 분리 수정에는 upstream의 대체 수정이 없으므로 보존했다. 새 upstream 날씨 데이터 구조와 계산식을 그대로 사용하고, `Program`과 `VertexShaderGenerator`에서 provider tick·age·radius를 네 번째 독립 유니폼까지 연결했다. 공통 FFP 블록의 레이아웃과 바인딩 복원 수정은 유지했다. GL 회귀 검사는 provider 값 전달과 tick wrap을 추가 검증한다.

이전 공통 upstream과 기존 HEAD 사이의 로컬 변경 파일 394개 중 385개는 기존 HEAD와 내용이 완전히 같다. 9개는 upstream 변경 또는 새 날씨 구조와 결합했다. upstream 전용 Java 파일 24개는 주석·공백을 제외하면 upstream 코드와 같다.

Unicode 페이지 합성, GTNH private-use glyph 우선순위, 위아래첨자 통일, custom glyph metric, 글꼴 텍스처 수명·binding·resource reload 보호, font GL 상태 복원은 변경하지 않았다. 청크 조명 snapshot, inventory lighting, item cutout lightmap 및 기존 렌더링 상태 복원 수정도 유지했다.

`.github/workflows/release-tags.yml`과 `gradle/mod-version.gradle.kts`는 기존 HEAD와 같다. upstream은 이번에 workflow를 변경하지 않았다. Discord 알림 또는 CurseForge/Modrinth 배포를 추가하지 않았고, 외부 라이브러리 소스나 `.venv`를 수정하지 않았다. 변경 함수의 설명 주석과 UTF-8 인코딩을 확인했다.

## 검증

```powershell
$env:VERSION = '2.2.46fork-glyphfix1'
.\gradlew.bat check assemble --continue --stacktrace --console=plain
.\gradlew.bat check assemble -x :lwjgl3-backend:glesTest --continue --stacktrace --console=plain
.\gradlew.bat assemble --stacktrace --console=plain
```

첫 전체 실행에서는 `:lwjgl3-backend:glesTest`의 초기화 하나가 실패했다. 이 Windows 환경에서 SDL이 OpenGL ES 라이브러리를 초기화하지 못했다(`SDL_CreateWindow failed: Could not initialize OpenGL / GLES library`). 나머지 검사는 완료됐으며 소스에서 테스트를 비활성화하거나 삭제하지 않았다.

GLES 전용 작업만 명령행에서 제외한 최종 `check assemble`은 `BUILD SUCCESSFUL`이었다. 테스트 XML과 해당 실행 로그를 대조한 결과 전체 4,294개 중 4,276개 통과, 18개 건너뜀, 실패·오류 0개였다. GLES 초기화 실패 한 건은 이 합계에 포함하지 않았다. glyphfix와 Unicode texture 수명·binding, font GL 상태, inventory lighting, item cutout lightmap, 청크 조명, 새 날씨의 CPU reference 대 instancing pixel parity 및 Iris weather shader link 검사를 포함한다.

검증 후 설명 주석을 보완한 소스에 대해 배포용 JAR과 sources JAR을 다시 생성했다. 실제 모드팩 플레이와 화면·FPS 비교는 수행하지 않았다.

검증 자료는 `build/verification/upstream-20261115/`에 있다.

- `check-assemble.log`: GLES 초기화 실패를 포함한 전체 실행
- `check-assemble-final.log`, `test-summary.json`: GLES 작업을 제외한 최종 검사와 XML 집계
- `assemble-final.log`: 최종 주석까지 포함한 산출물 갱신
- `preserved-fixes-fast.json`: 로컬 변경 보존·UTF-8·release workflow 확인
- `upstream-code-equivalence.json`: upstream 전용 Java 코드 비교

## 빌드 결과

배포용 JAR은 `build/libs/angelica-2.2.46fork-glyphfix1.jar`이다. 이전 각 모듈의 `build/libs` 산출 JAR 534개를 정리한 뒤 빌드했다. `fork-glyphfix1`은 로컬 검증용 버전이며 원격 tag나 release를 만들지 않았다. 원격 push도 수행하지 않았다.
