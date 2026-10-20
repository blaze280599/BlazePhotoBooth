package com.blaze.photobooth.model

import com.blaze.photobooth.model.Poses.who
import kotlin.random.Random

/**
 * The pose plan (README §5.6.1): one pose id per shot (cuts + bonus), drawn from the pool that suits who's in the
 * booth, with **no repeats** in a session.
 *
 * - Shot 1 is a *cute* warm-up; the last cut (index `cuts − 1`) is a *love* pose, so the strip ends on a heart.
 *   With a single cut (Solo Polaroid) the warm-up wins: there is no "last" cut to save the heart for.
 * - No two shots in a row share a mood.
 * - Two of us / Squad: about [OwnShare] of the picks come from their own list, the rest from the solo list.
 * - When a rule can't be met, the "different mood" rule is relaxed first, then the cute/love rule; a pose is only
 *   ever repeated if the whole catalogue has run out (it can't with the real one: 51 poses, at most 10 shots).
 *
 * Pure and deterministic for a given seed (the draft uses its `createdAt`), so it's unit-tested on the JVM.
 */
object PosePlanner {

    /** Share of Duo/Squad picks that come from their own list (§5.6.1: "about 60%"). */
    const val OwnShare = 0.6

    /** The mood shot [index] must have, if any: cute for the warm-up, love for the strip's last cut. */
    fun requiredMood(index: Int, cuts: Int): Mood? = when {
        index == 0 -> Mood.Cute
        index == cuts - 1 -> Mood.Love
        else -> null
    }

    /** A fresh plan of [total] pose ids for a strip of [cuts] cuts. */
    fun plan(crowd: Crowd, cuts: Int, total: Int, seed: Long, catalogue: List<Pose> = Poses.all): List<String> {
        val random = Random(seed)
        val plan = mutableListOf<Pose>()
        for (i in 0 until total) {
            plan += choose(
                crowd = crowd,
                catalogue = catalogue,
                used = plan.mapTo(HashSet()) { it.id },
                required = requiredMood(i, cuts),
                avoid = setOfNotNull(plan.lastOrNull()?.mood, requiredMood(i + 1, cuts).takeIf { i + 1 < total }),
                random = random,
            )
        }
        return plan.map { it.id }
    }

    /**
     * Tap-to-swap (§5.6.1): shot [index] gets another pose that isn't in the plan yet, keeping the rules where it
     * can (its own required mood, and a mood different from both neighbours).
     */
    fun swap(plan: List<String>, index: Int, crowd: Crowd, cuts: Int, random: Random, catalogue: List<Pose> = Poses.all): List<String> {
        if (index !in plan.indices) return plan
        val byId = catalogue.associateBy { it.id }
        val next = choose(
            crowd = crowd,
            catalogue = catalogue,
            used = plan.toHashSet(),
            required = requiredMood(index, cuts),
            avoid = setOfNotNull(plan.getOrNull(index - 1)?.let(byId::get)?.mood, plan.getOrNull(index + 1)?.let(byId::get)?.mood),
            random = random,
        )
        return plan.toMutableList().also { it[index] = next.id }
    }

    /**
     * A plan read back from `draft.json` (§11.4): an old draft has none (→ a fresh plan from [seed]); a plan of the
     * wrong length is replaced; an id that's no longer in the catalogue (or a repeat) is swapped for a fitting pose,
     * so the poses already shown keep their place.
     */
    fun repair(plan: List<String>, crowd: Crowd, cuts: Int, total: Int, seed: Long, catalogue: List<Pose> = Poses.all): List<String> {
        if (plan.size != total) return plan(crowd, cuts, total, seed, catalogue)
        val known = catalogue.mapTo(HashSet()) { it.id }
        val seen = HashSet<String>()
        val bad = plan.indices.filter { i -> plan[i] !in known || !seen.add(plan[i]) }
        if (bad.isEmpty()) return plan
        val random = Random(seed)
        // Placeholders that can't clash with a real id, so the swaps see only the good poses as used.
        var fixed = plan.mapIndexed { i, id -> if (i in bad) "?$i" else id }
        for (i in bad) fixed = swap(fixed, i, crowd, cuts, random, catalogue)
        return fixed
    }

    private fun choose(
        crowd: Crowd,
        catalogue: List<Pose>,
        used: Set<String>,
        required: Mood?,
        avoid: Set<Mood>,
        random: Random,
    ): Pose {
        val pool = Poses.poolFor(crowd, catalogue).filter { it.id !in used }
        // Strict first; then without "different mood"; then without cute/love; then anything unused in the catalogue.
        val candidates = pool.filter { (required == null || it.mood == required) && it.mood !in avoid }.ifEmpty {
            pool.filter { required == null || it.mood == required }
        }.ifEmpty { pool }.ifEmpty { catalogue.filter { it.id !in used } }.ifEmpty { catalogue }
        return weightedPick(candidates, crowd, random)
    }

    /** Duo/Squad: their own poses about [OwnShare] of the time when both kinds are possible. */
    private fun weightedPick(candidates: List<Pose>, crowd: Crowd, random: Random): Pose {
        if (crowd == Crowd.Solo) return candidates.random(random)
        val (own, others) = candidates.partition { it.who == crowd.who }
        val fromOwn = when {
            own.isEmpty() -> false
            others.isEmpty() -> true
            else -> random.nextDouble() < OwnShare
        }
        return (if (fromOwn) own else others).random(random)
    }
}
