import React, {useState} from 'react';
import styled from "styled-components";
import {RowWrapper} from "./SellComponent";
import Form from "react-bootstrap/Form";
import Modal from 'react-bootstrap/Modal';
import Button from 'react-bootstrap/Button'
import Alert from 'react-bootstrap/Alert'
import {useUserContext} from "../context/UserContext";
export const LoginWrapper = styled.div`
  grid-column: span 2;
  border: 2px solid grey;
  padding: 1.5rem;
  border-radius: 15px;
`
export const ButtonWrapper = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-evenly;
`
export const AlertWrapper = styled.div`
    margin: 0.5em;
`


const LoginComponent = () => {
    const {users, addUser, loginUser} = useUserContext();
    const [showModal, setShowModal] = useState(true);
    const [showRegisterAlert, setShowRegisterAlert] = useState(false);
    const [user, setUser] = useState({
        email: '',
        password: ''
    })
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");

    const updateUser = (e) => {
        const {value, name} = e.target;
        setUser({
            ...user,
            [name]: value
        });
    }
    const handleLogin = () => {
        //1. check that user exists in context
        console.log("OK HANDLING LOGIN FOR THIS USER: " + JSON.stringify(user));
        console.log(loginUser(user));
        if(loginUser(user)) {
            setShowModal(false);
        } else {
            console.log("uhh loginUsers failed");
        }
        //2. send request to get the users account balance and portfolio
        //3. get rid of modal
        // console.log(JSON.stringify(user));
        // console.log(email);
        // console.log(password);
    }

    const handleRegister = () => {
        //1. add user to context
        console.log(addUser(user));
        if(addUser(user)) {
            //load users info
            setShowModal(false);
            // console.log("USER HAS BEEN ADDED");
        } else {
            setShowRegisterAlert(true);
            // console.log("USER ALREADY EXISTS");
        }

        // console.log(JSON.stringify(users));
        //2. get rid of modal

        // console.log("OK HANDLING REGISTER");
        // console.log(JSON.stringify(user));
        // console.log(email);
        // console.log(password);
    }

    return (
        <Modal show={showModal}>
            <LoginWrapper>
                <h1>Login:</h1>
                <RowWrapper>
                    <Form.Label>Email:</Form.Label>
                    <Form.Control onChange={updateUser} name="email" type="text" required/>
                </RowWrapper>

                <RowWrapper>
                    <Form.Label>Password:</Form.Label>
                    <Form.Control onChange={updateUser} name="password" type="password" required/>
                </RowWrapper>
                <ButtonWrapper>
                    <Button size="lg" onClick={handleLogin} variant="primary"> Login </Button>
                    <Button size="lg" onClick={handleRegister} variant="secondary"> Register </Button>
                </ButtonWrapper>
                <AlertWrapper>
                    <Alert show={showRegisterAlert} variant="danger">
                        An account for {user.email} already exists!
                    </Alert>
                </AlertWrapper>
            </LoginWrapper>
        </Modal>
    );
};

export default LoginComponent;
