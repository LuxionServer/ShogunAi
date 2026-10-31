#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: prepare-repository.sh <folder>" >&2
  exit 2
fi

REPOSITORY="$1"
mkdir -p "$REPOSITORY/config"
cd "$REPOSITORY"

git init --quiet --initial-branch=main
git config user.name "ShogunAi smoke test"
git config user.email "smoke-test@example.invalid"

printf '# Sample project\n' > README.md
printf '.env.local\nconfig/local.json\n' > .gitignore
printf '{\n  "copy": ["config/local.json"]\n}\n' > .shogunai.json
git add README.md .gitignore .shogunai.json
git commit --quiet -m "Start the sample project"

printf 'GREETING=hello from the base repository\n' > .env.local
printf '{ "port": 8080 }\n' > config/local.json
