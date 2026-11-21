import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { OperationToasts } from './ui/OperationToasts';
import './style.css';
import './management.css';
import './ui/interactions.css';
import './ui/selects.css';
import './ui/mobile.css';
import './ui/motion.css';
import './ui/student-sidebar.css';
import './ui/portal-foundations.css';
import './ui/workspace-topbar.css';

createRoot(document.getElementById('root')!).render(<React.StrictMode><App /><OperationToasts /></React.StrictMode>);
