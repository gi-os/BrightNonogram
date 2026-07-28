# picross-gen

Generates nonogram (Picross) puzzle packs that are **guaranteed uniquely solvable by pure logic** — no guessing, no backtracking, no ambiguity. Built to feed a Light Phone III tool, but the output is just JSON and works anywhere.

It exists because there is no large, freely-redistributable nonogram corpus. Rather than ship puzzles of murky provenance, generate your own and own the copyright outright.

## Quick start

```bash
./gradlew installDist

# 150 random puzzles, 15x15
build/install/picross-gen/bin/picross-gen random \
    --size 15 --count 150 --seed 1 --pack-id core-15 --name "Core 15"

# turn a folder of art into puzzles
build/install/picross-gen/bin/picross-gen image \
    --in art/ --size 15 --pack-id shapes --name "Shapes"

# rebuild the manifest the app fetches
build/install/picross-gen/bin/picross-gen index \
    --base-url https://your-cdn.example.com/packs

# prove the solver still deduces only what's actually forced
build/install/picross-gen/bin/picross-gen selfcheck
```

Zero runtime dependencies. Kotlin stdlib and the JDK, nothing else.

## How it guarantees solvability

Everything rests on one component: an **optimal single-line solver**. Given a line's known cells and its clues, it deduces exactly the cells that are forced — no more, no less.

It runs two O(n·k) passes. The first computes, backwards, whether clues `j..k` can fit in cells `i..n`. The second walks forward through the reachable-and-feasible states, recording for each cell whether it *can* be filled and whether it *can* be empty in some valid arrangement. A cell that can only be one thing is forced; a cell that can be neither means the clues are unsatisfiable.

That "can it be X in *some* arrangement" framing is what makes it optimal. The common leftmost/rightmost overlap trick misses deductions that arise once known cells split a line into segments.

The grid solver just runs that over rows and columns to a fixpoint, re-queueing any line a deduction touched. **It never backtracks, on purpose.** A puzzle needing a guess is a bad Picross puzzle, so "line logic gets stuck" and "reject this candidate" are the same event.

### Difficulty

Two signals come out of the solve:

- `passes` — full row+column sweeps to completion.
- `meanDepth` — average sweep at which a cell resolved. Size-independent, so it compares fairly across 10×10 and 20×20.

Tiers 1–5 are assigned by **quintile within the batch** rather than absolute cutoffs, because a 20×20 naturally needs more sweeps than a 10×10 and fixed thresholds would label every large puzzle "hard".

## Generating from random grids

Yield depends sharply on fill ratio (measured, 400 trials per cell):

| size  | fill 0.45 | fill 0.55 | fill 0.65 |
|-------|-----------|-----------|-----------|
| 10×10 | 27%       | 69%       | 94%       |
| 15×15 | 7%        | 51%       | 92%       |
| 20×20 | 0%        | 41%       | 85%       |

Sparser grids make *harder* puzzles but are far more often ambiguous. `--fill 0.45 --smooth 0` gives a median of 10 passes; the default `--fill 0.58 --smooth 1` gives 4.

`--smooth` runs a cellular-automaton majority pass that turns salt-and-pepper noise into rounded blobs — they look more like pictures and produce longer, more satisfying clue runs.

## Generating from art

Load → box-downsample → threshold (Otsu by default) → validate.

Averaging rather than point-sampling on the downsample matters: at 15×15 each cell is a large chunk of the original, and nearest-neighbour drops thin strokes entirely.

**Clean art is very often ambiguous.** Solid symmetric regions admit multiple clue-consistent solutions — a plain ring is the textbook case. So when validation fails, `repair` kicks in: it solves the grid, looks at exactly which cells line logic could not pin down, tries flipping each, and keeps whichever flip most reduces the unresolved count. In practice **one or two flips fix a 15×15 icon** — under 1% of cells, invisible in the finished picture.

```
size 10: images: 6  kept: 6 (repaired 1)  rejected: 0
size 15: images: 6  kept: 6 (repaired 0)  rejected: 0
size 20: images: 6  kept: 6 (repaired 0)  rejected: 0
```

## Output format

`index.json` — the manifest the app polls:

```json
[{"id":"core-15","name":"Core 15","version":1,"count":150,
  "url":"https://cdn.example.com/packs/core-15.json"}]
```

`core-15.json`:

```json
{
  "id": "core-15", "name": "Core 15", "version": 1, "license": "CC0-1.0",
  "puzzles": [
    {"id":"a3f19c40e2b7","w":15,"h":15,"bits":"AH4A/gH...","difficulty":3,"passes":4}
  ]
}
```

`bits` is a row-major bitmask, MSB-first per byte, Base64. A 20×20 solution is 400 bits = 50 bytes = 68 characters. **Only the solution is stored** — clues are derived at load time, which is cheap and removes any chance of clues disagreeing with the picture.

`id` is a content hash, so regenerating a pack does not churn ids and player progress survives.

Bump `version` to trigger a client re-download of that pack.

## On-device

`ondevice/` holds drop-in files for the Light tool module, free of blocked Android imports:

- **`PuzzleCodec.kt`** — bit decoding, clue derivation, win detection. No Android dependencies at all, so it unit-tests on the JVM.
- **`PackSync.kt`** — `PackStore` (atomic writes to `filesDir`) and `PackSync` (version-diffed fetch over ktor), plus the `@LightJob` wiring.

Storage is plain files rather than Room. Room *is* allow-listed and is the right call if you later want cross-pack queries, but a 150-puzzle pack is ~15 KB and the only access patterns are "list packs" and "load one" — files keep both the dependency count and the review surface smaller. Player progress is the part that wants DataStore or Room, since it's written constantly; keep it separate from the read-only packs.

## Verification

`selfcheck` (and `./gradlew test`) cross-checks the solver against **exhaustive enumeration** on small grids. The property that matters isn't "does it solve puzzles" — it's *"is every deduction it makes actually forced?"* An over-eager solver silently ships unsolvable puzzles and you don't find out until a player is stuck.

```
PASS  overlap [8] in 10 forces cells 2..7
PASS  a known EMPTY splits the line, only one segment fits [3]
PASS  two viable segments force nothing
PASS  flat black-on-white art never thresholds to a blank grid
PASS  500 random grids: no false contradictions
PASS  500 random grids: every deduction agrees with truth
PASS  'Solved' always means exhaustively unique
```

The generated packs in this repo were additionally re-solved by a separate reference implementation written in another language, reading the shipped JSON — 226/226 verified.

## Licensing

Puzzles you generate are yours. The default `--license CC0-1.0` is recorded in each pack; change it with `--license`.

This matters for Light Phone distribution specifically: Light builds and signs tools **from a public git commit and archives the source at build time**. Committing scraped puzzles of unknown provenance makes that permanent and attributable. Generating your own sidesteps it.

Do use [webpbn.com](https://webpbn.com/) puzzles for benchmarking your solver — just don't redistribute them.
