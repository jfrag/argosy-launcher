#!/usr/bin/env bash
set -u
log="${1:?usage: ci-annotate-errors.sh <build.log>}"
[ -f "$log" ] || { echo "::error::no log file $log"; exit 0; }

{
  grep -E '^e: |^w: .*(deprecated|DEPRECATION_ERROR)|error:|FAILED|requires|minCompileSdk|minAndroidGradlePluginVersion' "$log" | grep -vE '^w: ' | head -80
  echo "-----"
  awk '/\* What went wrong:/{f=1} f{print; n++} n>40{exit}' "$log"
} > ci-errors.txt

total=$(wc -l < ci-errors.txt)
echo "ci-errors.txt: $total lines"
split -l 25 -d ci-errors.txt ci-errors-part-
for f in ci-errors-part-*; do
  msg=$(sed -e 's/%/%25/g' -e 's/\r/%0D/g' "$f" | awk 'BEGIN{ORS="%0A"}{print}')
  echo "::error title=Build errors ($f)::$msg"
done
exit 0
