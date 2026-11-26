# Linux CI post-build 검사 수정 — 2026-11-26

## 실패 지점

`d4cb4dc9`의 [Build and test 실행](https://github.com/chicken7201/Angelica/actions/runs/45630810123)에서 컴파일은 성공했지만 `Run post-build checks`의 `:glsm:glCoreTest`가 실패했다.

- `TextureSupersamplingGLTest.recoversTwelveDotsAtScaleThreeAndRestoresNearestAfterward(16)`: 분리된 점 12개 대신 2개를 검출했다.
- `TextureSupersamplingGLTest.atlasFetchesIgnoreSamplerFilteringAndRepeatAcrossNegativeUvs()`: 픽셀 15922의 값이 달랐다.

원래 검사를 수정하지 않고 실행한 [Linux 재현 실행](https://github.com/chicken7201/Angelica/actions/runs/45664110113)에서도 같은 두 오류와 같은 픽셀 값 차이가 발생했다. 반면 Windows의 하드웨어 및 Mesa 검사, Linux에서 이미지 저장을 추가한 진단 실행에서는 통과했다. 진단용 브랜치에서만 실험했으며 릴리스 워크플로는 변경하지 않았다.

## 입력 변경만으로는 해결되지 않음

`ba5b47d8`에서는 회전된 사각형과 점 패턴을 교체했다. [검증 브랜치 실행](https://github.com/chicken7201/Angelica/actions/runs/45664917791)은 통과했지만, [실제 master 실행](https://github.com/chicken7201/Angelica/actions/runs/45665658635)은 같은 두 검사에서 실패했다. 점 개수는 12 대신 8이었고 음수 UV의 픽셀 값도 달랐다. 따라서 입력 정밀도가 원인이라는 이전 판단은 입증되지 않았으며, 브랜치 실행 한 번의 성공만으로 해결을 판단할 수 없었다.

## 렌더링 대상 격리

두 실행은 동일한 Mesa 26.0.8과 LLVM 21.1.8을 사용했다. 여러 GL 테스트 JVM이 같은 Xvfb에서 실행되고 있었으며, 이 검사는 창의 기본 framebuffer에 렌더링하고 있었다. 테스트가 의도한 RGBA8 픽셀 검증을 창의 framebuffer 선택과 노출 상태에서 분리한다.

- 각 검사마다 200×200, single-sample RGBA8 renderbuffer와 전용 framebuffer를 생성한다.
- framebuffer completeness를 확인하고 렌더링과 readback을 모두 전용 대상으로 수행한다.
- 검사 종료 시 이전 draw/read framebuffer 및 renderbuffer binding을 복원하고 생성한 자원을 해제한다.
- 앞선 입력 변경을 되돌려 원래 회전된 사각형과 점 패턴으로 검사한다.
- 12개 점 복원, nearest 복원 및 픽셀 완전 일치 기대값을 유지한다. sampler/음수 UV 검사는 4회와 16회 샘플링 모두 유지한다.

배포 코드, glyphfix, shader 및 릴리스 설정은 변경하지 않는다. 후속 검증 자료는 `build/verification/ci-post-build-followup-20261126/`에 기록한다.
