import React from 'react'; import type { GomokuState } from '../games/gomoku/rules';
interface Props{ s:GomokuState; onPlace:(x:number,y:number)=>void; mySide:0|1; }
export default function GomokuBoard({ s, onPlace, mySide }:Props){ const cell=28;
  return(<div style={{display:'inline-block',padding:12,background:'#d8b36b',borderRadius:8}}>
    <div style={{fontSize:12,marginBottom:8}}>You are: {mySide===0?'Black':'White'}</div>
    <svg width={s.size*cell} height={s.size*cell} style={{boxShadow:'0 2px 10px rgba(0,0,0,0.2)',borderRadius:6}}>
      {Array.from({length:s.size}).map((_,i)=>(<line key={'h'+i} x1={cell/2} y1={cell/2+i*cell} x2={s.size*cell-cell/2} y2={cell/2+i*cell} stroke="#333"/>))}
      {Array.from({length:s.size}).map((_,i)=>(<line key={'v'+i} x1={cell/2+i*cell} y1={cell/2} x2={cell/2+i*cell} y2={s.size*cell-cell/2} stroke="#333"/>))}
      {s.board.map((row,y)=>row.map((c,x)=>(<g key={x+':'+y} onClick={()=>onPlace(x,y)} style={{cursor:'pointer'}}>
        {c!==-1&&(<circle cx={cell/2+x*cell} cy={cell/2+y*cell} r={cell*0.38} fill={c===0?'#111':'#eee'} stroke="#000"/>)}
      </g>)))}
    </svg>
  </div>);
}