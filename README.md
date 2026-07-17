# Xbox Game Pass Ultimate Clean List

This repository currently contains Stage 1B-A: a Kotlin/JVM console generator that reproduces the validated Stage 1A Xbox Game Pass catalog process. GitHub Actions automation belongs to Stage 1B-B and is intentionally not included yet.

## Requirements

- JDK 21
- Internet access to Maven Central and the two public Xbox catalog endpoints

Maven does not need to be installed. The repository includes Maven Wrapper 3.3.4 and pins Maven 3.9.16.

## Verify the generator

```bash
./mvnw --batch-mode verify
```

This compiles the Kotlin and Java targets for version 21 and runs the unit tests without contacting Xbox.

## Generate the CSV files

```bash
./mvnw --batch-mode exec:java
```

The default output directory is `data/`. A different output directory can be supplied as the only application argument:

```bash
./mvnw --batch-mode exec:java -Dexec.args="build/generated-data"
```

The program first obtains and validates every catalog and every product title. It then stages and verifies the full set before replacing any published CSV. ICU4J supplies the `en-US` collation used to reproduce Stage 1A JavaScript ordering.

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
