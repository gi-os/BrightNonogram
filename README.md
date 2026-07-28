# LightNonogram

Picross for the [Light Phone III](https://www.thelightphone.com/) — and the generator that feeds it.

A black-and-white grid puzzle is close to the ideal Light Phone tool: it's natively 1-bit, needs no network to play, and has no feed to scroll. This repo is the puzzle infrastructure first, because that's the part that decides whether the game is any good.

## Status

| Piece | State |
|---|---|
| `tools/picross-gen` — puzzle generator + solver | **Working.** Tests pass, output independently verified. |
| `packs/` — generated puzzle packs | **336 puzzles**, CC0. |
| Light SDK tool module — the actual app | Not started. |

## Why generate instead of importing

There is no large, freely-redistributable nonogram corpus. [webpbn.com](https://webpbn.com/) is the canonical archive — tens of thousands of puzzles — but every one is copyrighted by its author and explicitly not licensed for redistribution. Collections that ship with open-source nonogram games tend to be either tiny or of unclear origin.

That matters more than usual here: Light builds and signs community tools **from a public git commit and archives the source at build time**. Anything with murky provenance in this repo would be permanent and attributable.

So the puzzles are generated, validated, and CC0.

## Layout

```
packs/                    generated puzzle packs + index.json (CC0-1.0)
tools/picross-gen/        the generator — see its README for the algorithm
  src/main/kotlin/        line solver, grid solver, generator, CLI
  src/test/kotlin/        solver correctness suite
  ondevice/               drop-in files for the Light tool module
```

## The one idea that matters

Every puzzle is checked against an **optimal line solver** before it ships. That solver deduces exactly the cells forced by a line's clues — no more, no less — and never guesses. If pure line logic can't finish a candidate grid, the candidate is thrown away.

This is what separates a real Picross from a coin flip. A randomly-generated grid is usually either ambiguous or trivial; the validator is the whole product.

The property worth testing isn't "does the solver solve puzzles" but *"is every deduction it makes actually forced?"* An over-eager solver silently ships unsolvable puzzles and nobody finds out until a player is stuck on level 40. So the suite cross-checks it against exhaustive enumeration:

```
PASS  overlap [8] in 10 forces cells 2..7
PASS  a known EMPTY splits the line, only one segment fits [3]
PASS  two viable segments force nothing
PASS  500 random grids: every deduction agrees with truth
PASS  'Solved' always means exhaustively unique
```

## Quick start

```bash
cd tools/picross-gen
./gradlew test
./gradlew run --args="random --size 15 --count 150 --seed 1 --pack-id core-15"
./gradlew run --args="image --in art/ --size 15 --pack-id shapes"
```

## Packs

`packs/index.json` is the manifest a client polls; each pack is fetched only when its `version` changes.

| Pack | Size | Count |
|---|---|---|
| `core-10` | 10×10 | 60 |
| `core-15` | 15×15 | 150 |
| `hard-15` | 15×15 | 50 |
| `core-20` | 20×20 | 80 |

Solutions are stored as Base64 row-major bitmasks — a 20×20 is 50 bytes. Clues are derived at load time, never stored, so they can't drift out of sync with the picture.

## Next

- Light SDK tool module: `lighttool.toml`, `HomeScreen`, `PuzzleScreen`
- Compose grid with axis-locked drag-fill (the feature that separates a good Picross from a tedious one)
- Progress storage via DataStore, kept separate from the read-only packs

## Licenses

Code is MIT. Generated puzzles in `packs/` are CC0-1.0.
