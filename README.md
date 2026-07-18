# Xbox Game Pass Ultimate Clean List

This repository contains the validated Phase B Kotlin/JVM catalog generator, the Phase C GitHub Actions automation, and the Phase D web view that consumes the processed catalog files. Phase C has passed its manual success, change, and controlled-failure tests; validation of the first scheduled run is still pending.

## Project phases

| Phase | Scope | Status |
| --- | --- | --- |
| A | JavaScript catalog process run from the browser console | Complete |
| B | Validated Kotlin/JVM catalog generator | Complete |
| C | GitHub Actions automation and GitHub Pages deployment | Implemented and manually validated; first scheduled run pending |
| D | Web view for the generated catalog | Implemented and locally validated; publish through the existing workflow |

## Requirements

- JDK 25 (LTS)
- Internet access to Maven Central and the two public Xbox catalog endpoints

Maven does not need to be installed. The repository includes Maven Wrapper 3.3.4 and pins Maven 3.9.16.

## Verify the generator

```bash
./mvnw --batch-mode verify
```

This compiles the Kotlin and Java targets for version 25 and runs the unit tests without contacting Xbox.

## Generate the CSV files

```bash
./mvnw --batch-mode exec:java
```

The default output directory is `data/`. A different output directory can be supplied as the only application argument:

```bash
./mvnw --batch-mode exec:java -Dexec.args="build/generated-data"
```

The program first obtains and validates every catalog and every product title. It then stages and verifies the full set before replacing any published CSV. ICU4J supplies the `en-US` collation used to reproduce Phase A JavaScript ordering.

## GitHub Actions automation

The workflow at `.github/workflows/update-catalogs-and-pages.yml` can be started manually and is scheduled once a day at 3:30 a.m. in `America/New_York`. Scheduled runs add a random delay of 0 to 3,599 seconds; manual runs start immediately.

Each run:

1. Prepares JDK 25 and runs the automated tests.
2. Generates and validates exactly the six allowed CSV files.
3. Creates a `github-actions[bot]` commit only when valid data changes.
4. Deploys a clean artifact containing `index.html` and `data/` to GitHub Pages.

If a test, generation, file-set validation, safe push, or artifact step fails, no new content is deployed. The workflow never uses a personal token or force push.

Published site: <https://ninnex.github.io/xbox-gamepass-only-ultimate/>

## Phase D web view

The static view in `index.html` reads the two processed files directly:

- `data/ultimate-no-premium.csv` for the complete Ultimate minus Premium result.
- `data/ultimate-exclusive.csv` for the Ultimate Exclusive result.

It provides English search, platform and classification filters, category counts, list and grid layouts, responsive mobile behavior, and explicit loading and error states. It does not repeat the catalog comparison or classification rules in the browser.

The first Phase D version intentionally does not display the data update date. View changes do not have a dedicated `push` trigger. To publish a view change immediately, run **Update catalogs and deploy Pages** manually from GitHub Actions after committing it to `main`; otherwise the next scheduled catalog run will deploy the current `index.html`.

## Output contract

| File | Columns | Purpose |
| --- | --- | --- |
| `data/ultimate.csv` | `name,console,pc` | Full Ultimate source catalog |
| `data/premium.csv` | `name,console,pc` | Full Premium source catalog |
| `data/ea-play.csv` | `name,console,pc` | Full EA Play source catalog |
| `data/ubisoft-plus.csv` | `name,console,pc` | Full Ubisoft+ Classics source catalog |
| `data/ultimate-no-premium.csv` | `name,console,pc,category` | Ultimate minus Premium, classified by source |
| `data/ultimate-exclusive.csv` | `name,console,pc,category` | Ultimate minus Premium, EA Play, and Ubisoft+ Classics |

All files use UTF-8 with BOM, CRLF line endings, lowercase `true`/`false` values, and English catalog titles and category values.

## Scope

- Market: United States (`US`)
- Language: English (`en-us`)
- Platforms: Windows PC, Xbox One, and Xbox Series X|S
- Name matching: exact `ProductTitle`; no title normalization
- Category priority: `EA Play`, then `Ubisoft+ Classics`, then `Ultimate Exclusive`

