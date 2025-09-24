import React from 'react';
import { createRoot } from 'react-dom/client';
import './shims/global';
import App from './ui/App';

console.log('App booted');
createRoot(document.getElementById('root')!).render(<App />);
