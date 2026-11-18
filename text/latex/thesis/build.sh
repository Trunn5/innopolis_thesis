#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
ROOT="$(pwd)"
docker run --rm \
  -v "$ROOT:/work" \
  -w /work \
  texlive/texlive:latest \
  bash -lc 'pdflatex -interaction=nonstopmode thesis.tex && biber thesis && pdflatex -interaction=nonstopmode thesis.tex && pdflatex -interaction=nonstopmode thesis.tex'
