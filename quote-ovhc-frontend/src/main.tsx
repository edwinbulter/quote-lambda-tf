import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import AppWithProviders from './AppWithProviders.tsx'
import { OVHAuthProvider } from "./contexts/OVHAuthContext.tsx";

createRoot(document.getElementById('root')!).render(
    <StrictMode>
        <OVHAuthProvider>
            <AppWithProviders />
        </OVHAuthProvider>
    </StrictMode>,
);