# LightNonogram

Picross for the [Light Phone III](https://www.thelightphone.com/), and the generator
that feeds it.

A black-and-white grid puzzle suits this phone. It is one-bit by nature, it needs no
network, and it has no feed to scroll. This repo holds the puzzle infrastructure first,
because that part decides whether the game is any good.

Part of the [gi-os Light App collection](#the-gi-os-light-app-collection).

## Status

| Piece | State |
| --- | --- |
| `tools/picross-gen`, the generator and solver | Working. Tests pass and the output checks out independently. |
| `packs/`, the generated puzzles | 336 puzzles, CC0. |
| The light-sdk tool module, the game itself | Not started. |

## Why generate instead of import

No large, freely redistributable nonogram corpus exists. [webpbn.com](https://webpbn.com/)
is the canonical archive, with tens of thousands of puzzles, but each one belongs to its
author and carries no redistribution license. The collections that ship with open-source
nonogram games are either small or of unclear origin.

That matters more than usual here. Light builds and signs a community tool from a public
git commit and archives the source at build time. Anything with murky provenance in this
repo would stay permanent and attributable.

So the puzzles are generated, validated, and CC0.

## The one idea that matters

Every puzzle passes an optimal line solver before it ships. That solver deduces exactly
the cells a line's clues force, no more and no less, and it never guesses. If pure line
logic cannot finish a candidate grid, the generator throws the candidate away.

This is what separates a real Picross from a coin flip. A random grid is usually either
ambiguous or trivial. The validator is the product.

The property worth testing is not "does the solver solve puzzles". It is "is every
deduction it makes actually forced". An over-eager solver ships unsolvable puzzles in
silence, and nobody finds out until a player sticks on level 40. So the suite
cross-checks it against exhaustive enumeration.

```
PASS  overlap [8] in 10 forces cells 2..7
PASS  a known EMPTY splits the line, only one segment fits [3]
PASS  two viable segments force nothing
PASS  500 random grids: every deduction agrees with truth
PASS  'Solved' always means exhaustively unique
```

## Layout

```
packs/                    generated packs and index.json (CC0-1.0)
tools/picross-gen/        the generator, see its README for the algorithm
  src/main/kotlin/        line solver, grid solver, generator, CLI
  src/test/kotlin/        solver correctness suite
  ondevice/               drop-in files for the Light tool module
```

## Quick start

```sh
cd tools/picross-gen
./gradlew test
./gradlew run --args="random --size 15 --count 150 --seed 1 --pack-id core-15"
./gradlew run --args="image --in art/ --size 15 --pack-id shapes"
```

`packs/index.json` is the manifest a client polls. It fetches a pack only when the
`version` field changes.

## Origin and credits

- **[The Light Phone](https://www.thelightphone.com/)** for
  [light-sdk](https://github.com/lightphone/light-sdk), which the tool module will use.
  `tools/picross-gen/ondevice/` already holds the two files that module needs,
  `PackSync.kt` and `PuzzleCodec.kt`.
- **[webpbn.com](https://webpbn.com/)**, built by the late Jan Wolter and kept running
  since, is the reference archive for this puzzle form. Its
  [solver survey](https://webpbn.com/survey/) and its write-ups on line-solving and
  puzzle difficulty shaped the solver here. No webpbn puzzle appears in this repo,
  because each one belongs to the person who posted it and carries no redistribution
  license. Thank you for the documentation.
- The line solver follows the standard left-most and right-most packing overlap method
  that the nonogram-solving literature describes. The implementation is original.
- Every puzzle in `packs/` is CC0-1.0. Take them.

[LightSolitaire](https://github.com/gi-os/LightSolitaire) went the other way in this
collection. It put a complete game in the SDK `tool` module first, so it is the model to
follow when this repo builds its own.

## The gi-os Light App collection

Eight tools for the Light Phone III, all open source, all built in one run.

| Tool | What it does | Built on |
| --- | --- | --- |
| [LightPass](https://github.com/gi-os/LightPass) | Photograph a movie ticket, keep the stub | Plain Android |
| [LightQR](https://github.com/gi-os/LightQR) | QR scanner, plus a browser generator | Plain Android |
| [LightRSS](https://github.com/gi-os/LightRSS) | RSS and Atom reader with images and QR subscribe | light-sdk, fork of [zachattack323/LightRSS](https://github.com/zachattack323/LightRSS) |
| [LightNYCSubway](https://github.com/gi-os/LightNYCSubway) | Live MTA subway arrivals | light-sdk fork |
| [chat](https://github.com/gi-os/chat) | iMessage over a self-hosted BlueBubbles server | Fork of [craigeley/chat](https://github.com/craigeley/chat) |
| [LightFog](https://github.com/gi-os/LightFog) | Fog of World companion, GPS recorder and fog map | Fork of [garado/light-topographic](https://github.com/garado/light-topographic) |
| **LightNonogram** (this repo) | Picross, plus a generator that only ships solvable puzzles | Kotlin generator, light-sdk tool |
| [LightSolitaire](https://github.com/gi-os/LightSolitaire) | Klondike, draw one, unlimited redeals | light-sdk |

The Light Phone does not sponsor or endorse any of these.

## License

Code is MIT, see [LICENSE](LICENSE). Puzzle packs are CC0-1.0, see
[packs/LICENSE](packs/LICENSE).
