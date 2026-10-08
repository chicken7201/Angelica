# RC-2 위첨자 숫자 통일 — 2026-10-08

`2.2.30fork-glyphfix1`에서 리소스팩의 bitmap/Unicode 공급자가 위첨자 숫자를 서로 다르게 선택하여 `²³²Th`의 2와 3이 다른 굵기로 표시됐다. `2.2.30fork-glyphfix2`에서는 리소스팩 폰트 사용 시 위첨자 숫자 `⁰¹²³⁴⁵⁶⁷⁸⁹`를 Unicode 공급자로 모으고, Latin-1/U+20xx 페이지에서 빠진 숫자가 없고 높이가 같은 묶음 중 픽셀 점유율이 가장 낮은 묶음을 선택한다. 점유율이 같으면 기존 리소스팩 우선순위를 유지한다. 선택한 실제 글리프 폭으로 간격도 계산한다.

현재 Modernity/Zedtech 조합에서는 Modernity의 얇은 위첨자가 선택된다. 일반 글자, 아래첨자와 GTNH private-use 글리프 합성은 기존 경로를 유지한다. 커스텀 시스템 폰트 선택도 유지한다. 외부 리소스팩/라이브러리 및 `.venv`는 수정하지 않았으며 소스는 UTF-8이다.

## 검증

`VERSION=2.2.30fork-glyphfix2`로 `gradlew.bat --offline --console=plain :test :assemble`이 성공했다. Root 단위 테스트 868개와 실제 OpenGL 테스트 274개, 총 1,142개가 모두 통과했다. 새 회귀 테스트 5개는 숫자 범위, 두 페이지의 얇은 묶음 선택, 불완전한 묶음 거부, 해상도 차이 및 동률 우선순위를 검증한다. `git diff --check`도 통과했다.

PrismLauncher RC-2의 복사 월드 `_Angelica_superscript_20261008`에서 기존 리소스팩 순서(Modernity → Zedtech → WDMla 한국어 → curvy pipes 한국어), `ko_KR`, 기존 폰트 설정으로 실행했다. 채팅의 `(²³²Th) ⁰¹²³⁴⁵⁶⁷⁸⁹`, 아래첨자와 한글이 정상 표시됐다. 11:31:48 리소스팩 메뉴를 통한 실제 ResourceManager 재로드 후에도 같은 모양을 확인했다.

첨부 이미지의 원래 모드 아이템은 검색으로 확인하지 못했다. 대신 같은 화학식과 위첨자 숫자 전체를 lore에 넣은 테스트용 돌 아이템을 복사 월드에서 생성하여 실제 툴팁의 이탤릭 렌더링도 확인했다. Minecraft F2가 저장한 최종 framebuffer는 `build/verification/superscript-20261008/screenshots/2026-10-08_14.52.20.png`에 있다. 채팅 검증은 `2026-10-08_11.33.07.png`에 있다.

게임은 월드 저장 후 메인 메뉴에서 정상 종료했다. 원본 월드 544개 파일과 기존 NEI 데이터 87개 파일의 SHA-256이 검증 전과 모두 일치한다. 테스트 복사 월드/NEI 데이터는 검증 폴더로 보관했고, options 및 Angelica/shader 설정 4개 파일은 백업과 바이트 단위로 같은 상태로 복원했다. 일반 모드팩의 CTM/문서/번역 경고와 구형 Zedtech bitmap grid 거부 경고는 남아 있다.

## 설치 및 백업

RC-2 mods에는 `angelica-2.2.30fork-glyphfix2.jar`가 설치되어 있다. SHA-256은 `2AE7889C8F436B4D59FA50241B7DFD300047713934043F42C8AA7A717E076962`다. 같은 빌드 파일은 `build/libs`에 있다.

기존 glyphfix1 jar는 설치 전에 mods의 `angelica-2.2.30fork-glyphfix1.jar.before-superscript-20261008`과 검증 폴더의 `original-angelica.jar`로 백업했다. 원본과 백업 SHA-256은 `8FDA05BEF1005CC0F83A068D19961B309FF7CA98B20F8AB245C2699D0478BD3A`로 같다. 런타임 로그, 테스트 결과, 원본 manifest 및 복원 확인은 `build/verification/superscript-20261008`에 보관했다.
