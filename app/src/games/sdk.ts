export type PlayerID = 0 | 1;

export interface Game<State, Move> {
  id: string;
  name: string;
  minPlayers: number;
  maxPlayers: number;
  setup(): State;
  isLegal(s: State, m: Move, by: PlayerID): boolean;
  apply(s: State, m: Move, by: PlayerID): State;
  outcome(s: State): { winner?: PlayerID; draw?: boolean } | null;
  serialize(s: State): string;
  deserialize(text: string): State;
}
