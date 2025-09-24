export type Cell = -1 | 0 | 1;
export interface State { size: number; board: Cell[][]; turn: 0 | 1; }
export interface Move { x: number; y: number; }

export const Gomoku = {
  id: 'gomoku',
  name: 'Gomoku / 五子棋',
  minPlayers: 2,
  maxPlayers: 2,
  setup(): State {
    const size = 15; const board = Array.from({ length: size }, () => Array<Cell>(size).fill(-1));
    return { size, board, turn: 0 };
  },
  isLegal(s: State, m: Move) {
    const { x, y } = m; return x>=0 && y>=0 && x<s.size && y<s.size && s.board[y][x] === -1;
  },
  apply(s: State, m: Move, by: 0|1): State {
    const ns: State = { ...s, board: s.board.map(r => r.slice()) };
    ns.board[m.y][m.x] = by; ns.turn = (s.turn ^ 1) as 0|1; return ns;
  },
  outcome(s: State) {
    const n = s.size, b = s.board;
    const lines = [ [1,0], [0,1], [1,1], [1,-1] ];
    const win = (px:number,py:number,dx:number,dy:number,c:0|1)=>{
      for(let k=0;k<5;k++) { const x=px+dx*k, y=py+dy*k; if (x<0||y<0||x>=n||y>=n||b[y][x]!==c) return false; }
      return true;
    };
    for (let y=0;y<n;y++) for (let x=0;x<n;x++) {
      const cell = b[y][x]; if (cell===-1) continue;
      for (const [dx,dy] of lines) if (win(x,y,dx,dy,cell as 0|1)) return { winner: cell as 0|1 };
    }
    if (b.every(row => row.every(c => c!==-1))) return { draw: true };
    return null;
  },
  serialize(s: State) { return JSON.stringify(s); },
  deserialize(text: string) { return JSON.parse(text) as State; }
};
export type GomokuState = State;
