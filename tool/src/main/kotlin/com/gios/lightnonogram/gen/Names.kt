package com.gios.lightnonogram.gen

/**
 * Names for generated puzzles.
 *
 * A generated puzzle is already fully determined by its seed, so its name is too.
 * That means the collection stores one Int per puzzle and nothing else — no name
 * strings, no pictures — and the same seed always yields the same title.
 *
 * The mixing is hand-rolled rather than using `kotlin.random`. Not because the
 * stdlib is unstable, but because a name that silently changes would quietly
 * rewrite someone's collection, and arithmetic I can read is easier to promise
 * than a library's internals. Bump [NAMES_VERSION] if the lists or the mixing
 * change and you accept old names shifting.
 */
object Names {

    const val NAMES_VERSION = 1

    /**
     * SplitMix64's finalizer. Cheap, and it decorrelates adjacent seeds well —
     * which matters because seeds come from the clock, so consecutive puzzles
     * differ by only a few milliseconds and a weaker mix would name them all
     * something similar.
     */
    private fun mix(x: Long): Long {
        var z = x + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    private fun pick(seed: Int, salt: Long, list: List<String>): String {
        val h = mix(seed.toLong() * 0x9E3779B97F4A7C15uL.toLong() + salt)
        return list[((h ushr 16).toInt() and 0x7FFFFFFF) % list.size]
    }

    /**
     * Turn typed text into a seed.
     *
     * This is what Minecraft does: if the box doesn't parse as a number, it hashes
     * the string instead, which is why "gargamel" is a world you can share. Same
     * mechanism here — `String.hashCode` is specified by the JDK as
     * `s[0]*31^(n-1) + …`, so it's identical on every device and version, which is
     * exactly the property a shareable seed needs.
     *
     * @return the number typed, or the hash of the text, or null if blank.
     */
    fun seedFromText(text: String): Int? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return trimmed.toIntOrNull() ?: trimmed.hashCode()
    }

    fun nameFor(seed: Int): String {
        val shape = ((mix(seed.toLong() + 0x5D8) ushr 8).toInt() and 0x7FFFFFFF) % 5
        val adj = pick(seed, 1, ADJECTIVES)
        val noun = pick(seed, 2, NOUNS)
        val abstract = pick(seed, 3, ABSTRACTS)
        val place = pick(seed, 4, PLACES)
        return when (shape) {
            0 -> "The $adj $noun"
            1 -> "$noun of the $adj $abstract"
            2 -> "$adj $noun of $place"
            3 -> "The $noun in $abstract"
            else -> "$place's $adj $noun"
        }
    }

    private val ADJECTIVES = listOf(
        "Umbral", "Gilded", "Hollow", "Sable", "Vermillion", "Pallid", "Ninefold",
        "Wandering", "Patient", "Unspoken", "Brazen", "Cindered", "Glassen",
        "Weeping", "Silent", "Fathomless", "Errant", "Tidal", "Salted", "Lucid",
        "Waking", "Threadbare", "Hallowed", "Restless", "Fallow", "Argent",
        "Verdant", "Ashen", "Muttering", "Sunken", "Inverted", "Faint",
        "Obsidian", "Cardinal", "Wintering", "Feral", "Quiet", "Uncounted",
        "Braided", "Drowsing", "Iron", "Paper", "Second", "Last", "Nameless",
        "Folded", "Bright", "Hungering", "Slow", "Distant", "Small", "Grave",
        "Crooked", "Tender", "Winnowed", "Blessed", "Rimed", "Kindly",
    )

    private val NOUNS = listOf(
        "Cartographer", "Lantern", "Aperture", "Cipher", "Meridian", "Reliquary",
        "Automaton", "Beekeeper", "Herbarium", "Orrery", "Palimpsest", "Sextant",
        "Menagerie", "Almanac", "Loom", "Aviary", "Compass", "Bellows", "Kiln",
        "Astrolabe", "Ossuary", "Cloister", "Weathervane", "Threshold",
        "Hourglass", "Inkwell", "Keystone", "Lodestone", "Marginalia", "Nocturne",
        "Obelisk", "Pendulum", "Quarry", "Rookery", "Scriptorium", "Tessera",
        "Undertow", "Vestibule", "Wunderkammer", "Zodiac", "Anchorite",
        "Cabinet", "Dovecote", "Ferryman", "Glasshouse", "Harvester", "Iris",
        "Lighthouse", "Mariner", "Numeral", "Oracle", "Pilgrim", "Quill",
        "Signalman", "Tinker", "Verger", "Watchtower", "Archivist",
    )

    private val ABSTRACTS = listOf(
        "Interval", "Quiet", "Recurrence", "Latitude", "Aftermath", "Interim",
        "Long Division", "Small Hours", "Off-Season", "Undertone", "Vanishing",
        "Understory", "Slow Thaw", "Third Watch", "Low Tide", "Given Word",
        "Middle Distance", "Waiting Room", "Ninth Hour", "Turning", "Reckoning",
        "Overlap", "Remainder", "Approach", "Departure", "Salt Air", "Blue Hour",
        "Dry Season", "Held Breath", "Open Question", "Rule of Thumb",
    )

    private val PLACES = listOf(
        "Aldwych", "Brackwater", "Cinderfell", "Dunmarrow", "Elsinore",
        "Fennhollow", "Grimsby", "Harrowgate", "Innisfree", "Kettleby",
        "Lowmarsh", "Mirebourne", "Netherby", "Orwell", "Pellmire",
        "Quillon", "Rookhaven", "Saltmere", "Thornby", "Underhill",
        "Vantwich", "Westerholm", "Yarrowmoor", "Zennor", "Ashbourne",
        "Blackmoor", "Corvewell", "Draymouth", "Eastmarch", "Farrowdale",
    )

    /** Rough count of distinct names, for sanity-checking variety in tests. */
    val approximateVariety: Int
        get() = 5 * ADJECTIVES.size * NOUNS.size
}
