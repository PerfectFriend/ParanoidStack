package com.example.ui.screens.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.GameStatus
import com.example.model.Player
import com.example.ui.GameViewModel
import com.example.ui.components.DiceCube
import com.example.ui.screens.Language

fun Player.localizedLabel(lang: String): String {
    return if (this == Player.WHITE) Language.get("white", lang) else Language.get("black", lang)
}

/** Диалог жеребьёвки: два этапа (цвет и первый ход). */
@Composable
fun JrebiyDialog(viewModel: GameViewModel) {
    val lang = viewModel.selectedLanguage
    val theme = viewModel.selectedTheme

    LaunchedEffect(viewModel.gameStatus) {
        if (viewModel.gameStatus == GameStatus.LOT_STAGE2 && viewModel.isOnlinePlayActive && viewModel.localPlayerColor == Player.BLACK) {
            if (!viewModel.onlineLot2WhiteRolled && !viewModel.isOnline2WhiteRolling && !viewModel.isOnline2BlackRolling) {
                viewModel.rollLot2OnlineOpponentFirst()
            }
        }
    }

    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .border(2.dp, Color(0xFFD3A373), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = Language.get("lot_title", lang),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFD3A373),
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (viewModel.gameStatus == GameStatus.LOT_STAGE1) {
                    Text(
                        text = Language.get("lot_stage1_title", lang),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (viewModel.isOnlinePlayActive) {
                            if (viewModel.selectedLanguage == "RU") "Каждый игрок бросает по очереди. У кого больше — играет Белыми." else "Each player rolls in turn. Higher roll gets White."
                        } else {
                            Language.get("lot_stage1_desc", lang)
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when {
                                    viewModel.isOnlinePlayActive -> "Мы"
                                    !viewModel.isBotOpponentEnabled -> Language.get("p2p_player1", lang).replace(" (Белые)", "")
                                    else -> Language.get("player_name", lang)
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val diceLotValue1 = if (viewModel.isOnlinePlayActive) {
                                if (viewModel.isOnline1UserRolling) viewModel.diceLot1User else if (viewModel.diceLot1User > 0) viewModel.diceLot1User else 1
                            } else {
                                if (viewModel.isRollingDice) viewModel.diceValue1 else if (viewModel.diceLot1User > 0) viewModel.diceLot1User else 1
                            }
                            DiceCube(
                                value = diceLotValue1,
                                isRolling = if (viewModel.isOnlinePlayActive) viewModel.isOnline1UserRolling else viewModel.isRollingDice,
                                themeId = theme
                            )
                        }
                        Text("VS", fontWeight = FontWeight.Black, color = Color(0xFFD3A373), fontSize = 14.sp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when {
                                    viewModel.isOnlinePlayActive -> viewModel.onlineOpponentName
                                    !viewModel.isBotOpponentEnabled -> Language.get("p2p_player2", lang).replace(" (Черные)", "")
                                    else -> Language.get("bot_name", lang)
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val diceLotValue2 = if (viewModel.isOnlinePlayActive) {
                                if (viewModel.isOnline1BotRolling) viewModel.diceLot1Bot else if (viewModel.diceLot1Bot > 0) viewModel.diceLot1Bot else 1
                            } else {
                                if (viewModel.isRollingDice) viewModel.diceValue2 else if (viewModel.diceLot1Bot > 0) viewModel.diceLot1Bot else 1
                            }
                            DiceCube(
                                value = diceLotValue2,
                                isRolling = if (viewModel.isOnlinePlayActive) viewModel.isOnline1BotRolling else viewModel.isRollingDice,
                                themeId = theme
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    if (viewModel.isOnlinePlayActive) {
                        if (viewModel.onlineLot1UserRolled && viewModel.onlineLot1BotRolled) {
                            val isTie = viewModel.diceLot1User == viewModel.diceLot1Bot
                            if (isTie) {
                                Text(
                                    text = Language.get("lot_result_tie", lang),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                val localWonColors = viewModel.diceLot1User > viewModel.diceLot1Bot
                                Text(
                                    text = if (localWonColors) {
                                        if (viewModel.selectedLanguage == "RU") "Вы выиграли! Ваши фишки — Белые (⚪), соперник — Черные (⚫)." else "You won! Your checkers are White (⚪), opponent is Black (⚫)."
                                    } else {
                                        if (viewModel.selectedLanguage == "RU") "Соперник выиграл! Ваши фишки — Черные (⚫), соперник — Белые (⚪)." else "Opponent won! Your checkers are Black (⚫), opponent is White (⚪)."
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.transitionToStage2() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD3A373),
                                        contentColor = Color.Black
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(Language.get("lot_continue_btn", lang).uppercase(), fontWeight = FontWeight.Bold)
                                }
                            }
                        } else if (viewModel.isOnline1UserRolling) {
                            Button(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (viewModel.selectedLanguage == "RU") "БРОСАЕМ КУБИК..." else "ROLLING ...")
                            }
                        } else if (viewModel.onlineLot1UserRolled && viewModel.isOnline1BotRolling) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (viewModel.selectedLanguage == "RU") " бросает кубик..." else " is rolling...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        } else {
                            Button(
                                onClick = { viewModel.rollLot1OnlineLocal() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text((if (viewModel.selectedLanguage == "RU") "БРОСИТЬ СВОЙ КУБИК" else "ROLL MY DIE").uppercase())
                            }
                        }
                    } else {
                        if (!viewModel.isRollingDice && viewModel.diceLot1User > 0 && viewModel.diceLot1Bot > 0) {
                            val isTie = viewModel.diceLot1User == viewModel.diceLot1Bot
                            if (isTie) {
                                Text(
                                    text = Language.get("lot_result_tie", lang),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.rollLot1() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(Language.get("lot_roll_btn", lang).uppercase())
                                }
                            } else {
                                val userWon = viewModel.diceLot1User > viewModel.diceLot1Bot
                                val winnerText = if (!viewModel.isBotOpponentEnabled) {
                                    if (userWon) {
                                        if (viewModel.selectedLanguage == "RU") "Игрок 1 выиграл! Фишки Игрока 1 — Белые, Игрок 2 — Черные." else "Player 1 won! Player 1 color is White, Player 2 is Black."
                                    } else {
                                        if (viewModel.selectedLanguage == "RU") "Игрок 2 выиграл! Фишки Игрока 2 — Белые, Игрок 1 — Черные." else "Player 2 won! Player 2 color is White, Player 1 is Black."
                                    }
                                } else {
                                    if (userWon) Language.get("lot_result_winner_colors_white", lang) else Language.get("lot_result_winner_colors_black", lang)
                                }
                                Text(
                                    text = winnerText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.transitionToStage2() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD3A373),
                                        contentColor = Color.Black
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(Language.get("lot_continue_btn", lang).uppercase(), fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Button(
                                onClick = { viewModel.rollLot1() },
                                enabled = !viewModel.isRollingDice,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(Language.get("lot_roll_btn", lang).uppercase())
                            }
                        }
                    }
                } else if (viewModel.gameStatus == GameStatus.LOT_STAGE2) {
                    Text(
                        text = Language.get("lot_stage2_title", lang),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (viewModel.isOnlinePlayActive) {
                            if (viewModel.selectedLanguage == "RU") "Белые бросают кубик первыми для жеребьёвки первого хода." else "White player rolls first to determine who takes the opening turn."
                        } else {
                            Language.get("lot_stage2_desc", lang)
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when {
                                    viewModel.isOnlinePlayActive -> "Мы ()"
                                    !viewModel.isBotOpponentEnabled -> " ()"
                                    else -> "Вы ()"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val diceLotValue1 = if (viewModel.isOnlinePlayActive) {
                                if (viewModel.localPlayerColor == Player.WHITE) {
                                    if (viewModel.isOnline2WhiteRolling) viewModel.diceLot2White else if (viewModel.onlineLot2WhiteRolled) viewModel.diceLot2White else 1
                                } else {
                                    if (viewModel.isOnline2BlackRolling) viewModel.diceLot2Black else if (viewModel.onlineLot2BlackRolled) viewModel.diceLot2Black else 1
                                }
                            } else {
                                if (viewModel.isRollingDice) viewModel.diceValue1 else if (viewModel.diceLot2White > 0) viewModel.diceLot2White else 1
                            }
                            DiceCube(
                                value = diceLotValue1,
                                isRolling = if (viewModel.isOnlinePlayActive) {
                                    if (viewModel.localPlayerColor == Player.WHITE) viewModel.isOnline2WhiteRolling else viewModel.isOnline2BlackRolling
                                } else viewModel.isRollingDice,
                                themeId = theme
                            )
                        }
                        Text("VS", fontWeight = FontWeight.Black, color = Color(0xFFD3A373), fontSize = 14.sp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when {
                                    viewModel.isOnlinePlayActive -> " ()"
                                    !viewModel.isBotOpponentEnabled -> " ()"
                                    else -> "Бот ()"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val diceLotValue2 = if (viewModel.isOnlinePlayActive) {
                                if (viewModel.localPlayerColor == Player.WHITE) {
                                    if (viewModel.isOnline2BlackRolling) viewModel.diceLot2Black else if (viewModel.onlineLot2BlackRolled) viewModel.diceLot2Black else 1
                                } else {
                                    if (viewModel.isOnline2WhiteRolling) viewModel.diceLot2White else if (viewModel.onlineLot2WhiteRolled) viewModel.diceLot2White else 1
                                }
                            } else {
                                if (viewModel.isRollingDice) viewModel.diceValue2 else if (viewModel.diceLot2Black > 0) viewModel.diceLot2Black else 1
                            }
                            DiceCube(
                                value = diceLotValue2,
                                isRolling = if (viewModel.isOnlinePlayActive) {
                                    if (viewModel.localPlayerColor == Player.WHITE) viewModel.isOnline2BlackRolling else viewModel.isOnline2WhiteRolling
                                } else viewModel.isRollingDice,
                                themeId = theme
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    if (viewModel.isOnlinePlayActive) {
                        if (viewModel.onlineLot2WhiteRolled && viewModel.onlineLot2BlackRolled) {
                            val isTie = viewModel.diceLot2White == viewModel.diceLot2Black
                            if (isTie) {
                                Text(
                                    text = Language.get("lot_result_tie", lang),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                val whiteWon = viewModel.diceLot2White > viewModel.diceLot2Black
                                val winningColor = if (whiteWon) Player.WHITE else Player.BLACK
                                val isLocalTurnWinner = viewModel.localPlayerColor == winningColor
                                val turnWinnerText = if (isLocalTurnWinner) {
                                    if (viewModel.selectedLanguage == "RU") "Вы выиграли первый ход! Начинайте партию." else "You won the first turn! Begin the game."
                                } else {
                                    if (viewModel.selectedLanguage == "RU") " выиграл(а) первый ход! Они начинают партию." else " won the first turn! They begin the game."
                                }
                                Text(
                                    text = turnWinnerText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.applyLotStage2WinnerAndStart() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD3A373),
                                        contentColor = Color.Black
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(Language.get("lot_start_game_btn", lang).uppercase(), fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            if (viewModel.localPlayerColor == Player.WHITE) {
                                if (!viewModel.onlineLot2WhiteRolled && !viewModel.isOnline2WhiteRolling) {
                                    Button(
                                        onClick = { viewModel.rollLot2OnlineLocalWhite() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text((if (viewModel.selectedLanguage == "RU") "БРОСИТЬ БЕЛЫЙ КУБИК" else "ROLL WHITE DIE").uppercase())
                                    }
                                } else if (viewModel.isOnline2WhiteRolling) {
                                    Button(
                                        onClick = {},
                                        enabled = false,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(if (viewModel.selectedLanguage == "RU") "БРОСАЕМ БЕЛЫЙ КУБИК..." else "ROLLING WHITE...")
                                    }
                                } else if (viewModel.onlineLot2WhiteRolled && viewModel.isOnline2BlackRolling) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (viewModel.selectedLanguage == "RU") " бросает черный кубик..." else " is rolling black...",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            } else {
                                if (!viewModel.onlineLot2WhiteRolled && viewModel.isOnline2WhiteRolling) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (viewModel.selectedLanguage == "RU") " бросает белый кубик..." else " is rolling white...",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                } else if (viewModel.onlineLot2WhiteRolled && !viewModel.onlineLot2BlackRolled && !viewModel.isOnline2BlackRolling) {
                                    Button(
                                        onClick = { viewModel.rollLot2OnlineLocalBlack() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text((if (viewModel.selectedLanguage == "RU") "БРОСИТЬ ЧЕРНЫЙ КУБИК" else "ROLL BLACK DIE").uppercase())
                                    }
                                } else if (viewModel.isOnline2BlackRolling) {
                                    Button(
                                        onClick = {},
                                        enabled = false,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(if (viewModel.selectedLanguage == "RU") "БРОСАЕМ ЧЕРНЫЙ КУБИК..." else "ROLLING BLACK...")
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (viewModel.selectedLanguage == "RU") "Инициируем ход соперника..." else "Initiating opponent roll...",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        if (!viewModel.isRollingDice && viewModel.diceLot2White > 0 && viewModel.diceLot2Black > 0) {
                            val isTie = viewModel.diceLot2White == viewModel.diceLot2Black
                            if (isTie) {
                                Text(
                                    text = Language.get("lot_result_tie", lang),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.rollLot2() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(Language.get("lot_roll_btn", lang).uppercase())
                                }
                            } else {
                                val whiteWon = viewModel.diceLot2White > viewModel.diceLot2Black
                                val winnerColor = if (whiteWon) Player.WHITE else Player.BLACK

                                val firstMoveText = if (!viewModel.isBotOpponentEnabled) {
                                    val localWinnerName = if (winnerColor == viewModel.humanPlayerColor) {
                                        Language.get("p2p_player1", lang).replace(" (Белые)", "")
                                    } else {
                                        Language.get("p2p_player2", lang).replace(" (Черные)", "")
                                    }
                                    if (viewModel.selectedLanguage == "RU") {
                                        "Жеребьёвка завершена!  выиграл(а) первый ход ( против )."
                                    } else {
                                        "Lottery complete!  won first turn ( vs )."
                                    }
                                } else {
                                    val winningVisualColor = if (whiteWon) viewModel.humanPlayerColor else viewModel.botPlayerColor
                                    if (winningVisualColor == Player.WHITE) {
                                        Language.get("lot_result_first_move_white", lang)
                                            .replace("%1", viewModel.diceLot2White.toString())
                                            .replace("%2", viewModel.diceLot2Black.toString())
                                    } else {
                                        Language.get("lot_result_first_move_black", lang)
                                            .replace("%1", viewModel.diceLot2White.toString())
                                            .replace("%2", viewModel.diceLot2Black.toString())
                                    }
                                }

                                Text(
                                    text = firstMoveText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.applyLotStage2WinnerAndStart() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD3A373),
                                        contentColor = Color.Black
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(Language.get("lot_start_game_btn", lang).uppercase(), fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Button(
                                onClick = { viewModel.rollLot2() },
                                enabled = !viewModel.isRollingDice,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(Language.get("lot_roll_btn", lang).uppercase())
                            }
                        }
                    }
                }
            }
        }
    }
}
