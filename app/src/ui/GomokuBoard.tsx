import React from 'react';
import type { GomokuState } from '../games/gomoku/rules';
interface Props { s: GomokuState; onPlace: (x:number,y:number)=>void; mySide: 0|1; }
export default function GomokuBoard({ s, onPlace, mySide }: Props) {
  const cellSize = 28;
  return (
    <div style={{ display:'inline-block', padding: 12, background: '#d8b36b', borderRadius: 8 }}>
      <div style={{ fontSize: 12, marginBottom: 8 }}>You are: {mySide===0?'Black':'White'}</div>
      <svg width={s.size*cellSize} height={s.size*cellSize} style={{ boxShadow: '0 2px 10px rgba(0,0,0,0.2)', borderRadius: 6 }}>
        {Array.from({length:s.size}).map((_,i)=> (
          <line key={'h'+i} x1={cellSize/2} y1={cellSize/2+i*cellSize} x2={s.size*cellSize-cellSize/2} y2={cellSize/2+i*cellSize} stroke="#333"/>
        ))}
        {Array.from({length:s.size}).map((_,i)=> (
          <line key={'v'+i} x1={cellSize/2+i*cellSize} y1={cellSize/2} x2={cellSize/2+i*cellSize} y2={s.size*cellSize-cellSize/2} stroke="#333"/>
        ))}
        {s.board.map((row,y)=> row.map((c,x)=> (
          <g key={x+':'+y} onClick={()=> onPlace(x,y)} style={{ cursor: 'pointer' }}>
            {c!==-1 && (
              <circle cx={cellSize/2+x*cellSize} cy={cellSize/2+y*cellSize} r={cellSize*0.38} fill={c===0?'#111':'#eee'} stroke="#000"/>
            )}
          </g>
        )))}
      </svg>
    </div>
  );
}
