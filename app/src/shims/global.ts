// Browser shims for Node-like globals used by some deps
import { Buffer } from 'buffer';
const g: any = globalThis as any;
if (typeof g.global === 'undefined') g.global = globalThis;
if (typeof g.process === 'undefined') g.process = { env: {} };
if (typeof g.Buffer === 'undefined') g.Buffer = Buffer;
