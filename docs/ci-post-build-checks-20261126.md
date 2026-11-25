# Linux CI post-build 검사 수정 — 2026-11-26

## 실패 지점

`d4cb4dc9`의 [Build and test 실행](https://github.com/chicken7201/Angelica/actions/runs/45630810123)에서 컴파일은 성공했지만 `Run post-build checks`의 `:glsm:glCoreTest`가 실패했다.

- `TextureSupersamplingGLTest.recoversTwelveDotsAtScaleThreeAndRestoresNearestAfterward(16)`: 분리된 점 12개 대신 2개를 검출했다.
- `TextureSupersamplingGLTest.atlasFetchesIgnoreSamplerFilteringAndRepeatAcrossNegativeUvs()`: 픽셀 15922의 값이 달랐다.

원래 검사를 수정하지 않고 실행한 [Linux 재현 실행](https://github.com/chicken7201/Angelica/actions/runs/45664110113)에서도 같은 두 오류와 같은 픽셀 값 차이가 발생했다. 반면 Windows의 하드웨어 및 Mesa 검사, Linux에서 이미지 저장을 추가한 진단 실행에서는 통과했다. 진단용 브랜치에서만 실험했으며 릴리스 워크플로는 변경하지 않았다.

## 수정 내용

실패한 두 검사는 작은 점들을 회전된 사각형에 그린 뒤 연결 영역 개수와 픽셀의 완전한 일치를 요구했다. 회전 연산과 가까운 샘플 경계에 대한 의존성을 줄이도록 두 검사의 합성 입력을 교체했다.

- 명시적인 행렬로 축을 교환하고 정확히 절반 크기로 축소한다.
- 샘플 위치가 texel 경계에서 떨어지도록 사각형 위치를 정한다.
- 점 간격을 넓혀 16회 샘플링의 가장자리 픽셀이 이웃 점과 연결되지 않게 한다.
- nearest에서는 점이 보이지 않고 supersampling에서는 12개가 복원되는지, 설정 복원 후 원래 픽셀과 일치하는지 계속 검사한다.
- sampler의 filtering/clamp 설정과 음수 UV 반복 검사는 기존 16회에 더해 4회 샘플링도 검사한다.

검사 기대값을 완화하거나 검사를 건너뛰지 않았다. 기울어진 사각형의 투명 texel 색상과 모델 텍스처 filtering 검사는 기존 입력을 유지한다. 배포 코드, glyphfix, shader 및 릴리스 설정은 변경하지 않았다.

로컬 Mesa 전체 GL core 검사에서 442개 중 441개 통과, 1개 건너뜀, 실패·오류 0개를 확인했다. [수정 후 Linux 실행](https://github.com/chicken7201/Angelica/actions/runs/45664917791)에서는 원래 `GTNewHorizons/GTNH-Actions-Workflows`의 build-and-test 워크플로를 사용해 컴파일과 `Run post-build checks`가 통과했다. 임시 검증 실행에는 저장소 비밀값을 전달하지 않았다. 최종 변경에는 진단용 워크플로를 포함하지 않는다. 검증 자료는 `build/verification/ci-post-build-20261126/`에 있다.
