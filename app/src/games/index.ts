import type { Game } from './sdk'; import { Gomoku } from './gomoku/rules'; export const games: Record<string, Game<any, any>> = { [Gomoku.id]: Gomoku };
