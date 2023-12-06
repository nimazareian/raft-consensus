import {createContext, useContext, useState} from "react";

export const LeaderContext = createContext();
export const LeaderContextProvider = LeaderContext.Provider;
export const useLeaderContext = () => useContext(LeaderContext);

export const LeaderProvider = ({children}) => {
    const [leader, setLeader] = useState({address: null, port: null});


    return (
        <LeaderContextProvider
            value={{
                leader,
                setLeader
            }}>
            {children}
        </LeaderContextProvider>
    )
}
