#!/usr/bin/env bash
# Download the Walch 2019 PhysioNet `sleep-accel` dataset to a local
# fixture directory. The companion PhoneSleepValidationTest reads
# fixtures from this directory and produces per-subject + aggregate
# sensitivity / specificity / total-sleep-time RMSE metrics against
# the PSG-labelled ground truth. #244 Cut 3.
#
# The dataset is ~700 MB and lives at:
#   https://physionet.org/content/sleep-accel/1.0.0/
#
# License: Open Data Commons Attribution License v1.0 (ODC-By). The
# data is openly downloadable — no PhysioNet credentialing required.
#
# After running this script, point the validation test at the
# fixture directory via either:
#   - JVM system property: -Dbios.physionet.dir=/path/to/sleep-accel
#   - environment variable: BIOS_PHYSIONET_DIR=/path/to/sleep-accel
#
# If neither is set, the validation test is skipped (the JUnit
# Assume) — CI and developer machines without the dataset continue
# to pass.

set -euo pipefail

DEFAULT_DIR="${HOME}/datasets/physionet/sleep-accel"
TARGET_DIR="${1:-${BIOS_PHYSIONET_DIR:-${DEFAULT_DIR}}}"

mkdir -p "${TARGET_DIR}"

echo "Mirroring sleep-accel (~700 MB) to: ${TARGET_DIR}"
echo "  (use BIOS_PHYSIONET_DIR or pass a directory to override)"

# -r recursive, -N timestamp-based skip, -c continue on partial, -np no parent dirs.
# --cut-dirs=4 collapses the physionet.org/files/sleep-accel/1.0.0/ path
# prefix so the fixture layout matches what the reader expects.
wget \
    --recursive \
    --no-host-directories \
    --no-parent \
    --timestamping \
    --continue \
    --cut-dirs=4 \
    --directory-prefix="${TARGET_DIR}" \
    --reject="index.html*" \
    https://physionet.org/files/sleep-accel/1.0.0/

echo
echo "Done. To run the validation:"
echo "  cd android && ./gradlew :app:testStandaloneDebugUnitTest \\"
echo "      --tests com.bios.app.PhoneSleepValidationTest \\"
echo "      -Dbios.physionet.dir=${TARGET_DIR}"
