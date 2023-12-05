import {createContext, useContext, useState} from "react";

export const UserContext = createContext();
export const UserContextProvider = UserContext.Provider;
export const useUserContext = () => useContext(UserContext);

export const UserProvider = ({children}) => {
    const [users, setUsers] = useState([]);
    const [currUser, setCurrUser] = useState({});
    const addUser = (user) => {
        console.log("IN USER PROVIDER addUser");
        console.log(JSON.stringify(user));
        if (users.filter(u => u.email === user.email).length === 0) {
            console.log("returning true from add user");
            setUsers([...users, user]);
            return true;
        }
        console.log("returning false from add user");
        return false;
    }

    const loginUser = (user) => {

        if (users.filter(u => (u.email === user.email) && (u.password === user.password)).length === 1) {
            setCurrUser(user);
            console.log("returning true from login user");
            return true;
        }
        console.log("returning false from login user");
        return false;
    }


    return (
        <UserContextProvider
            value={{
                currUser,
                users,
                addUser,
                loginUser,
            }}>
            {children}
        </UserContextProvider>
    )
}
