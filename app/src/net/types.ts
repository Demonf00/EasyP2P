export type SignalMessage =
  | { type: 'create' }
  | { type: 'created'; code: string }
  | { type: 'join'; code: string }
  | { type: 'joined'; code: string }
  | { type: 'signal'; payload: any }
  | { type: 'peer-join' }
  | { type: 'peer-leave' }
  | { type: 'error'; reason: string };
