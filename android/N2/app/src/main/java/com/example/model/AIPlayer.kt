/**
 * AI opponent for backgammon. Provides heuristic-based move calculation, board evaluation,
 * and fallback random moves for difficulty level 0.
 *
 * This module is separate from [GameEngine.selectBestBotMove] and uses a simpler
 * integer-based board representation for independent AI logic.
 */
package com.example.model

import kotlin.random.Random

/**
 * Простой AI-противник для игры в нарды.
 * Использует эвристики: предпочитает сбивать шашки,
 * избегает одиночных шашек, двигает дальние шашки.
 *
 * @property difficulty AI strength level (0 = random, 1+ = heuristic).
 */
class AIPlayer(private val difficulty: Int = 1) {

    /** A candidate move with a heuristic priority score. Higher = better. */
    data class AIMove(
        val from: Int,
        val to: Int,
        val priority: Int = 0
    )

    /**
     * Calculates the best sequence of moves for the given board state.
     * On difficulty 0, returns a random valid move.
     * On higher difficulties, prioritises: captures (to == -1) > blots (from == 1) > home entry (to > 18).
     * @param board integer board where positive values are player's checkers, -1 is opponent blot.
     * @param dice available dice values.
     * @param barCount number of opponent checkers on the bar (unused in current implementation).
     * @param homeCount number of opponent checkers already borne off (unused in current implementation).
     */
    fun calculateMove(
        board: List<Int>,
        dice: List<Int>,
        barCount: Int,
        homeCount: Int
    ): List<AIMove> {
        if (difficulty == 0) return randomMove(board, dice, barCount)

        val moves = mutableListOf<AIMove>()
        val available = board.indices.filter { board[it] > 0 }

        for (from in available) {
            for (die in dice) {
                val to = from + die
                if (to < 24 && board[to] >= -1) {
                    var priority = 0
                    if (board[to] == -1) priority += 100
                    if (board[from] == 1) priority += 50
                    if (to > 18) priority += 25
                    moves.add(AIMove(from, to, priority))
                }
            }
        }

        return moves.sortedByDescending { it.priority }
    }

    /** Picks a single random legal move from the available positions. Used at difficulty 0. */
    private fun randomMove(board: List<Int>, dice: List<Int>, barCount: Int): List<AIMove> {
        val available = board.indices.filter { board[it] > 0 }
        if (available.isEmpty()) return emptyList()
        val from = available.random()
        val die = dice.random()
        val to = from + die
        return if (to < 24 && board[to] >= -1) {
            listOf(AIMove(from, to))
        } else emptyList()
    }

    /**
     * Evaluates the board from the AI's perspective using a simple heuristic.
     * Checkers closer to bearing off score higher; isolated checkers (count == 1) are penalised.
     * @param board integer board representation.
     * @param homeCount number of checkers already borne off.
     * @return total heuristic score.
     */
    fun evaluateBoard(board: List<Int>, homeCount: Int): Double {
        var score = 0.0
        for (i in board.indices) {
            val count = board[i]
            if (count > 0) {
                score += count.toDouble() * (24 - i)
                if (count == 1) score -= 10
            }
        }
        score += homeCount * 50.0
        return score
    }
}
