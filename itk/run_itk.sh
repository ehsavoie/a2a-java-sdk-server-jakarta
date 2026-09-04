#!/bin/bash
# ITK harness for a2a-java-sdk-server-jakarta — thin shim over a2a-itk's shared driver.
set -e
cd "$(dirname "${BASH_SOURCE[0]}")"

ITK_SDK_NAME=java
ITK_SCENARIO_SET=shared
ITK_COPY_PROTO=0
ITK_MOUNT_ITK_DIR=0

: "${A2A_ITK_REVISION:?A2A_ITK_REVISION environment variable must be set}"
if [ ! -d a2a-itk ]; then
  git clone https://github.com/a2aproject/a2a-itk.git a2a-itk
  git -C a2a-itk checkout "$A2A_ITK_REVISION"
fi

source a2a-itk/scripts/run_itk_shared.sh
