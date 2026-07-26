# Xbox Game Pass Ultimate Clean List

This repository contains the Kotlin/JVM catalog generator, the GitHub Actions automation, and the static web view for the current Xbox Game Pass Ultimate, Premium, and Essential catalogs.

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

The program queries the ten configured SIGL catalog sources and resolves their complete set of unique Product IDs in one Display Catalog request. It resolves an official Xbox Store URL for every Product ID, preserves per-list new-game dates, and validates the complete candidate set before replacing any published data. It never requests an individual Xbox Store page: when structured product metadata has no Store URL, it uses the official `-/PRODUCT_ID` route directly. ICU4J supplies the `en-US` collation used to reproduce Phase A JavaScript ordering.

To generate into a candidate directory while comparing against the currently published baseline:

```bash
./mvnw --batch-mode exec:java -Dexec.args="build/generated-data data"
```

## GitHub Actions automation

The workflow at `.github/workflows/update-catalogs-and-pages.yml` can be started manually and is scheduled once a day at 3:30 a.m. in `America/New_York`. Scheduled runs add a random delay of 0 to 3,599 seconds; manual runs start immediately.

Each run:

1. Prepares JDK 25 and runs the automated tests.
2. Generates exactly seven CSV files and `catalog-info.json` in a candidate directory.
3. Validates headers, values, Product IDs, Store paths, dates, UTF-8 without BOM, and LF line endings before copying the complete set to `data/`.
4. Creates a `github-actions[bot]` commit after every successful query. `catalog-info.json` changes even when the catalogs do not.
5. Deploys a clean artifact containing `index.html` and the eight data files to GitHub Pages.

If a test, generation, file-set validation, safe push, or artifact step fails, no new content is deployed. The workflow never uses a personal token or force push.

Published site: <https://ninnex.github.io/xbox-gamepass-only-ultimate/>

## Phase D web view

The static view in `index.html` reads all seven CSV files and `catalog-info.json` directly. Its selector exposes five result sets: Ultimate minus Premium, Ultimate Exclusive, full Ultimate, full Premium, and full Essential.

It provides English search, platform and classification filters, category counts, list and grid layouts, responsive mobile behavior, official Xbox Store links, and explicit loading and error states. A **New game** label is calculated in the browser from `newSinceDate` and `newGameDisplayDays`; the CSV remains unchanged when the label expires.

The visible timestamp comes from `catalog-info.json:lastCheckedAt` and therefore represents the latest successful catalog query, even when no CSV changed. View changes do not have a dedicated `push` trigger. To publish a view change immediately, run **Update catalogs and deploy Pages** manually from GitHub Actions after committing it to `main`; otherwise the next scheduled catalog run will deploy the current `index.html`.

## Output contract

| File | Columns | Purpose |
| --- | --- | --- |
| `data/ultimate.csv` | `name,productId,console,pc,storePath,newSinceDate` | Full Ultimate source catalog |
| `data/premium.csv` | `name,productId,console,pc,storePath,newSinceDate` | Full Premium source catalog |
| `data/essential.csv` | `name,productId,console,pc,storePath,newSinceDate` | Full Essential source catalog |
| `data/ea-play.csv` | `name,productId,console,pc,storePath,newSinceDate` | Full EA Play source catalog |
| `data/ubisoft-plus.csv` | `name,productId,console,pc,storePath,newSinceDate` | Full Ubisoft+ Classics source catalog |
| `data/ultimate-no-premium.csv` | `name,productId,console,pc,category,storePath,newSinceDate` | Ultimate minus Premium, classified by source |
| `data/ultimate-exclusive.csv` | `name,productId,console,pc,category,storePath,newSinceDate` | Ultimate minus Premium, EA Play, and Ubisoft+ Classics |
| `data/catalog-info.json` | Common JSON configuration | Store base URL, new-game display duration, last successful check, and CSV change result |

All CSV files use UTF-8 without BOM, LF (`\n`) line endings, lowercase `true`/`false` values, and English catalog titles and category values. `storePath` comes from structured Xbox product metadata when available; otherwise it is the official `-/PRODUCT_ID` route. Every path ends in the uppercase Product ID. `newSinceDate` is empty for the migration baseline; later additions use `YYYY-MM-DD` and retain their first-seen date while they remain in that specific list.

## Scope

- Market: United States (`US`)
- Language: English (`en-us`)
- Platforms: Windows PC, Xbox One, and Xbox Series X|S
- Name matching: exact `ProductTitle`; no title normalization
- Category priority: `EA Play`, then `Ubisoft+ Classics`, then `Ultimate Exclusive`
