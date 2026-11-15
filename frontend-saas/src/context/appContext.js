import { createContext, useContext } from 'react';

export const AppContext = createContext(null);

/** Datos compartidos: me, memberships, refreshMe, showToast, isDarkMode */
export function useApp() {
    return useContext(AppContext);
}
