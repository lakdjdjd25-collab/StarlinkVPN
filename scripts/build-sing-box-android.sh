#!/usr/bin/env bash
set -euo pipefail

SING_BOX_VERSION="v1.13.18"
SING_BOX_COMMIT="45ca32dcb966f07f97fc888fe8586e359dbe8405"
GOMOBILE_VERSION="v0.1.12"
RECOMMENDED_GO_VERSION="go1.25.12"

script_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "${script_directory}/.." && pwd)"
output_path="${1:-${repository_root}/apps/android/libs/libbox.aar}"
build_directory="$(mktemp -d "${TMPDIR:-/tmp}/quickping-sing-box.XXXXXX")"

cleanup() {
  rm -rf "${build_directory}"
}
trap cleanup EXIT

command -v go >/dev/null || {
  echo "Go is required to build libbox." >&2
  exit 1
}
if [[ "$(go env GOVERSION)" != "${RECOMMENDED_GO_VERSION}" ]]; then
  echo "Warning: official Android v1.13.18 uses ${RECOMMENDED_GO_VERSION}; found $(go env GOVERSION)." >&2
fi
command -v git >/dev/null || {
  echo "Git is required to fetch the pinned sing-box source." >&2
  exit 1
}
test -n "${ANDROID_HOME:-}" || {
  echo "ANDROID_HOME must point to an Android SDK with NDK 28.0.13004108." >&2
  exit 1
}

git clone --quiet --depth 1 --branch "${SING_BOX_VERSION}" \
  https://github.com/SagerNet/sing-box.git "${build_directory}/sing-box"

actual_commit="$(git -C "${build_directory}/sing-box" rev-parse HEAD)"
if [[ "${actual_commit}" != "${SING_BOX_COMMIT}" ]]; then
  echo "Pinned sing-box tag resolved to unexpected commit ${actual_commit}." >&2
  exit 1
fi

go install "github.com/sagernet/gomobile/cmd/gomobile@${GOMOBILE_VERSION}"
go install "github.com/sagernet/gomobile/cmd/gobind@${GOMOBILE_VERSION}"

(
  cd "${build_directory}/sing-box"
  go run ./cmd/internal/build_libbox -target android
)

mkdir -p "$(dirname "${output_path}")"
cp "${build_directory}/sing-box/libbox.aar" "${output_path}"
unzip -tq "${output_path}" >/dev/null
echo "Built sing-box ${SING_BOX_VERSION} (${SING_BOX_COMMIT}) at ${output_path}"
