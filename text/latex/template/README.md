# Innopolis thesis LaTeX template (unchanged stub)

University `extreport` template: `title.pdf`, demo chapters, `ref.bib` + BibLaTeX.

Do not put your thesis text here. Edit files under `../thesis/`.

## Title page (official workflow)

1. Open the [thesis title template](https://tfs.university.innopolis.ru/tfs/Edu/_git/doe-docs?path=%2Fthesis_title_template&version=GBmaster&_a=contents) (Word).
2. Fill title, names (RU + EN).
3. Export to `title.pdf` and replace the file in this directory.

## Build smoke test (Docker)

From repo root:

```bash
docker run --rm -v "$PWD/text/latex/template:/work" -w /work texlive/texlive:latest \
  bash -lc 'pdflatex thesis.tex && biber thesis && pdflatex thesis.tex && pdflatex thesis.tex'
```
